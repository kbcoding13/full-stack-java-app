package com.example.inventory.supplier;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class SupplierDtos {

    private SupplierDtos() {}

    public record SupplierRequest(
            @NotBlank @Size(max = 160) String name,
            @Email @Size(max = 255) String contactEmail,
            @Size(max = 40) String contactPhone,
            @Size(max = 500) String address) {}

    public record SupplierResponse(
            Long id,
            String name,
            String contactEmail,
            String contactPhone,
            String address,
            Instant createdAt,
            Instant updatedAt) {

        static SupplierResponse from(Supplier supplier) {
            return new SupplierResponse(
                    supplier.getId(),
                    supplier.getName(),
                    supplier.getContactEmail(),
                    supplier.getContactPhone(),
                    supplier.getAddress(),
                    supplier.getCreatedAt(),
                    supplier.getUpdatedAt());
        }
    }
}
