// File: src/main/java/com/fitclash/web/dto/Dtos.java
package com.fitclash.web.dto;

import com.fitclash.domain.Duel;
import com.fitclash.domain.WorkoutLog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Every request/response shape in one place. Nested records keep the wire
 * contract readable in a single file without 30 near-empty classes.
 *
 * Bean Validation here is the coarse filter (is this even a number?).
 * AntiCheatService is the fine filter (is this humanly possible?).
 */
public final class Dtos {

    private Dtos() {
    }

    // ---------------------------------------------------------------- auth

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$",
                    message = "Username must be 3-32 letters, digits or underscores")
            String username,

            @NotBlank @Email @Size(max = 255) String email,

            @NotBlank @Size(min = 8, max = 72,
                    message = "Password must be 8-72 characters")
            String password,

            @Size(max = 64) String displayName) {
    }

    public record LoginRequest(
            @NotBlank String usernameOrEmail,
            @NotBlank String password) {
    }

    public record AuthResponse(
            String token,
            String tokenType,
            long expiresInSeconds,
            UserSummary user,
            CharacterView character) {
    }

    public record UserSummary(UUID id, String username, String displayName) {
    }

    // ----------------------------------------------------------- character

    public record CharacterView(
            UUID id,
            UUID userId,
            String username,
            String displayName,
            String archetype,
            int level,
            long currentXp,
            long xpForNextLevel,
            double levelProgress,
            long lifetimeXp,
            int str,
            int sta,
            int con,
            int currentStreak,
            int longestStreak,
            LocalDate lastWorkoutDate,
            double bestE1rmKg,
            int duelsWon,
            int duelsLost,
            int duelsDrawn,
            long dailyXpEarned,
            long dailyXpRemaining) {
    }

    public record StatDelta(int str, int sta, int con) {
    }

    // ------------------------------------------------------------ workouts

    public record SetEntry(
            @NotBlank @Size(max = 80) String exerciseName,
            @NotNull WorkoutLog.Kind kind,
            @Min(0) @Max(1000) int reps,
            @DecimalMin("0.0") @DecimalMax("2000.0") double weightKg,
            @Min(0) @Max(86400) int durationSec,
            @Min(1) @Max(10) int intensity) {

        public SetEntry {
            if (intensity == 0) {
                intensity = 5;
            }
        }
    }

    public record LogWorkoutRequest(
            LocalDate workoutDate,
            @Size(max = 80) String title,
            @Size(max = 500) String notes,
            @NotEmpty(message = "Log at least one set") @Valid List<SetEntry> sets) {
    }

    public record SetResult(
            String exerciseName,
            int setIndex,
            double volumeKg,
            double xpRaw,
            double multiplier,
            double xpAwarded,
            boolean personalRecord,
            boolean flagged,
            String flagReason) {
    }

    public record LogWorkoutResponse(
            UUID workoutId,
            LocalDate workoutDate,
            long xpRaw,
            long xpAwarded,
            boolean dailyCapReached,
            long dailyXpEarned,
            long dailyXpRemaining,
            List<SetResult> sets,
            List<String> notices,
            boolean leveledUp,
            int levelsGained,
            int newLevel,
            StatDelta statDelta,
            CharacterView character) {
    }

    public record WorkoutSummary(
            UUID id,
            LocalDate workoutDate,
            Instant loggedAt,
            String title,
            int totalSets,
            double totalVolumeKg,
            long xpAwarded,
            boolean flaggedForReview) {
    }

    // --------------------------------------------------------------- duels

    public record CreateDuelRequest(
            @NotNull UUID opponentId,
            @NotNull Duel.Metric metric,
            @Min(0) @Max(250) int stakeXp,
            @Min(1) @Max(30) int durationDays) {
    }

    public record DuelSide(
            UUID userId,
            String username,
            String displayName,
            int level,
            double score) {
    }

    public record DuelView(
            UUID id,
            Duel.Metric metric,
            Duel.Status status,
            int stakeXp,
            int durationDays,
            boolean rated,
            Instant createdAt,
            Instant startsAt,
            Instant endsAt,
            Instant resolvedAt,
            DuelSide challenger,
            DuelSide opponent,
            UUID winnerId,
            String resolutionNote,
            boolean youAreChallenger,
            String outcome) {
    }

    // -------------------------------------------------------------- social

    public record LeaderboardRow(
            int rank,
            UUID userId,
            String username,
            String displayName,
            String archetype,
            int level,
            long lifetimeXp,
            int str,
            int sta,
            int con,
            int currentStreak,
            int duelsWon) {
    }

    public record FriendRequestBody(@NotBlank String username) {
    }

    public record FriendView(
            UUID friendshipId,
            UUID userId,
            String username,
            String displayName,
            String status,
            boolean incoming,
            Integer level) {
    }

    // -------------------------------------------------------------- errors

    public record ApiError(String error, String message, List<String> details) {
    }
}
