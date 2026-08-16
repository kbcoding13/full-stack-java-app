package com.example.inventory.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class CategoryDtos {

    private CategoryDtos() {}

    public record CategoryRequest(
            @NotBlank @Size(max = 120) String name, @Size(max = 500) String description) {}

    public record CategoryResponse(
            Long id, String name, String description, Instant createdAt, Instant updatedAt) {

        static CategoryResponse from(Category category) {
            return new CategoryResponse(
                    category.getId(),
                    category.getName(),
                    category.getDescription(),
                    category.getCreatedAt(),
                    category.getUpdatedAt());
        }
    }
}
