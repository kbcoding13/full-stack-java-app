package com.example.inventory.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class ProductDtos {

    private ProductDtos() {}

    /**
     * Note the absence of a quantity field. Stock is derived from the movement ledger, so a
     * product update can never change it — record a stock movement instead.
     */
    public record ProductRequest(
            @NotBlank @Size(max = 64) String sku,
            @NotBlank @Size(max = 200) String name,
            String description,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
            @NotNull @Min(0) Integer reorderLevel,
            Long categoryId,
            Long supplierId) {}

    public record ProductResponse(
            Long id,
            String sku,
            String name,
            String description,
            BigDecimal unitPrice,
            int reorderLevel,
            Long categoryId,
            String categoryName,
            Long supplierId,
            String supplierName,
            int quantityOnHand,
            boolean lowStock,
            Instant createdAt,
            Instant updatedAt) {

        static ProductResponse from(ProductRow row) {
            return new ProductResponse(
                    row.id(),
                    row.sku(),
                    row.name(),
                    row.description(),
                    row.unitPrice(),
                    row.reorderLevel(),
                    row.categoryId(),
                    row.categoryName(),
                    row.supplierId(),
                    row.supplierName(),
                    row.quantityOnHand(),
                    row.isLowStock(),
                    row.createdAt(),
                    row.updatedAt());
        }
    }

    public record PresignImageRequest(
            @NotBlank String filename, @NotBlank String contentType, @NotNull @Min(1) Long sizeBytes) {}

    public record PresignImageResponse(String uploadUrl, String key, long expiresInSeconds) {}

    public record ConfirmImageRequest(
            @NotBlank String key, String contentType, Long sizeBytes, boolean makePrimary) {}

    public record ProductImageResponse(
            Long id, String key, String downloadUrl, String contentType, Long sizeBytes, boolean primary) {}
}
