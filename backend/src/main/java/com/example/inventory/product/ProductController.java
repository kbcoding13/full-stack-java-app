package com.example.inventory.product;

import com.example.inventory.product.ProductDtos.ConfirmImageRequest;
import com.example.inventory.product.ProductDtos.PresignImageRequest;
import com.example.inventory.product.ProductDtos.PresignImageResponse;
import com.example.inventory.product.ProductDtos.ProductImageResponse;
import com.example.inventory.product.ProductDtos.ProductRequest;
import com.example.inventory.product.ProductDtos.ProductResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;
    private final ProductImageService imageService;

    public ProductController(ProductService productService, ProductImageService imageService) {
        this.productService = productService;
        this.imageService = imageService;
    }

    @GetMapping
    public Page<ProductResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(defaultValue = "false") boolean lowStock,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return productService.list(search, categoryId, supplierId, lowStock, pageable);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return productService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }

    // --- images -------------------------------------------------------------

    @GetMapping("/{id}/images")
    public List<ProductImageResponse> listImages(@PathVariable Long id) {
        return imageService.list(id);
    }

    @PostMapping("/{id}/images/presign")
    public PresignImageResponse presignImage(@PathVariable Long id, @Valid @RequestBody PresignImageRequest request) {
        return imageService.presign(id, request);
    }

    @PostMapping("/{id}/images/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductImageResponse confirmImage(@PathVariable Long id, @Valid @RequestBody ConfirmImageRequest request) {
        return imageService.confirm(id, request);
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        imageService.delete(id, imageId);
    }
}
