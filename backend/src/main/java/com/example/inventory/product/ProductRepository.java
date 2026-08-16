package com.example.inventory.product;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndDeletedAtIsNull(Long id);

    /** Used by the CSV importer to decide between insert and update. */
    Optional<Product> findBySkuIgnoreCaseAndDeletedAtIsNull(String sku);

    @Query("""
            SELECT COUNT(p) > 0 FROM Product p
            WHERE p.deletedAt IS NULL
              AND UPPER(p.sku) = UPPER(:sku)
              AND (:excludeId IS NULL OR p.id <> :excludeId)
            """)
    boolean existsBySku(@Param("sku") String sku, @Param("excludeId") Long excludeId);

    @Query(
            value = """
                    SELECT new com.example.inventory.product.ProductRow(
                        p.id, p.sku, p.name, p.description, p.unitPrice, p.reorderLevel,
                        c.id, c.name, s.id, s.name,
                        COALESCE(st.quantity, 0),
                        p.createdAt, p.updatedAt)
                    FROM Product p
                    LEFT JOIN p.category c
                    LEFT JOIN p.supplier s
                    LEFT JOIN ProductStock st ON st.productId = p.id
                    WHERE p.deletedAt IS NULL
                      AND (:search IS NULL
                           OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                           OR UPPER(p.sku) LIKE UPPER(CONCAT('%', :search, '%')))
                      AND (:categoryId IS NULL OR c.id = :categoryId)
                      AND (:supplierId IS NULL OR s.id = :supplierId)
                      AND (:lowStock = FALSE OR COALESCE(st.quantity, 0) <= p.reorderLevel)
                    """,
            countQuery = """
                    SELECT COUNT(p)
                    FROM Product p
                    LEFT JOIN p.category c
                    LEFT JOIN p.supplier s
                    LEFT JOIN ProductStock st ON st.productId = p.id
                    WHERE p.deletedAt IS NULL
                      AND (:search IS NULL
                           OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                           OR UPPER(p.sku) LIKE UPPER(CONCAT('%', :search, '%')))
                      AND (:categoryId IS NULL OR c.id = :categoryId)
                      AND (:supplierId IS NULL OR s.id = :supplierId)
                      AND (:lowStock = FALSE OR COALESCE(st.quantity, 0) <= p.reorderLevel)
                    """)
    Page<ProductRow> search(
            @Param("search") String search,
            @Param("categoryId") Long categoryId,
            @Param("supplierId") Long supplierId,
            @Param("lowStock") boolean lowStock,
            Pageable pageable);

    @Query("""
            SELECT new com.example.inventory.product.ProductRow(
                p.id, p.sku, p.name, p.description, p.unitPrice, p.reorderLevel,
                c.id, c.name, s.id, s.name,
                COALESCE(st.quantity, 0),
                p.createdAt, p.updatedAt)
            FROM Product p
            LEFT JOIN p.category c
            LEFT JOIN p.supplier s
            LEFT JOIN ProductStock st ON st.productId = p.id
            WHERE p.id = :id AND p.deletedAt IS NULL
            """)
    Optional<ProductRow> findRowById(@Param("id") Long id);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.deletedAt IS NULL AND p.category.id = :categoryId")
    long countByCategory(@Param("categoryId") Long categoryId);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.deletedAt IS NULL AND p.supplier.id = :supplierId")
    long countBySupplier(@Param("supplierId") Long supplierId);
}
