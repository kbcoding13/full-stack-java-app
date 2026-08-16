package com.example.inventory.storage;

import com.example.inventory.config.AppProperties;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Builds the S3 clients purely from configuration, so the same code runs against LocalStack
 * locally and real S3 in a deployed environment. No region, bucket or endpoint is hardcoded.
 */
@Configuration
public class StorageConfig {

    private final AppProperties.Storage properties;

    public StorageConfig(AppProperties properties) {
        this.properties = properties.storage();
    }

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.pathStyleAccess())
                        .build());

        if (properties.hasCustomEndpoint()) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        var builder = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.pathStyleAccess())
                        .build());

        if (properties.hasCustomEndpoint()) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        return builder.build();
    }

    /**
     * LocalStack ignores credential values but the SDK still requires some; a deployed
     * environment uses the default chain (instance role, env vars, profile).
     */
    private software.amazon.awssdk.auth.credentials.AwsCredentialsProvider credentialsProvider() {
        if (properties.hasCustomEndpoint()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
        }
        return DefaultCredentialsProvider.builder().build();
    }
}
