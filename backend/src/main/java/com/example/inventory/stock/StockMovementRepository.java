package com.example.inventory.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    Page<StockMovement> findByProductIdOrderByOccurredAtDescIdDesc(Long productId, Pageable pageable);

    /**
     * Recomputes on-hand quantity straight from the ledger. Used by tests and reconciliation to
     * prove the materialised {@code product_stock} value has not drifted.
     */
    @Query("SELECT COALESCE(SUM(m.quantityDelta), 0) FROM StockMovement m WHERE m.product.id = :productId")
    int sumDeltasForProduct(@Param("productId") Long productId);
}
