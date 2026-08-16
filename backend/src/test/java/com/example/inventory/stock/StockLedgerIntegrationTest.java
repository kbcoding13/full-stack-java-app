package com.example.inventory.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.inventory.IntegrationTest;
import com.example.inventory.category.Category;
import com.example.inventory.category.CategoryRepository;
import com.example.inventory.common.ApiExceptions.DomainRuleException;
import com.example.inventory.product.Product;
import com.example.inventory.product.ProductRepository;
import com.example.inventory.stock.StockDtos.StockMovementRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * The stock ledger is the core invariant of this system, so it gets the most direct coverage:
 * derived quantity must always equal the sum of movements, and nothing may drive it negative.
 */
@WithMockUser(username = "tester@example.com", roles = "ADMIN")
class StockLedgerIntegrationTest extends IntegrationTest {

    @Autowired
    StockMovementService stockMovementService;

    @Autowired
    StockMovementRepository movementRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Product product;

    @BeforeEach
    void createProduct() {
        Category category = categoryRepository.save(new Category("Hardware-" + System.nanoTime(), null));
        product = productRepository.saveAndFlush(
                new Product("SKU-" + System.nanoTime(), "Widget", null, new BigDecimal("9.99"), 5));
        product.setCategory(category);
        productRepository.flush();
    }

    @Test
    @DisplayName("a new product starts at zero on hand")
    void newProductStartsAtZero() {
        assertThat(stockMovementService.level(product.getId()).quantityOnHand()).isZero();
    }

    @Test
    @DisplayName("IN adds, OUT subtracts, and the total matches the ledger sum")
    void movementsAccumulate() {
        record(MovementType.IN, 100, false);
        record(MovementType.OUT, 30, false);
        record(MovementType.IN, 5, false);

        assertThat(stockMovementService.level(product.getId()).quantityOnHand()).isEqualTo(75);
        assertThat(movementRepository.sumDeltasForProduct(product.getId())).isEqualTo(75);
        assertThat(stockMovementService.isConsistent(product.getId())).isTrue();
    }

    @Test
    @DisplayName("ADJUST can correct in either direction")
    void adjustHandlesBothDirections() {
        record(MovementType.IN, 50, false);
        record(MovementType.ADJUST, 8, true);
        record(MovementType.ADJUST, 3, false);

        assertThat(stockMovementService.level(product.getId()).quantityOnHand()).isEqualTo(45);
        assertThat(stockMovementService.isConsistent(product.getId())).isTrue();
    }

    @Test
    @DisplayName("an OUT larger than stock is rejected with a domain rule error")
    void cannotOversell() {
        record(MovementType.IN, 10, false);

        assertThatThrownBy(() -> record(MovementType.OUT, 11, false))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("only 10 in stock");

        assertThat(stockMovementService.level(product.getId()).quantityOnHand()).isEqualTo(10);
    }

    @Test
    @DisplayName("a decreasing ADJUST cannot drive stock negative either")
    void cannotAdjustBelowZero() {
        record(MovementType.IN, 4, false);

        assertThatThrownBy(() -> record(MovementType.ADJUST, 5, true))
                .isInstanceOf(DomainRuleException.class);
    }

    @Test
    @DisplayName("the database rejects a direct UPDATE of the ledger")
    void ledgerRowsAreImmutable() {
        record(MovementType.IN, 10, false);
        Long movementId = jdbcTemplate.queryForObject(
                "SELECT id FROM stock_movements WHERE product_id = ? LIMIT 1", Long.class, product.getId());

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE stock_movements SET quantity_delta = 999 WHERE id = ?", movementId))
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("the database rejects a DELETE from the ledger")
    void ledgerRowsCannotBeDeleted() {
        record(MovementType.IN, 10, false);

        assertThatThrownBy(() ->
                        jdbcTemplate.update("DELETE FROM stock_movements WHERE product_id = ?", product.getId()))
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("the summary table cannot be pushed negative even by raw SQL")
    void summaryTableGuardsAgainstNegative() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE product_stock SET quantity = -1 WHERE product_id = ?", product.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void record(MovementType type, int quantity, boolean decrease) {
        stockMovementService.record(
                new StockMovementRequest(product.getId(), type, quantity, decrease, "test", null, null));
    }
}
