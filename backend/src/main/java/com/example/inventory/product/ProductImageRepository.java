package com.example.inventory.product;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderByPrimaryDescIdAsc(Long productId);

    Optional<ProductImage> findByIdAndProductId(Long id, Long productId);

    boolean existsByObjectKey(String objectKey);

    long countByProductId(Long productId);

    @Modifying
    @Query("UPDATE ProductImage i SET i.primary = FALSE WHERE i.product.id = :productId")
    void clearPrimaryFor(@Param("productId") Long productId);
}
