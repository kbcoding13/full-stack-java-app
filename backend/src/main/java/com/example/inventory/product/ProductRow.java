package com.example.inventory.product;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Flat read projection joining a product to its derived stock level in a single query,
 * so listing products never triggers lazy loads or an N+1.
 */
public record ProductRow(
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
        Instant createdAt,
        Instant updatedAt) {

    public boolean isLowStock() {
        return quantityOnHand <= reorderLevel;
    }
}
