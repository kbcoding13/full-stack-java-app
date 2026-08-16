package com.example.inventory.attachment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByEntityTypeAndEntityIdOrderByIdDesc(AttachmentEntityType entityType, Long entityId);

    long countByEntityTypeAndEntityId(AttachmentEntityType entityType, Long entityId);
}
