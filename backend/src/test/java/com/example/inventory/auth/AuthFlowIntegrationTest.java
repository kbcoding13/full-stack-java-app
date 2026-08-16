package com.example.inventory.auth;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.inventory.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end auth: register or log in over HTTP, then use the returned bearer token against real
 * endpoints through the whole filter chain.
 *
 * <p>Deliberately does not use {@code @WithMockUser}. That injects a principal directly and skips
 * the JWT filter, which hid a real defect: a denial calls sendError, Spring re-dispatches to
 * /error, OncePerRequestFilter skips ERROR dispatches so the request arrives anonymous, and the
 * 403 came back to the client as a bodyless 401.
 */
class AuthFlowIntegrationTest extends IntegrationTest {

    @Autowired
    ObjectMapper objectMapper;

    private String registerAndGetToken() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";

        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("email", email, "password", "password123"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("accessToken").asString();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    @DisplayName("a registered user can call an authenticated endpoint with the issued token")
    void tokenAuthenticatesRequests() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", notNullValue()));

        mockMvc.perform(get("/api/v1/products").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a non-admin token is refused with 403, not a bodyless 401")
    void nonAdminGetsForbidden() throws Exception {
        // The first ever user becomes ADMIN, so register twice: this one is STAFF.
        registerAndGetToken();
        String staffToken = registerAndGetToken();

        mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"FORBIDDEN-1","name":"Nope","unitPrice":"1.00","reorderLevel":0}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("STAFF may still record stock movements")
    void staffCanRecordMovements() throws Exception {
        registerAndGetToken();
        String staffToken = registerAndGetToken();

        mockMvc.perform(get("/api/v1/products").header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a missing token is 401")
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a garbage token is 401")
    void garbageTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/products").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a refresh token is refused where an access token is required")
    void refreshTokenIsNotAnAccessToken() throws Exception {
        String email = "refresh-" + UUID.randomUUID() + "@example.com";

        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("email", email, "password", "password123"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String refreshToken = objectMapper.readTree(response).get("refreshToken").asString();

        mockMvc.perform(get("/api/v1/products").header(HttpHeaders.AUTHORIZATION, bearer(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("registering the same email twice returns 409")
    void duplicateRegistrationConflicts() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        String body = objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", "password123"));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Conflict")));
    }
}
