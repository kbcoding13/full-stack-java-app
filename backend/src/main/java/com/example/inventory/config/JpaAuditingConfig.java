package com.example.inventory.config;

import com.example.inventory.auth.AuthenticatedUser;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    /** Stamps createdBy/updatedBy with the caller's email; "system" for unauthenticated writes. */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.of("system");
            }
            if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
                return Optional.of(user.getUsername());
            }
            return Optional.of(authentication.getName());
        };
    }
}
