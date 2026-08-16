package com.example.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Boots the whole application against real Postgres and LocalStack, migrations included. */
class InventoryApplicationTests extends IntegrationTest {

    @Test
    @DisplayName("the application context loads and Flyway has migrated")
    void contextLoads() {
        assertThat(webApplicationContext).isNotNull();
        assertThat(s3Client).isNotNull();
    }
}
