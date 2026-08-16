package com.example.inventory.attachment;

/**
 * What an attachment hangs off. Mirrors the {@code ck_attachments_entity_type} check
 * constraint — adding a value here needs a migration to widen that constraint.
 */
public enum AttachmentEntityType {
    PRODUCT,
    STOCK_MOVEMENT,
    SUPPLIER
}
