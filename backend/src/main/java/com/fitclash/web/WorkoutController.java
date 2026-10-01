// File: src/main/java/com/fitclash/web/WorkoutController.java
package com.fitclash.web;

import com.fitclash.security.AuthPrincipal;
import com.fitclash.service.WorkoutService;
import com.fitclash.web.dto.Dtos.LogWorkoutRequest;
import com.fitclash.web.dto.Dtos.LogWorkoutResponse;
import com.fitclash.web.dto.Dtos.WorkoutSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workouts")
public class WorkoutController {

    private final WorkoutService workoutService;

    public WorkoutController(WorkoutService workoutService) {
        this.workoutService = workoutService;
    }

    /**
     * Logs a session and returns everything the UI needs to animate the result:
     * per-set XP with its multiplier, the cap state, the stat deltas and the
     * new character sheet. One round trip, no refetch.
     */
    @PostMapping
    public ResponseEntity<LogWorkoutResponse> log(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @Valid @RequestBody LogWorkoutRequest request) {
        LogWorkoutResponse response = workoutService.logWorkout(principal.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("X-FitClash-Daily-Xp-Remaining", String.valueOf(response.dailyXpRemaining()))
                .body(response);
    }

    @GetMapping
    public List<WorkoutSummary> recent(@AuthenticationPrincipal AuthPrincipal principal) {
        return workoutService.recentWorkouts(principal.userId());
    }

    @GetMapping("/daily-budget")
    public Map<String, Long> dailyBudget(@AuthenticationPrincipal AuthPrincipal principal) {
        return Map.of("remainingXp", workoutService.remainingDailyXp(principal.userId()));
    }
}
