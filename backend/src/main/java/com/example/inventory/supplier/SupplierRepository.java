package com.example.inventory.supplier;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByIdAndDeletedAtIsNull(Long id);

    /** Used by the CSV importer, which creates suppliers on demand by name. */
    Optional<Supplier> findFirstByNameIgnoreCaseAndDeletedAtIsNull(String name);

    @Query("""
            SELECT s FROM Supplier s
            WHERE s.deletedAt IS NULL
              AND (CAST(:search AS String) IS NULL
                   OR LOWER(s.name) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%')))
            """)
    Page<Supplier> search(@Param("search") String search, Pageable pageable);

    @Query("""
            SELECT COUNT(s) > 0 FROM Supplier s
            WHERE s.deletedAt IS NULL
              AND LOWER(s.name) = LOWER(:name)
              AND (CAST(:excludeId AS Long) IS NULL OR s.id <> :excludeId)
            """)
    boolean existsByNameIgnoreCase(@Param("name") String name, @Param("excludeId") Long excludeId);
}
