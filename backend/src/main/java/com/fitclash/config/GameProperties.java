// File: src/main/java/com/fitclash/config/GameProperties.java
package com.fitclash.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every tunable number the game balance depends on (Agents 2 and 3).
 * Defaults here are the shipping values; application.properties restates them
 * so an ops engineer can retune balance without touching code.
 */
@ConfigurationProperties(prefix = "fitclash.rules")
public class GameProperties {

    // --- XP economy (Agent 3) ---
    private long dailyXpCap = 1000L;
    private int freeSetsPerExercise = 5;
    private double diminishingFactor = 0.5;
    private double minMultiplier = 0.03125;
    private double strengthXpDivisor = 60.0;
    private double cardioXpPerMinute = 3.0;

    // Session-wide anti-splitting curve: stops the per-exercise rule being
    // dodged by spreading one movement across invented exercise names.
    private int freeSetsPerDay = 20;
    private double dailyDecayFactor = 0.8;
    private double dailyMinMultiplier = 0.1;

    /** Cardio is self-reported and unverifiable, so it cannot fill the day alone. */
    private long dailyCardioXpCap = 400L;

    /** Backdated sessions still bank XP, but only recent ones build a streak. */
    private int streakCreditMaxAgeDays = 1;

    // --- Anti-cheat sanity thresholds (Agent 2) ---
    private double maxSetVolumeKg = 5000.0;
    private double maxWeightKg = 500.0;
    private int maxRepsPerSet = 150;
    private int maxSetsPerExercisePerDay = 25;
    private int maxSetsPerSession = 120;
    private int maxWorkoutsPerDay = 6;
    private int maxCardioSeconds = 14400;
    private int maxBackdateDays = 14;
    private double reviewFlagRatio = 0.75;

    // --- Duels (Agent 3) ---
    private int maxRatedDuelsPerWeek = 3;
    private int duelBaseRewardXp = 150;
    private int duelMaxRewardXp = 300;
    private int duelConsolationXp = 25;

    public long getDailyXpCap() { return dailyXpCap; }
    public void setDailyXpCap(long v) { this.dailyXpCap = v; }

    public int getFreeSetsPerExercise() { return freeSetsPerExercise; }
    public void setFreeSetsPerExercise(int v) { this.freeSetsPerExercise = v; }

    public double getDiminishingFactor() { return diminishingFactor; }
    public void setDiminishingFactor(double v) { this.diminishingFactor = v; }

    public double getMinMultiplier() { return minMultiplier; }
    public void setMinMultiplier(double v) { this.minMultiplier = v; }

    public double getStrengthXpDivisor() { return strengthXpDivisor; }
    public void setStrengthXpDivisor(double v) { this.strengthXpDivisor = v; }

    public double getCardioXpPerMinute() { return cardioXpPerMinute; }
    public void setCardioXpPerMinute(double v) { this.cardioXpPerMinute = v; }

    public int getFreeSetsPerDay() { return freeSetsPerDay; }
    public void setFreeSetsPerDay(int v) { this.freeSetsPerDay = v; }

    public double getDailyDecayFactor() { return dailyDecayFactor; }
    public void setDailyDecayFactor(double v) { this.dailyDecayFactor = v; }

    public double getDailyMinMultiplier() { return dailyMinMultiplier; }
    public void setDailyMinMultiplier(double v) { this.dailyMinMultiplier = v; }

    public long getDailyCardioXpCap() { return dailyCardioXpCap; }
    public void setDailyCardioXpCap(long v) { this.dailyCardioXpCap = v; }

    public int getStreakCreditMaxAgeDays() { return streakCreditMaxAgeDays; }
    public void setStreakCreditMaxAgeDays(int v) { this.streakCreditMaxAgeDays = v; }

    public double getMaxSetVolumeKg() { return maxSetVolumeKg; }
    public void setMaxSetVolumeKg(double v) { this.maxSetVolumeKg = v; }

    public double getMaxWeightKg() { return maxWeightKg; }
    public void setMaxWeightKg(double v) { this.maxWeightKg = v; }

    public int getMaxRepsPerSet() { return maxRepsPerSet; }
    public void setMaxRepsPerSet(int v) { this.maxRepsPerSet = v; }

    public int getMaxSetsPerExercisePerDay() { return maxSetsPerExercisePerDay; }
    public void setMaxSetsPerExercisePerDay(int v) { this.maxSetsPerExercisePerDay = v; }

    public int getMaxSetsPerSession() { return maxSetsPerSession; }
    public void setMaxSetsPerSession(int v) { this.maxSetsPerSession = v; }

    public int getMaxWorkoutsPerDay() { return maxWorkoutsPerDay; }
    public void setMaxWorkoutsPerDay(int v) { this.maxWorkoutsPerDay = v; }

    public int getMaxCardioSeconds() { return maxCardioSeconds; }
    public void setMaxCardioSeconds(int v) { this.maxCardioSeconds = v; }

    public int getMaxBackdateDays() { return maxBackdateDays; }
    public void setMaxBackdateDays(int v) { this.maxBackdateDays = v; }

    public double getReviewFlagRatio() { return reviewFlagRatio; }
    public void setReviewFlagRatio(double v) { this.reviewFlagRatio = v; }

    public int getMaxRatedDuelsPerWeek() { return maxRatedDuelsPerWeek; }
    public void setMaxRatedDuelsPerWeek(int v) { this.maxRatedDuelsPerWeek = v; }

    public int getDuelBaseRewardXp() { return duelBaseRewardXp; }
    public void setDuelBaseRewardXp(int v) { this.duelBaseRewardXp = v; }

    public int getDuelMaxRewardXp() { return duelMaxRewardXp; }
    public void setDuelMaxRewardXp(int v) { this.duelMaxRewardXp = v; }

    public int getDuelConsolationXp() { return duelConsolationXp; }
    public void setDuelConsolationXp(int v) { this.duelConsolationXp = v; }
}
