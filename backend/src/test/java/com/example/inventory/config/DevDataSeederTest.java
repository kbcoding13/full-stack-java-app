package com.example.inventory.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.inventory.IntegrationTest;
import com.example.inventory.product.ProductRepository;
import com.example.inventory.stock.StockMovementRepository;
import com.example.inventory.stock.StockMovementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

/**
 * The seeder only runs under a profile, so without a test it would rot unnoticed. This also pins
 * the rule that seeded stock arrives through the ledger rather than a quantity column.
 */
@ActiveProfiles("seed")
class DevDataSeederTest extends IntegrationTest {

    @Autowired
    DevDataSeeder seeder;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    StockMovementRepository movementRepository;

    @Autowired
    StockMovementService stockMovementService;

    @Test
    @DisplayName("the seed profile populates a demo catalog")
    void seedsCatalog() {
        assertThat(productRepository.findBySkuIgnoreCaseAndDeletedAtIsNull("TL-1001")).isPresent();
        assertThat(productRepository.findBySkuIgnoreCaseAndDeletedAtIsNull("SF-3002")).isPresent();
    }

    @Test
    @DisplayName("seeded stock comes from movements and reconciles with the ledger")
    void seededStockMatchesLedger() {
        var hammer = productRepository.findBySkuIgnoreCaseAndDeletedAtIsNull("TL-1001").orElseThrow();

        // 60 in, 12 out.
        assertThat(stockMovementService.level(hammer.getId()).quantityOnHand()).isEqualTo(48);
        assertThat(movementRepository.sumDeltasForProduct(hammer.getId())).isEqualTo(48);
        assertThat(stockMovementService.isConsistent(hammer.getId())).isTrue();
    }

    @Test
    @DisplayName("at least one seeded product is low stock, so the filter has something to show")
    void includesALowStockProduct() {
        var bolts = productRepository.findBySkuIgnoreCaseAndDeletedAtIsNull("FS-2002").orElseThrow();
        var level = stockMovementService.level(bolts.getId());

        assertThat(level.quantityOnHand()).isEqualTo(12);
        assertThat(level.lowStock()).isTrue();
    }

    @Test
    @DisplayName("re-running the seeder creates nothing and adds no movements")
    void isIdempotent() {
        long movementsBefore = movementRepository.count();

        // The runner already seeded at context startup, so a second pass must be a no-op.
        int created = seeder.seed();

        assertThat(created).isZero();
        assertThat(movementRepository.count()).isEqualTo(movementsBefore);
    }
}
