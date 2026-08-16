package com.example.inventory.supplier;

import com.example.inventory.common.ApiExceptions.ConflictException;
import com.example.inventory.common.ApiExceptions.NotFoundException;
import com.example.inventory.product.ProductRepository;
import com.example.inventory.supplier.SupplierDtos.SupplierRequest;
import com.example.inventory.supplier.SupplierDtos.SupplierResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;

    public SupplierService(SupplierRepository supplierRepository, ProductRepository productRepository) {
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> list(String search, Pageable pageable) {
        return supplierRepository
                .search(blankToNull(search), pageable)
                .map(SupplierResponse::from);
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(Long id) {
        return SupplierResponse.from(requireLive(id));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public SupplierResponse create(SupplierRequest request) {
        requireUniqueName(request.name(), null);

        Supplier supplier = supplierRepository.save(new Supplier(
                request.name(), request.contactEmail(), request.contactPhone(), request.address()));
        return SupplierResponse.from(supplier);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public SupplierResponse update(Long id, SupplierRequest request) {
        Supplier supplier = requireLive(id);
        requireUniqueName(request.name(), id);

        supplier.setName(request.name());
        supplier.setContactEmail(request.contactEmail());
        supplier.setContactPhone(request.contactPhone());
        supplier.setAddress(request.address());
        return SupplierResponse.from(supplier);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long id) {
        Supplier supplier = requireLive(id);

        long productCount = productRepository.countBySupplier(id);
        if (productCount > 0) {
            throw new ConflictException(
                    "Cannot delete a supplier with %d product(s) still assigned".formatted(productCount));
        }

        supplier.softDelete();
    }

    private Supplier requireLive(Long id) {
        return supplierRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Supplier", id));
    }

    private void requireUniqueName(String name, Long excludeId) {
        if (supplierRepository.existsByNameIgnoreCase(name, excludeId)) {
            throw new ConflictException("A supplier named '%s' already exists".formatted(name));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
