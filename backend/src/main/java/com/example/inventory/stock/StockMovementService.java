package com.example.inventory.stock;

import com.example.inventory.auth.AuthenticatedUser;
import com.example.inventory.common.ApiExceptions.DomainRuleException;
import com.example.inventory.common.ApiExceptions.NotFoundException;
import com.example.inventory.product.Product;
import com.example.inventory.product.ProductRepository;
import com.example.inventory.product.ProductStockRepository;
import com.example.inventory.stock.StockDtos.StockLevelResponse;
import com.example.inventory.stock.StockDtos.StockMovementRequest;
import com.example.inventory.stock.StockDtos.StockMovementResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way stock ever changes.
 *
 * <p>Movements are appended to the ledger; a database trigger applies the delta to
 * {@code product_stock} inside the same transaction, and a CHECK constraint there is the final
 * guard against negative stock. This service checks first so callers get a clean 422 rather than
 * a constraint violation.
 */
@Service
public class StockMovementService {

    private final StockMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final ProductStockRepository stockRepository;

    public StockMovementService(
            StockMovementRepository movementRepository,
            ProductRepository productRepository,
            ProductStockRepository stockRepository) {
        this.movementRepository = movementRepository;
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public StockMovementResponse record(StockMovementRequest request) {
        Product product = productRepository
                .findByIdAndDeletedAtIsNull(request.productId())
                .orElseThrow(() -> new NotFoundException("Product", request.productId()));

        int delta = signedDelta(request);
        int currentQuantity = currentQuantity(product.getId());

        if (currentQuantity + delta < 0) {
            throw new DomainRuleException(
                    "Cannot remove %d units: only %d in stock".formatted(Math.abs(delta), currentQuantity));
        }

        StockMovement movement = movementRepository.save(new StockMovement(
                product,
                request.type(),
                delta,
                request.reason(),
                request.reference(),
                request.occurredAt(),
                currentUserEmail()));

        return StockMovementResponse.from(movement);
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> listForProduct(Long productId, Pageable pageable) {
        if (productRepository.findByIdAndDeletedAtIsNull(productId).isEmpty()) {
            throw new NotFoundException("Product", productId);
        }

        return movementRepository
                .findByProductIdOrderByOccurredAtDescIdDesc(productId, pageable)
                .map(StockMovementResponse::from);
    }

    @Transactional(readOnly = true)
    public StockLevelResponse level(Long productId) {
        Product product = productRepository
                .findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new NotFoundException("Product", productId));

        int quantity = currentQuantity(productId);
        return new StockLevelResponse(
                productId, quantity, product.getReorderLevel(), quantity <= product.getReorderLevel());
    }

    /**
     * Recomputes the on-hand quantity straight from the ledger and compares it to the
     * materialised value. Any mismatch means the trigger was bypassed.
     */
    @Transactional(readOnly = true)
    public boolean isConsistent(Long productId) {
        return movementRepository.sumDeltasForProduct(productId) == currentQuantity(productId);
    }

    private int currentQuantity(Long productId) {
        return stockRepository
                .findByProductId(productId)
                .map(stock -> stock.getQuantity())
                .orElse(0);
    }

    /** Turns the user-facing positive quantity plus type into the signed ledger delta. */
    private int signedDelta(StockMovementRequest request) {
        int magnitude = request.quantity();

        return switch (request.type()) {
            case IN -> magnitude;
            case OUT -> -magnitude;
            case ADJUST -> request.decrease() ? -magnitude : magnitude;
        };
    }

    private String currentUserEmail() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.getUsername();
        }
        return authentication == null ? "system" : authentication.getName();
    }
}
