package com.example.inventory.category;

import com.example.inventory.category.CategoryDtos.CategoryRequest;
import com.example.inventory.category.CategoryDtos.CategoryResponse;
import com.example.inventory.common.ApiExceptions.ConflictException;
import com.example.inventory.common.ApiExceptions.NotFoundException;
import com.example.inventory.product.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponse> list(String search, Pageable pageable) {
        return categoryRepository
                .search(blankToNull(search), pageable)
                .map(CategoryResponse::from);
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long id) {
        return CategoryResponse.from(requireLive(id));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponse create(CategoryRequest request) {
        requireUniqueName(request.name(), null);
        Category category = categoryRepository.save(new Category(request.name(), request.description()));
        return CategoryResponse.from(category);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = requireLive(id);
        requireUniqueName(request.name(), id);

        category.setName(request.name());
        category.setDescription(request.description());
        return CategoryResponse.from(category);
    }

    /** Soft delete — products and their movement history keep referencing this row. */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long id) {
        Category category = requireLive(id);

        long productCount = productRepository.countByCategory(id);
        if (productCount > 0) {
            throw new ConflictException(
                    "Cannot delete a category with %d product(s) still assigned".formatted(productCount));
        }

        category.softDelete();
    }

    private Category requireLive(Long id) {
        return categoryRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Category", id));
    }

    private void requireUniqueName(String name, Long excludeId) {
        if (categoryRepository.existsByNameIgnoreCase(name, excludeId)) {
            throw new ConflictException("A category named '%s' already exists".formatted(name));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
