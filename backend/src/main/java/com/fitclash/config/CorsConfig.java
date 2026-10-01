// File: src/main/java/com/fitclash/config/CorsConfig.java
package com.fitclash.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global CORS for the Spring MVC layer.
 *
 * Spring Security runs its own CORS filter ahead of MVC, so {@link SecurityConfig}
 * publishes a matching CorsConfigurationSource bean. Both are intentional: the
 * security filter answers pre-flight before authentication, this configurer covers
 * anything dispatched outside the security chain (error dispatches, static paths).
 * Keeping the origin list in one property stops the two from drifting apart -
 * the classic "works in Postman, blocked in the browser" bug.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${fitclash.cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization", "X-FitClash-Daily-Xp-Remaining")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
