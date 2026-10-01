// File: src/main/java/com/fitclash/web/HealthController.java
package com.fitclash.web;

import com.fitclash.config.GameProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Open endpoint. The React dev server hits this on boot: if CORS or the port
 * is wrong, the failure shows up here instead of inside a login attempt.
 */
@RestController
public class HealthController {

    private final GameProperties rules;

    public HealthController(GameProperties rules) {
        this.rules = rules;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "up",
                "service", "fitclash-api",
                "time", Instant.now().toString(),
                "rules", Map.of(
                        "dailyXpCap", rules.getDailyXpCap(),
                        "freeSetsPerExercise", rules.getFreeSetsPerExercise(),
                        "diminishingFactor", rules.getDiminishingFactor(),
                        "maxSetVolumeKg", rules.getMaxSetVolumeKg()));
    }
}
