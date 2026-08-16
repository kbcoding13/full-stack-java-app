package com.example.inventory.supplier;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByIdAndDeletedAtIsNull(Long id);

    @Query("""
            SELECT s FROM Supplier s
            WHERE s.deletedAt IS NULL
              AND (:search IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Supplier> search(@Param("search") String search, Pageable pageable);

    @Query("""
            SELECT COUNT(s) > 0 FROM Supplier s
            WHERE s.deletedAt IS NULL
              AND LOWER(s.name) = LOWER(:name)
              AND (:excludeId IS NULL OR s.id <> :excludeId)
            """)
    boolean existsByNameIgnoreCase(@Param("name") String name, @Param("excludeId") Long excludeId);
}
