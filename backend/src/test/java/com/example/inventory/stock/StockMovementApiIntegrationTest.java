package com.example.inventory.stock;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.inventory.IntegrationTest;
import com.example.inventory.product.Product;
import com.example.inventory.product.ProductRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * Exercises the JSON boundary of the stock endpoint rather than the service directly.
 *
 * <p>These exist because a live smoke test caught a 500 the service-level tests could not: an
 * omitted optional field deserialises as null, and Jackson 3 refuses to map null onto a Java
 * primitive. The UI always sent the field, so nothing else noticed.
 */
@WithMockUser(username = "staff@example.com", roles = "STAFF")
class StockMovementApiIntegrationTest extends IntegrationTest {

    @Autowired
    ProductRepository productRepository;

    Product product;

    @BeforeEach
    void createProduct() {
        product = productRepository.saveAndFlush(
                new Product("API-" + System.nanoTime(), "Widget", null, new BigDecimal("1.00"), 5));
    }

    private String body(String extra) {
        return """
                {"productId":%d,"type":"IN","quantity":10%s}
                """.formatted(product.getId(), extra);
    }

    @Test
    @DisplayName("a request omitting every optional field is accepted")
    void minimalRequestIsAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/stock-movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantityDelta", is(10)));
    }

    @Test
    @DisplayName("an explicit null for an optional flag is accepted")
    void explicitNullFlagIsAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/stock-movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(",\"decrease\":null,\"reason\":null")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("an ADJUST without the decrease flag adds rather than failing")
    void adjustWithoutFlagAdds() throws Exception {
        mockMvc.perform(post("/api/v1/stock-movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"type":"ADJUST","quantity":3}
                                """.formatted(product.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantityDelta", is(3)));
    }

    @Test
    @DisplayName("an ADJUST with decrease true subtracts")
    void adjustWithFlagSubtracts() throws Exception {
        mockMvc.perform(post("/api/v1/stock-movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/stock-movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"type":"ADJUST","quantity":4,"decrease":true}
                                """.formatted(product.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantityDelta", is(-4)));
    }

    @Test
    @DisplayName("overselling returns 422 with a usable message, not a 500")
    void oversellReturns422() throws Exception {
        mockMvc.perform(post("/api/v1/stock-movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"type":"OUT","quantity":9999}
                                """.formatted(product.getId())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title", is("Business rule violated")));
    }

    @Test
    @DisplayName("a quantity of zero is rejected by validation")
    void zeroQuantityRejected() throws Exception {
        mockMvc.perform(post("/api/v1/stock-movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"type":"IN","quantity":0}
                                """.formatted(product.getId())))
                .andExpect(status().isBadRequest());
    }
}
