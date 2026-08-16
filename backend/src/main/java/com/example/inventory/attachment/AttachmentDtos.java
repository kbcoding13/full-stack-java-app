package com.example.inventory.attachment;

import java.time.Instant;

public final class AttachmentDtos {

    private AttachmentDtos() {}

    public record AttachmentResponse(
            Long id,
            AttachmentEntityType entityType,
            Long entityId,
            String originalName,
            String contentType,
            Long sizeBytes,
            String downloadUrl,
            Instant createdAt,
            String createdBy) {}
}
