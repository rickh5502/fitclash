// File: src/main/java/com/fitclash/security/AuthPrincipal.java
package com.fitclash.security;

import java.util.UUID;

/** What lands in the SecurityContext once a JWT is verified. */
public record AuthPrincipal(UUID userId, String username) {
}
