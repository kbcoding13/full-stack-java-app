package com.example.inventory.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * Materialised on-hand quantity, maintained by the {@code trg_stock_movements_apply} trigger.
 *
 * <p>Mapped as immutable on purpose: the application must never write this table. Stock changes
 * by inserting into {@code stock_movements}. Queries join this entity explicitly rather than
 * mapping it as an association, so it is always obvious that the number is derived.
 */
@Entity
@Immutable
@Table(name = "product_stock")
public class ProductStock {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductStock() {
        // JPA
    }

    public Long getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
