package com.example.inventory.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.inventory.IntegrationTest;
import com.example.inventory.common.ApiExceptions.DomainRuleException;
import com.example.inventory.common.ApiExceptions.NotFoundException;
import com.example.inventory.product.Product;
import com.example.inventory.product.ProductRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@WithMockUser(username = "staff@example.com", roles = "ADMIN")
class AttachmentIntegrationTest extends IntegrationTest {

    @Autowired
    AttachmentService attachmentService;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    AttachmentRepository attachmentRepository;

    Product product;

    @BeforeEach
    void createProduct() {
        product = productRepository.saveAndFlush(
                new Product("ATT-" + System.nanoTime(), "Widget", null, new BigDecimal("1.00"), 0));
    }

    private MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", "%PDF-1.4 fake".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a document is stored in S3 and returned with a presigned URL")
    void uploadStoresInS3() {
        var response = attachmentService.upload(AttachmentEntityType.PRODUCT, product.getId(), pdf("invoice.pdf"));

        assertThat(response.originalName()).isEqualTo("invoice.pdf");
        assertThat(response.createdBy()).isEqualTo("staff@example.com");
        assertThat(response.downloadUrl()).contains("attachments/product/" + product.getId());

        var stored = attachmentService.list(AttachmentEntityType.PRODUCT, product.getId());
        assertThat(stored).hasSize(1);

        ResponseBytes<?> object = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(TEST_BUCKET)
                .key(objectKeyOf(response.id()))
                .build());
        assertThat(object.asUtf8String()).contains("%PDF");
    }

    @Test
    @DisplayName("attachments are scoped to their owner")
    void listIsScopedToOwner() {
        var other = productRepository.saveAndFlush(
                new Product("ATT-OTHER-" + System.nanoTime(), "Other", null, BigDecimal.ONE, 0));

        attachmentService.upload(AttachmentEntityType.PRODUCT, product.getId(), pdf("a.pdf"));

        assertThat(attachmentService.list(AttachmentEntityType.PRODUCT, product.getId())).hasSize(1);
        assertThat(attachmentService.list(AttachmentEntityType.PRODUCT, other.getId())).isEmpty();
    }

    @Test
    @DisplayName("an executable is rejected on content type")
    void rejectsDisallowedContentType() {
        var exe = new MockMultipartFile(
                "file", "payload.exe", "application/x-msdownload", "MZ".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> attachmentService.upload(AttachmentEntityType.PRODUCT, product.getId(), exe))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("Unsupported attachment type");
    }

    @Test
    @DisplayName("attaching to a product that does not exist is a 404, not an orphan row")
    void rejectsUnknownOwner() {
        assertThatThrownBy(() ->
                        attachmentService.upload(AttachmentEntityType.PRODUCT, 999_999L, pdf("orphan.pdf")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("a soft-deleted product cannot take new attachments")
    void rejectsSoftDeletedOwner() {
        product.softDelete();
        productRepository.flush();

        assertThatThrownBy(() ->
                        attachmentService.upload(AttachmentEntityType.PRODUCT, product.getId(), pdf("late.pdf")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("deleting an attachment removes both the row and the S3 object")
    void deleteRemovesRowAndObject() {
        var response = attachmentService.upload(AttachmentEntityType.PRODUCT, product.getId(), pdf("bye.pdf"));
        String key = objectKeyOf(response.id());

        attachmentService.delete(response.id());

        assertThat(attachmentService.list(AttachmentEntityType.PRODUCT, product.getId())).isEmpty();
        assertThatThrownBy(() -> s3Client.getObjectAsBytes(
                        GetObjectRequest.builder().bucket(TEST_BUCKET).key(key).build()))
                .isInstanceOf(NoSuchKeyException.class);
    }

    private String objectKeyOf(Long attachmentId) {
        return attachmentRepository
                .findById(attachmentId)
                .map(Attachment::getObjectKey)
                .orElseThrow();
    }
}
