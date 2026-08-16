package com.example.inventory.storage;

import com.example.inventory.common.ApiExceptions.DomainRuleException;
import com.example.inventory.common.ApiExceptions.StorageException;
import com.example.inventory.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * The only place that talks to S3.
 *
 * <p>Two upload paths by design: product images get a presigned PUT so bytes never touch the API,
 * while CSV imports and attachments are proxied through here because they need server-side
 * validation before we trust them. Every download is a short-lived presigned GET — the bucket is
 * private and object keys, never URLs, are what get persisted.
 */
@Service
public class StorageService {

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy/MM");

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final AppProperties.Storage properties;

    public StorageService(S3Client s3Client, S3Presigner presigner, AppProperties properties) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.properties = properties.storage();
    }

    // --- key builders -------------------------------------------------------

    public String productImageKey(Long productId, String originalFilename) {
        return "products/%d/images/%s%s".formatted(productId, UUID.randomUUID(), extensionOf(originalFilename));
    }

    public String importKey() {
        return "imports/%s/%s.csv".formatted(LocalDate.now().format(YEAR_MONTH), UUID.randomUUID());
    }

    public String exportKey() {
        return "exports/inventory-%d.csv".formatted(System.currentTimeMillis());
    }

    public String attachmentKey(String entityType, Long entityId, String originalName) {
        return "attachments/%s/%d/%s-%s"
                .formatted(entityType.toLowerCase(), entityId, UUID.randomUUID(), sanitize(originalName));
    }

    // --- presigned URLs -----------------------------------------------------

    /** Issues a presigned PUT so the browser can upload an image straight to S3. */
    public PresignedUpload presignUpload(String key, String contentType) {
        validateImageContentType(contentType);

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .build();

        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(properties.presignTtl())
                .putObjectRequest(objectRequest)
                .build());

        return new PresignedUpload(
                presigned.url().toString(), key, properties.presignTtl().toSeconds());
    }

    /** Builds a short-lived download URL. Never persist the result. */
    public String presignDownload(String key) {
        var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(properties.presignTtl())
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .build())
                .build());

        return presigned.url().toString();
    }

    public String presignDownload(String key, Duration ttl) {
        var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .build())
                .build());

        return presigned.url().toString();
    }

    // --- proxy uploads ------------------------------------------------------

    /** Streams a validated multipart file to S3. Used for CSV imports and attachments. */
    public void upload(String key, MultipartFile file) {
        try (InputStream stream = file.getInputStream()) {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(key)
                            .contentType(file.getContentType())
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(stream, file.getSize()));
        } catch (IOException | S3Exception ex) {
            throw new StorageException("Failed to upload " + key, ex);
        }
    }

    public void upload(String key, byte[] content, String contentType) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(key)
                            .contentType(contentType)
                            .contentLength((long) content.length)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (S3Exception ex) {
            throw new StorageException("Failed to upload " + key, ex);
        }
    }

    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
        } catch (S3Exception ex) {
            throw new StorageException("Failed to delete " + key, ex);
        }
    }

    // --- validation ---------------------------------------------------------

    public void validateImageContentType(String contentType) {
        if (contentType == null || !properties.allowedImageTypes().contains(contentType.toLowerCase())) {
            throw new DomainRuleException(
                    "Unsupported image type. Allowed: " + String.join(", ", properties.allowedImageTypes()));
        }
    }

    public void validateAttachment(String contentType, long sizeBytes) {
        if (sizeBytes > properties.maxAttachmentBytes()) {
            throw new DomainRuleException(
                    "Attachment exceeds the maximum size of %d bytes".formatted(properties.maxAttachmentBytes()));
        }
        if (contentType == null
                || properties.allowedAttachmentTypes().stream()
                        .noneMatch(allowed -> contentType.toLowerCase().startsWith(allowed))) {
            throw new DomainRuleException(
                    "Unsupported attachment type. Allowed: "
                            + String.join(", ", properties.allowedAttachmentTypes()));
        }
    }

    public void validateImageSize(long sizeBytes) {
        if (sizeBytes > properties.maxImageBytes()) {
            throw new DomainRuleException("Image exceeds the maximum size of %d bytes".formatted(
                    properties.maxImageBytes()));
        }
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot).toLowerCase();
    }

    private String sanitize(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }
        return filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /** Presigned PUT target the browser uploads to, plus the key to send back on confirm. */
    public record PresignedUpload(String uploadUrl, String key, long expiresInSeconds) {}
}
