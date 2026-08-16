package com.example.inventory.category;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByIdAndDeletedAtIsNull(Long id);

    @Query("""
            SELECT c FROM Category c
            WHERE c.deletedAt IS NULL
              AND (:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Category> search(@Param("search") String search, Pageable pageable);

    @Query("""
            SELECT COUNT(c) > 0 FROM Category c
            WHERE c.deletedAt IS NULL
              AND LOWER(c.name) = LOWER(:name)
              AND (:excludeId IS NULL OR c.id <> :excludeId)
            """)
    boolean existsByNameIgnoreCase(@Param("name") String name, @Param("excludeId") Long excludeId);
}
