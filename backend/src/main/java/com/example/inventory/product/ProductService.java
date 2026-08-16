package com.example.inventory.product;

import com.example.inventory.category.Category;
import com.example.inventory.category.CategoryRepository;
import com.example.inventory.common.ApiExceptions.ConflictException;
import com.example.inventory.common.ApiExceptions.NotFoundException;
import com.example.inventory.product.ProductDtos.ProductRequest;
import com.example.inventory.product.ProductDtos.ProductResponse;
import com.example.inventory.supplier.Supplier;
import com.example.inventory.supplier.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            SupplierRepository supplierRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> list(
            String search, Long categoryId, Long supplierId, boolean lowStock, Pageable pageable) {
        return productRepository
                .search(blankToNull(search), categoryId, supplierId, lowStock, pageable)
                .map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return productRepository
                .findRowById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new NotFoundException("Product", id));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse create(ProductRequest request) {
        requireUniqueSku(request.sku(), null);

        Product product = new Product(
                request.sku().trim(),
                request.name().trim(),
                request.description(),
                request.unitPrice(),
                request.reorderLevel());
        applyRelations(product, request);

        Product saved = productRepository.saveAndFlush(product);

        // The product_stock row is created by a trigger, so re-read through the projection
        // rather than assuming a quantity here.
        return get(saved.getId());
    }

    /**
     * Updates catalog fields only. There is deliberately no path here to set quantity —
     * stock changes go through the movement ledger.
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = requireLive(id);
        requireUniqueSku(request.sku(), id);

        product.setSku(request.sku().trim());
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setUnitPrice(request.unitPrice());
        product.setReorderLevel(request.reorderLevel());
        applyRelations(product, request);

        productRepository.flush();
        return get(id);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long id) {
        requireLive(id).softDelete();
    }

    /** Shared lookup for other features that need a live product (stock, images). */
    @Transactional(readOnly = true)
    public Product requireLive(Long id) {
        return productRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Product", id));
    }

    private void applyRelations(Product product, ProductRequest request) {
        if (request.categoryId() == null) {
            product.setCategory(null);
        } else {
            Category category = categoryRepository
                    .findByIdAndDeletedAtIsNull(request.categoryId())
                    .orElseThrow(() -> new NotFoundException("Category", request.categoryId()));
            product.setCategory(category);
        }

        if (request.supplierId() == null) {
            product.setSupplier(null);
        } else {
            Supplier supplier = supplierRepository
                    .findByIdAndDeletedAtIsNull(request.supplierId())
                    .orElseThrow(() -> new NotFoundException("Supplier", request.supplierId()));
            product.setSupplier(supplier);
        }
    }

    private void requireUniqueSku(String sku, Long excludeId) {
        if (productRepository.existsBySku(sku.trim(), excludeId)) {
            throw new ConflictException("A product with SKU '%s' already exists".formatted(sku));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
