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
     * Pinned rather than :latest so a LocalStack release cannot break CI overnight. S3 is a
     * core community service and is available without restricting SERVICES — passing it made
     * the container exit with code 55 on this image.
     */
    static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:2026.07.4"));

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
