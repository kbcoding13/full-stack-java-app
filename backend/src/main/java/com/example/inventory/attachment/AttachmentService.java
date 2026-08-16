package com.example.inventory.attachment;

import com.example.inventory.attachment.AttachmentDtos.AttachmentResponse;
import com.example.inventory.auth.AuthenticatedUser;
import com.example.inventory.common.ApiExceptions.NotFoundException;
import com.example.inventory.product.ProductRepository;
import com.example.inventory.stock.StockMovementRepository;
import com.example.inventory.storage.StorageService;
import com.example.inventory.supplier.SupplierRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Documents attached to a product, supplier, or stock movement.
 *
 * <p>Proxy upload, like CSV import: the bytes come through the API so content type and size can
 * be checked before we trust them. Product images take the presigned route instead — they are
 * displayed, not inspected.
 */
@Service
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final StockMovementRepository movementRepository;
    private final StorageService storageService;

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            ProductRepository productRepository,
            SupplierRepository supplierRepository,
            StockMovementRepository movementRepository,
            StorageService storageService) {
        this.attachmentRepository = attachmentRepository;
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.movementRepository = movementRepository;
        this.storageService = storageService;
    }

    /** STAFF may attach documents — they are the ones recording deliveries against movements. */
    @Transactional
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public AttachmentResponse upload(AttachmentEntityType entityType, Long entityId, MultipartFile file) {
        requireOwnerExists(entityType, entityId);
        storageService.validateAttachment(file.getContentType(), file.getSize());

        String objectKey = storageService.attachmentKey(entityType.name(), entityId, file.getOriginalFilename());
        storageService.upload(objectKey, file);

        Attachment attachment = attachmentRepository.save(new Attachment(
                entityType,
                entityId,
                objectKey,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                currentUserEmail()));

        return toResponse(attachment);
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(AttachmentEntityType entityType, Long entityId) {
        requireOwnerExists(entityType, entityId);

        return attachmentRepository.findByEntityTypeAndEntityIdOrderByIdDesc(entityType, entityId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long id) {
        Attachment attachment =
                attachmentRepository.findById(id).orElseThrow(() -> new NotFoundException("Attachment", id));

        attachmentRepository.delete(attachment);
        storageService.delete(attachment.getObjectKey());
    }

    /**
     * The owner is a polymorphic reference with no foreign key behind it, so the existence check
     * has to live here.
     */
    private void requireOwnerExists(AttachmentEntityType entityType, Long entityId) {
        boolean exists = switch (entityType) {
            case PRODUCT -> productRepository.findByIdAndDeletedAtIsNull(entityId).isPresent();
            case SUPPLIER -> supplierRepository.findByIdAndDeletedAtIsNull(entityId).isPresent();
            case STOCK_MOVEMENT -> movementRepository.existsById(entityId);
        };

        if (!exists) {
            throw new NotFoundException(entityType.name(), entityId);
        }
    }

    private AttachmentResponse toResponse(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getEntityType(),
                attachment.getEntityId(),
                attachment.getOriginalName(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                storageService.presignDownload(attachment.getObjectKey()),
                attachment.getCreatedAt(),
                attachment.getCreatedBy());
    }

    private String currentUserEmail() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.getUsername();
        }
        return authentication == null ? "system" : authentication.getName();
    }
}
