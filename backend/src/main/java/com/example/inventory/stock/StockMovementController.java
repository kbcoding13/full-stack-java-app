package com.example.inventory.stock;

import com.example.inventory.stock.StockDtos.StockLevelResponse;
import com.example.inventory.stock.StockDtos.StockMovementRequest;
import com.example.inventory.stock.StockDtos.StockMovementResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StockMovementController {

    private final StockMovementService stockMovementService;

    public StockMovementController(StockMovementService stockMovementService) {
        this.stockMovementService = stockMovementService;
    }

    /** The only endpoint that changes stock. STAFF and ADMIN may both post movements. */
    @PostMapping("/api/v1/stock-movements")
    @ResponseStatus(HttpStatus.CREATED)
    public StockMovementResponse record(@Valid @RequestBody StockMovementRequest request) {
        return stockMovementService.record(request);
    }

    @GetMapping("/api/v1/products/{productId}/movements")
    public Page<StockMovementResponse> listForProduct(
            @PathVariable Long productId, @PageableDefault(size = 20) Pageable pageable) {
        return stockMovementService.listForProduct(productId, pageable);
    }

    @GetMapping("/api/v1/products/{productId}/stock")
    public StockLevelResponse level(@PathVariable Long productId) {
        return stockMovementService.level(productId);
    }
}
