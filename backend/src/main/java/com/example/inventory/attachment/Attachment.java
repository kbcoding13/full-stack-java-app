package com.example.inventory.attachment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A document tied to a product, supplier, or stock movement — a delivery note, invoice, or
 * packing slip. Stores the S3 object key only; download URLs are presigned at read time.
 *
 * <p>Polymorphic by {@code entityType} + {@code entityId} rather than a foreign key, because a
 * single attachment table serving three owners is simpler than three near-identical tables.
 * The trade-off is that referential integrity is enforced in the service, not the schema.
 */
@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 40)
    private AttachmentEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "original_name")
    private String originalName;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private String createdBy;

    protected Attachment() {
        // JPA
    }

    public Attachment(
            AttachmentEntityType entityType,
            Long entityId,
            String objectKey,
            String originalName,
            String contentType,
            Long sizeBytes,
            String createdBy) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.objectKey = objectKey;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public AttachmentEntityType getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
