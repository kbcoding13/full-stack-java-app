package com.example.inventory.stock;

import com.example.inventory.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * One entry in the append-only stock ledger. Database triggers apply the delta to
 * {@code product_stock} and reject any UPDATE or DELETE, so this entity is mapped immutable.
 * To correct a mistake, record a compensating ADJUST movement.
 */
@Entity
@Immutable
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 10)
    private MovementType movementType;

    @Column(name = "quantity_delta", nullable = false)
    private int quantityDelta;

    @Column(length = 255)
    private String reason;

    @Column(length = 120)
    private String reference;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private String createdBy;

    protected StockMovement() {
        // JPA
    }

    public StockMovement(
            Product product,
            MovementType movementType,
            int quantityDelta,
            String reason,
            String reference,
            Instant occurredAt,
            String createdBy) {
        this.product = product;
        this.movementType = movementType;
        this.quantityDelta = quantityDelta;
        this.reason = reason;
        this.reference = reference;
        this.occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public int getQuantityDelta() {
        return quantityDelta;
    }

    /** Absolute quantity as entered by the user; the sign lives in {@link #getMovementType()}. */
    public int getQuantity() {
        return Math.abs(quantityDelta);
    }

    public String getReason() {
        return reason;
    }

    public String getReference() {
        return reference;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
