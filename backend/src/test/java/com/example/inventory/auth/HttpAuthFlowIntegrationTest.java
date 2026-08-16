package com.example.inventory.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.inventory.IntegrationTest;
import com.example.inventory.TestcontainersConfiguration;
import com.example.inventory.product.Product;
import com.example.inventory.product.ProductRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Runs against a real servlet container rather than MockMvc.
 *
 * <p>This exists because MockMvc cannot catch a whole class of defect: when Spring Security denies
 * a request it calls {@code sendError}, and the container re-dispatches to /error through the
 * filter chain again. MockMvc performs no ERROR dispatch, so a 403 looked correct in every
 * MockMvc test while real clients received a bodyless 401. Only real HTTP reproduces it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class HttpAuthFlowIntegrationTest {

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.endpoint", () -> IntegrationTest.LOCALSTACK.getEndpoint().toString());
        registry.add("app.storage.region", IntegrationTest.LOCALSTACK::getRegion);
        registry.add("app.storage.bucket", () -> "inventory-test");
        registry.add("app.storage.path-style-access", () -> true);
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ProductRepository productRepository;

    private String registerAndGetToken() {
        var body = Map.of("email", "http-" + UUID.randomUUID() + "@example.com", "password", "password123");

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/v1/auth/register",
                HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders(null)),
                new org.springframework.core.ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("accessToken");
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    @Test
    @DisplayName("a non-admin token gets a real 403 over HTTP, not a 401 from the error dispatch")
    void nonAdminGetsForbiddenOverRealHttp() {
        // Only the very first account in a fresh database is ADMIN; every later one is STAFF,
        // and this suite shares a database, so a freshly registered user is always non-admin.
        String staffToken = registerAndGetToken();

        ResponseEntity<String> response = rest.exchange(
                "/api/v1/products",
                HttpMethod.POST,
                new HttpEntity<>(
                        """
                        {"sku":"HTTP-FORBIDDEN-1","name":"Nope","unitPrice":"1.00","reorderLevel":0}
                        """,
                        jsonHeaders(staffToken)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("an authenticated read succeeds over HTTP")
    void authenticatedReadSucceeds() {
        String token = registerAndGetToken();

        ResponseEntity<String> response = rest.exchange(
                "/api/v1/products", HttpMethod.GET, new HttpEntity<>(jsonHeaders(token)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("an anonymous request is 401 over HTTP")
    void anonymousIsUnauthorized() {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/products", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("a domain rule violation keeps its 422 and problem body through the error dispatch")
    void domainRuleViolationKeepsStatusAndBody() {
        String token = registerAndGetToken();

        // Created directly rather than over HTTP: catalog writes need ADMIN, and this test is
        // about the 422 surviving the error dispatch, not about who may create products.
        Product product = productRepository.saveAndFlush(
                new Product("HTTP-OVERSELL-" + System.nanoTime(), "Widget", null, BigDecimal.ONE, 0));

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/v1/stock-movements",
                HttpMethod.POST,
                new HttpEntity<>(
                        """
                        {"productId":%d,"type":"OUT","quantity":5}
                        """.formatted(product.getId()),
                        jsonHeaders(token)),
                new org.springframework.core.ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).containsEntry("title", "Business rule violated");
    }
}
