// File: src/main/java/com/fitclash/service/AntiCheatException.java
package com.fitclash.service;

import java.util.List;

/**
 * The entry was rejected outright: nothing is persisted, no XP moves.
 * Carries every violation so the UI can show the user exactly which set
 * to fix instead of a generic "invalid workout".
 */
public class AntiCheatException extends RuntimeException {

    private final List<String> violations;

    public AntiCheatException(List<String> violations) {
        super("Workout rejected by integrity check");
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
