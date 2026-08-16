package com.example.inventory.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * All environment-specific knobs in one place, bound from the {@code app.*} config tree.
 * Nothing here may be hardcoded elsewhere — buckets, regions, endpoints and origins all
 * change between local, CI and deployed environments.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, Jwt jwt, Storage storage) {

    public record Cors(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {}

    public record Jwt(
            String secret,
            @DefaultValue("inventory-api") String issuer,
            @DefaultValue("PT15M") Duration accessTokenTtl,
            @DefaultValue("P7D") Duration refreshTokenTtl) {}

    public record Storage(
            String bucket,
            @DefaultValue("us-east-1") String region,
            String endpoint,
            @DefaultValue("false") boolean pathStyleAccess,
            @DefaultValue("PT15M") Duration presignTtl,
            @DefaultValue("5242880") long maxImageBytes,
            @DefaultValue("image/jpeg,image/png,image/webp") List<String> allowedImageTypes) {

        /** Blank for real AWS; set to the LocalStack/MinIO URL locally. */
        public boolean hasCustomEndpoint() {
            return endpoint != null && !endpoint.isBlank();
        }
    }
}
