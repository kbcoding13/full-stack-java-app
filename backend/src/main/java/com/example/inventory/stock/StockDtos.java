package com.example.inventory.stock;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class StockDtos {

    private StockDtos() {}

    /**
     * Quantity is always entered as a positive number; {@code type} decides the sign.
     * For ADJUST, {@code decrease} says whether the correction adds or removes.
     *
     * <p>{@code decrease} is boxed so an omitted field means "no", rather than failing
     * deserialisation — Jackson refuses to map a missing value onto a primitive.
     */
    public record StockMovementRequest(
            @NotNull Long productId,
            @NotNull MovementType type,
            @NotNull @Min(1) Integer quantity,
            Boolean decrease,
            @Size(max = 255) String reason,
            @Size(max = 120) String reference,
            Instant occurredAt) {}

    public record StockMovementResponse(
            Long id,
            Long productId,
            String productSku,
            String productName,
            MovementType type,
            int quantity,
            int quantityDelta,
            String reason,
            String reference,
            Instant occurredAt,
            String createdBy) {

        static StockMovementResponse from(StockMovement movement) {
            return new StockMovementResponse(
                    movement.getId(),
                    movement.getProduct().getId(),
                    movement.getProduct().getSku(),
                    movement.getProduct().getName(),
                    movement.getMovementType(),
                    movement.getQuantity(),
                    movement.getQuantityDelta(),
                    movement.getReason(),
                    movement.getReference(),
                    movement.getOccurredAt(),
                    movement.getCreatedBy());
        }
    }

    public record StockLevelResponse(Long productId, int quantityOnHand, int reorderLevel, boolean lowStock) {}
}
