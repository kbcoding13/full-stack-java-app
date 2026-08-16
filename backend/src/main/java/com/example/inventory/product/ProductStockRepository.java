package com.example.inventory.product;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read-only access to derived stock levels. Writes happen through the stock ledger. */
public interface ProductStockRepository extends JpaRepository<ProductStock, Long> {

    Optional<ProductStock> findByProductId(Long productId);
}
