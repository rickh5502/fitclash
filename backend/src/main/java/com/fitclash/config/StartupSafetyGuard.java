// File: src/main/java/com/fitclash/config/StartupSafetyGuard.java
package com.fitclash.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

/**
 * Last line of defense against shipping the committed dev defaults to a real
 * deployment.
 *
 * application.properties' JWT secret and DB password are public (they are
 * committed, and this repo is public) - fine for a contributor's localhost,
 * catastrophic anywhere reachable by anyone else: anyone can read the secret
 * and forge a valid bearer token for any user.
 *
 * application-prod.properties already removes the fallback, so a correctly
 * configured "prod" deployment fails at property-resolution time if the
 * environment variables are missing. This guard is a second, independent
 * check that does not depend on that file staying correct: it refuses to
 * finish starting under any profile other than the trusted "local" ones if
 * either secret still equals the known committed default.
 */
@Component
public class StartupSafetyGuard {

    private static final Logger log = LoggerFactory.getLogger(StartupSafetyGuard.class);

    /** Profiles where the committed defaults are expected and acceptable. */
    private static final List<String> LOCAL_PROFILES = List.of("default", "local", "dev", "test");

    private static final String DEFAULT_JWT_SECRET =
            "dev-only-fitclash-secret-please-rotate-me-0123456789";
    private static final String DEFAULT_DB_PASSWORD = "postgres";

    private final Environment environment;
    private final String jwtSecret;
    private final String dbPassword;

    public StartupSafetyGuard(Environment environment,
                              @Value("${fitclash.jwt.secret}") String jwtSecret,
                              @Value("${spring.datasource.password}") String dbPassword) {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
        this.dbPassword = dbPassword;
    }

    @PostConstruct
    public void verify() {
        String[] active = environment.getActiveProfiles();
        boolean isLocal = active.length == 0
                || Arrays.stream(active).anyMatch(LOCAL_PROFILES::contains);

        if (isLocal) {
            return;
        }

        if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "Refusing to start under profile(s) " + Arrays.toString(active) + ": "
                    + "fitclash.jwt.secret is still the committed dev default. "
                    + "Set the JWT_SECRET environment variable to a private, random value "
                    + "(32+ bytes) before deploying outside localhost.");
        }
        if (DEFAULT_DB_PASSWORD.equals(dbPassword)) {
            throw new IllegalStateException(
                    "Refusing to start under profile(s) " + Arrays.toString(active) + ": "
                    + "spring.datasource.password is still the committed dev default ('postgres'). "
                    + "Set the DB_PASSWORD environment variable before deploying outside localhost.");
        }
        log.info("StartupSafetyGuard: non-local profile {} verified, no default secrets in use.",
                Arrays.toString(active));
    }
}
