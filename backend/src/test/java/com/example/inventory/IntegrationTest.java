package com.example.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Base for full-stack tests: real Postgres, real S3 via LocalStack, real security filter chain.
 * The LocalStack container is static so all test classes share one instance for the whole run.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    protected static final String TEST_BUCKET = "inventory-test";

    /**
     * Pinned to the last 4.x release on purpose. From the 2026.x CalVer images onward LocalStack
     * requires a LOCALSTACK_AUTH_TOKEN, and without one the container fails license activation
     * and exits with code 55 before it ever logs "Ready." — which surfaces here as an opaque
     * wait-strategy timeout. 4.14.0 needs no token. If this is ever bumped to a 2026.x image,
     * a token has to be supplied as a CI secret at the same time.
     */
    static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.14.0"));

    static {
        LOCALSTACK.start();
    }

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.endpoint", () -> LOCALSTACK.getEndpoint().toString());
        registry.add("app.storage.region", LOCALSTACK::getRegion);
        registry.add("app.storage.bucket", () -> TEST_BUCKET);
        registry.add("app.storage.path-style-access", () -> true);
    }

    @Autowired
    protected WebApplicationContext webApplicationContext;

    @Autowired
    protected S3Client s3Client;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvcAndBucket() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        try {
            s3Client.createBucket(
                    CreateBucketRequest.builder().bucket(TEST_BUCKET).build());
        } catch (S3Exception ex) {
            // Bucket already exists from an earlier test class — fine.
        }
    }
}
