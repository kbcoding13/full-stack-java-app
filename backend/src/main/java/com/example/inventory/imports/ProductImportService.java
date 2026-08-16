package com.example.inventory.imports;

import com.example.inventory.category.Category;
import com.example.inventory.category.CategoryRepository;
import com.example.inventory.common.ApiExceptions.DomainRuleException;
import com.example.inventory.imports.ImportDtos.ImportResult;
import com.example.inventory.imports.ImportDtos.RowError;
import com.example.inventory.product.Product;
import com.example.inventory.product.ProductRepository;
import com.example.inventory.storage.StorageService;
import com.example.inventory.supplier.Supplier;
import com.example.inventory.supplier.SupplierRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk product upsert from CSV.
 *
 * <p>This is the proxy-upload path: the file comes through the API so it can be validated and
 * parsed server-side before anything is trusted, then archived to S3 for audit. Product images
 * take the presigned route instead, because they need no inspection.
 *
 * <p>Note the importer never sets stock. A CSV can create or update catalog rows only; opening
 * quantities have to be recorded as stock movements so the ledger stays the single source of truth.
 */
@Service
public class ProductImportService {

    private static final long MAX_CSV_BYTES = 5 * 1024 * 1024;
    private static final int MAX_ROWS = 10_000;

    private static final List<String> ACCEPTED_CONTENT_TYPES =
            List.of("text/csv", "application/csv", "application/vnd.ms-excel", "text/plain");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final StorageService storageService;

    public ProductImportService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            SupplierRepository supplierRepository,
            StorageService storageService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.storageService = storageService;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ImportResult importProducts(MultipartFile file) {
        validate(file);

        String objectKey = storageService.importKey();
        storageService.upload(objectKey, file);

        List<RowError> errors = new ArrayList<>();
        int total = 0;
        int created = 0;
        int updated = 0;

        try (CSVParser parser = CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .get()
                .parse(new BufferedReader(
                        new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)))) {

            requireHeaders(parser);

            for (CSVRecord row : parser) {
                total++;
                if (total > MAX_ROWS) {
                    throw new DomainRuleException("CSV exceeds the maximum of %d rows".formatted(MAX_ROWS));
                }

                try {
                    if (upsert(row)) {
                        created++;
                    } else {
                        updated++;
                    }
                } catch (RuntimeException ex) {
                    errors.add(new RowError((int) row.getRecordNumber() + 1, get(row, "sku"), ex.getMessage()));
                }
            }
        } catch (IOException ex) {
            throw new DomainRuleException("Could not read the CSV file: " + ex.getMessage());
        }

        return new ImportResult(objectKey, total, created, updated, errors.size(), errors);
    }

    /** Returns true when a new product was created, false when an existing SKU was updated. */
    private boolean upsert(CSVRecord row) {
        String sku = require(row, "sku");
        String name = require(row, "name");

        Optional<Product> existing = productRepository.findBySkuIgnoreCaseAndDeletedAtIsNull(sku);

        Product product = existing.orElseGet(
                () -> new Product(sku, name, null, BigDecimal.ZERO, 0));
        product.setName(name);
        product.setDescription(get(row, "description"));
        product.setUnitPrice(parsePrice(get(row, "unitprice")));
        product.setReorderLevel(parseReorderLevel(get(row, "reorderlevel")));
        product.setCategory(resolveCategory(get(row, "category")));
        product.setSupplier(resolveSupplier(get(row, "supplier")));

        if (existing.isEmpty()) {
            productRepository.save(product);
            return true;
        }
        return false;
    }

    /** Categories and suppliers are created on demand so an import is not blocked by setup order. */
    private Category resolveCategory(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return categoryRepository
                .findFirstByNameIgnoreCaseAndDeletedAtIsNull(name)
                .orElseGet(() -> categoryRepository.save(new Category(name, null)));
    }

    private Supplier resolveSupplier(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return supplierRepository
                .findFirstByNameIgnoreCaseAndDeletedAtIsNull(name)
                .orElseGet(() -> supplierRepository.save(new Supplier(name, null, null, null)));
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DomainRuleException("The uploaded file is empty");
        }
        if (file.getSize() > MAX_CSV_BYTES) {
            throw new DomainRuleException("CSV exceeds the maximum size of %d bytes".formatted(MAX_CSV_BYTES));
        }

        String contentType = file.getContentType();
        if (contentType != null
                && ACCEPTED_CONTENT_TYPES.stream().noneMatch(accepted -> contentType.startsWith(accepted))) {
            throw new DomainRuleException("Expected a CSV file but received " + contentType);
        }
    }

    private void requireHeaders(CSVParser parser) {
        var headers = parser.getHeaderMap().keySet().stream()
                .map(header -> header.toLowerCase().replace(" ", ""))
                .toList();

        if (!headers.contains("sku") || !headers.contains("name")) {
            throw new DomainRuleException("CSV must have at least 'sku' and 'name' columns");
        }
    }

    private static String get(CSVRecord row, String column) {
        for (String header : row.getParser().getHeaderMap().keySet()) {
            if (header.toLowerCase().replace(" ", "").equals(column)) {
                String value = row.get(header);
                return value == null || value.isBlank() ? null : value.trim();
            }
        }
        return null;
    }

    private static String require(CSVRecord row, String column) {
        String value = get(row, column);
        if (value == null) {
            throw new IllegalArgumentException("'%s' is required".formatted(column));
        }
        return value;
    }

    private static BigDecimal parsePrice(String value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal price = new BigDecimal(value);
            if (price.signum() < 0) {
                throw new IllegalArgumentException("unitPrice cannot be negative");
            }
            return price;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("'%s' is not a valid price".formatted(value));
        }
    }

    private static int parseReorderLevel(String value) {
        if (value == null) {
            return 0;
        }
        try {
            int level = Integer.parseInt(value);
            if (level < 0) {
                throw new IllegalArgumentException("reorderLevel cannot be negative");
            }
            return level;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("'%s' is not a valid reorder level".formatted(value));
        }
    }
}
