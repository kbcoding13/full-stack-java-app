package com.example.inventory.product;

import com.example.inventory.common.ApiExceptions.ConflictException;
import com.example.inventory.common.ApiExceptions.NotFoundException;
import com.example.inventory.product.ProductDtos.ConfirmImageRequest;
import com.example.inventory.product.ProductDtos.PresignImageRequest;
import com.example.inventory.product.ProductDtos.PresignImageResponse;
import com.example.inventory.product.ProductDtos.ProductImageResponse;
import com.example.inventory.storage.StorageService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product images take the presigned-upload path: we hand the browser a short-lived PUT URL,
 * it uploads straight to S3, then calls confirm so the key is persisted. Image bytes never
 * pass through this service.
 */
@Service
public class ProductImageService {

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final StorageService storageService;

    public ProductImageService(
            ProductRepository productRepository,
            ProductImageRepository imageRepository,
            StorageService storageService) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public List<ProductImageResponse> list(Long productId) {
        requireLiveProduct(productId);

        return imageRepository.findByProductIdOrderByPrimaryDescIdAsc(productId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public PresignImageResponse presign(Long productId, PresignImageRequest request) {
        requireLiveProduct(productId);
        storageService.validateImageContentType(request.contentType());
        storageService.validateImageSize(request.sizeBytes());

        String key = storageService.productImageKey(productId, request.filename());
        var presigned = storageService.presignUpload(key, request.contentType());

        return new PresignImageResponse(presigned.uploadUrl(), presigned.key(), presigned.expiresInSeconds());
    }

    /** Registers a key the browser has finished uploading. Called after the direct-to-S3 PUT. */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ProductImageResponse confirm(Long productId, ConfirmImageRequest request) {
        Product product = requireLiveProduct(productId);

        // Guard against a client confirming a key that belongs to a different product.
        String expectedPrefix = "products/%d/images/".formatted(productId);
        if (!request.key().startsWith(expectedPrefix)) {
            throw new ConflictException("Image key does not belong to product " + productId);
        }
        if (imageRepository.existsByObjectKey(request.key())) {
            throw new ConflictException("That image has already been registered");
        }

        boolean makePrimary = request.makePrimary() || imageRepository.countByProductId(productId) == 0;
        if (makePrimary) {
            imageRepository.clearPrimaryFor(productId);
        }

        ProductImage image = imageRepository.save(new ProductImage(
                product, request.key(), request.contentType(), request.sizeBytes(), makePrimary));

        return toResponse(image);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long productId, Long imageId) {
        requireLiveProduct(productId);

        ProductImage image = imageRepository
                .findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new NotFoundException("Image", imageId));

        imageRepository.delete(image);
        storageService.delete(image.getObjectKey());
    }

    private Product requireLiveProduct(Long productId) {
        return productRepository
                .findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new NotFoundException("Product", productId));
    }

    private ProductImageResponse toResponse(ProductImage image) {
        return new ProductImageResponse(
                image.getId(),
                image.getObjectKey(),
                storageService.presignDownload(image.getObjectKey()),
                image.getContentType(),
                image.getSizeBytes(),
                image.isPrimary());
    }
}
