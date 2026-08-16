package com.example.inventory.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.inventory.IntegrationTest;
import com.example.inventory.common.ApiExceptions.DomainRuleException;
import com.example.inventory.product.ProductRepository;
import com.example.inventory.stock.MovementType;
import com.example.inventory.stock.StockDtos.StockMovementRequest;
import com.example.inventory.stock.StockMovementService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@WithMockUser(username = "importer@example.com", roles = "ADMIN")
class ImportExportIntegrationTest extends IntegrationTest {

    @Autowired
    ProductImportService importService;

    @Autowired
    InventoryExportService exportService;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    StockMovementService stockMovementService;

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile(
                "file", "products.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a valid CSV creates products and resolves categories by name")
    void importsValidRows() {
        var result = importService.importProducts(csv(
                """
                sku,name,description,category,supplier,unitPrice,reorderLevel
                IMP-001,Hammer,Claw hammer,Tools,Acme,12.50,4
                IMP-002,Screwdriver,Phillips,Tools,Acme,4.25,10
                """));

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.created()).isEqualTo(2);
        assertThat(result.errors()).isEmpty();

        // Read back through the projection rather than the entity: category and supplier are
        // lazy associations and this assertion runs outside the import's transaction.
        var hammer = productRepository.findBySkuIgnoreCaseAndDeletedAtIsNull("IMP-001").orElseThrow();
        var row = productRepository.findRowById(hammer.getId()).orElseThrow();

        assertThat(row.name()).isEqualTo("Hammer");
        assertThat(row.reorderLevel()).isEqualTo(4);
        assertThat(row.categoryName()).isEqualTo("Tools");
        assertThat(row.supplierName()).isEqualTo("Acme");
    }

    @Test
    @DisplayName("re-importing the same SKU updates rather than duplicating")
    void importIsIdempotentPerSku() {
        importService.importProducts(csv("sku,name,unitPrice\nIMP-UP-1,Original,1.00\n"));
        var second = importService.importProducts(csv("sku,name,unitPrice\nIMP-UP-1,Renamed,2.00\n"));

        assertThat(second.created()).isZero();
        assertThat(second.updated()).isEqualTo(1);

        var product = productRepository.findBySkuIgnoreCaseAndDeletedAtIsNull("IMP-UP-1").orElseThrow();
        assertThat(product.getName()).isEqualTo("Renamed");
    }

    @Test
    @DisplayName("a bad row is reported and skipped without failing the whole file")
    void badRowsAreIsolated() {
        var result = importService.importProducts(csv(
                """
                sku,name,unitPrice
                IMP-OK-1,Good,1.00
                ,Missing SKU,1.00
                IMP-BAD-1,Bad price,not-a-number
                IMP-OK-2,Also good,3.00
                """));

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(result.errors()).hasSize(2);
        assertThat(productRepository.findBySkuIgnoreCaseAndDeletedAtIsNull("IMP-OK-2")).isPresent();
        assertThat(productRepository.findBySkuIgnoreCaseAndDeletedAtIsNull("IMP-BAD-1")).isEmpty();
    }

    @Test
    @DisplayName("an import never sets stock — quantity stays zero until a movement is recorded")
    void importCannotSetStock() {
        importService.importProducts(csv(
                "sku,name,unitPrice,reorderLevel,quantityOnHand\nIMP-QTY-1,Sneaky,1.00,0,999\n"));

        var product = productRepository.findBySkuIgnoreCaseAndDeletedAtIsNull("IMP-QTY-1").orElseThrow();
        assertThat(stockMovementService.level(product.getId()).quantityOnHand()).isZero();
    }

    @Test
    @DisplayName("a CSV without the required columns is rejected")
    void missingHeadersRejected() {
        assertThatThrownBy(() -> importService.importProducts(csv("foo,bar\n1,2\n")))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("sku");
    }

    @Test
    @DisplayName("an empty upload is rejected")
    void emptyFileRejected() {
        assertThatThrownBy(() -> importService.importProducts(csv("")))
                .isInstanceOf(DomainRuleException.class);
    }

    @Test
    @DisplayName("the uploaded CSV is archived to S3")
    void uploadIsArchived() {
        var result = importService.importProducts(csv("sku,name,unitPrice\nIMP-ARC-1,Archived,1.00\n"));

        assertThat(result.objectKey()).startsWith("imports/").endsWith(".csv");

        ResponseBytes<?> stored = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(TEST_BUCKET)
                .key(result.objectKey())
                .build());
        assertThat(stored.asUtf8String()).contains("IMP-ARC-1");
    }

    @Test
    @DisplayName("export writes a CSV to S3 and returns a presigned URL, including derived stock")
    void exportsInventoryWithDerivedStock() {
        importService.importProducts(csv("sku,name,unitPrice,reorderLevel\nEXP-001,Exported,5.00,2\n"));
        var product = productRepository.findBySkuIgnoreCaseAndDeletedAtIsNull("EXP-001").orElseThrow();

        stockMovementService.record(new StockMovementRequest(
                product.getId(), MovementType.IN, 7, false, "opening balance", null, null));

        var result = exportService.exportInventory();

        assertThat(result.objectKey()).startsWith("exports/inventory-").endsWith(".csv");
        assertThat(result.downloadUrl()).contains(result.objectKey());
        assertThat(result.rowCount()).isPositive();

        ResponseBytes<?> stored = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(TEST_BUCKET)
                .key(result.objectKey())
                .build());
        String csv = stored.asUtf8String();

        assertThat(csv).startsWith("sku,name,description,category,supplier,unitPrice,reorderLevel");
        assertThat(csv).contains("EXP-001");
        // 7 in stock against a reorder level of 2 — the export carries the derived numbers.
        assertThat(csv).contains(",7,false");
    }
}
