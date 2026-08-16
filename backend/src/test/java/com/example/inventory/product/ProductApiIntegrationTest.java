package com.example.inventory.product;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.inventory.IntegrationTest;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

class ProductApiIntegrationTest extends IntegrationTest {

    @Autowired
    ObjectMapper objectMapper;

    private Map<String, Object> productPayload(String sku) {
        return Map.of("sku", sku, "name", "Widget", "unitPrice", "9.99", "reorderLevel", 5);
    }

    @Test
    @DisplayName("anonymous requests are rejected")
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("STAFF can read products")
    void staffCanList() throws Exception {
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("STAFF cannot create products")
    void staffCannotCreate() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productPayload("SKU-STAFF-1"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN can create a product, and it starts with zero derived stock")
    void adminCanCreate() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productPayload("SKU-ADMIN-1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku", is("SKU-ADMIN-1")))
                .andExpect(jsonPath("$.quantityOnHand", is(0)))
                .andExpect(jsonPath("$.lowStock", is(true)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("a duplicate SKU returns 409")
    void duplicateSkuConflicts() throws Exception {
        String body = objectMapper.writeValueAsString(productPayload("SKU-DUP-1"));

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Conflict")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("invalid payloads return 400 with field errors")
    void validationFailsCleanly() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"\",\"name\":\"\",\"unitPrice\":\"9.99\",\"reorderLevel\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("a missing product returns 404 as a ProblemDetail")
    void missingProductReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/products/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title", is("Not found")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("deleting a product is a soft delete: it disappears from reads")
    void deleteIsSoft() throws Exception {
        String response = mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productPayload("SKU-DEL-1"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        int id = objectMapper.readTree(response).get("id").asInt();

        mockMvc.perform(delete("/api/v1/products/{id}", id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/products/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("the product API exposes no way to set quantity directly")
    void quantityIsNotWritable() throws Exception {
        // Sending a quantity is silently ignored — stock only moves through the ledger.
        String response = mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"sku":"SKU-QTY-1","name":"Widget","unitPrice":"1.00",
                                 "reorderLevel":0,"quantityOnHand":500}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(
                        objectMapper.readTree(response).get("quantityOnHand").asInt())
                .isZero();
    }
}
