// File: src/main/java/com/fitclash/service/Formulas.java
package com.fitclash.service;

import com.fitclash.domain.WorkoutLog;

import java.util.Locale;

/**
 * Every number the game is built on, as pure functions.
 *
 * Kept free of Spring so the balance can be unit-tested in isolation, and so
 * the React client can mirror these exact formulas for its optimistic preview
 * (see frontend/src/lib/formulas.js - the two must stay in step).
 *
 * ---------------------------------------------------------------------------
 * XP (Agent 3, bounded by Agent 2)
 *   volume(set)        = weight x reps            (bodyweight adds a 35 kg nominal load)
 *   rawXp(strength)    = volume / 60
 *   rawXp(cardio)      = minutes x xpPerMinute x (intensity / 5)
 *   multiplier(n)      = 1                for n <= 5 sets of that exercise today
 *                      = 0.5^(n-5)        beyond that, floored at 0.03125
 *   awarded            = min(SUM(rawXp x multiplier), 1000 - xpAlreadyEarnedToday)
 *
 * Levelling
 *   xpForNextLevel(N)  = floor(100 x N^1.5)       (XP to go from N to N+1)
 *
 * Stats
 *   STR = 5 + floor(8  x log10(1 + strPoints))    strPoints  += tonnes lifted
 *   STA = 5 + floor(8  x log10(1 + staPoints))    staPoints  += (min x intensity)/10 + high-rep bonus
 *   CON = 5 + floor(25 x (activeDays30/30)^1.2) + min(10, streak/3)
 *
 * STR and STA are logarithmic on purpose: a beginner's first month moves the
 * needle hard, a veteran's thousandth tonne barely does. That is what makes
 * grinding junk volume pointless, which is the whole point of Agent 2.
 * CON is a rolling window, so it decays if you stop showing up.
 * ---------------------------------------------------------------------------
 */
public final class Formulas {

    /** Nominal load credited to unloaded reps (pull-ups, push-ups, dips). */
    public static final double BODYWEIGHT_LOAD_KG = 35.0;

    public static final int MAX_STAT = 99;
    public static final int BASE_STAT = 5;
    public static final int MAX_LEVEL = 999;

    private Formulas() {
    }

    /**
     * "Incline Bench Press " -> "incline-bench-press"
     *
     * Trailing ordinals are stripped on purpose: "bench press", "bench press 2"
     * and "bench press v3" are one movement. Without this, a user splits one
     * exercise across invented names and never pays diminishing returns at all.
     */
    public static String slug(String exerciseName) {
        String cleaned = exerciseName.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "")
                .replaceAll("-v?\\d+$", "")
                .replaceAll("-$", "");
        return cleaned.isEmpty() ? "unnamed" : cleaned;
    }

    public static double volumeKg(WorkoutLog.Kind kind, double weightKg, int reps) {
        return switch (kind) {
            case STRENGTH -> weightKg * reps;
            case BODYWEIGHT -> (weightKg + BODYWEIGHT_LOAD_KG) * reps;
            case CARDIO -> 0d;
        };
    }

    /** Epley estimated one-rep max - the PR yardstick. */
    public static double epleyE1rm(double weightKg, int reps) {
        if (reps <= 0 || weightKg <= 0) {
            return 0d;
        }
        return weightKg * (1d + reps / 30d);
    }

    public static double rawSetXp(WorkoutLog.Kind kind,
                                  double volumeKg,
                                  int durationSec,
                                  int intensity,
                                  double strengthDivisor,
                                  double cardioXpPerMinute) {
        return switch (kind) {
            case STRENGTH, BODYWEIGHT -> volumeKg / strengthDivisor;
            case CARDIO -> (durationSec / 60d) * cardioXpPerMinute * (intensity / 5d);
        };
    }

    /**
     * Diminishing returns. setIndex is 1-based across the whole day for one
     * exercise, so sets 1-5 pay in full and every set after that halves again.
     * Set 6 pays 50%, set 7 pays 25%, set 12 hits the floor at ~3%.
     */
    public static double diminishingMultiplier(int setIndex, int freeSets, double factor, double floor) {
        if (setIndex <= freeSets) {
            return 1d;
        }
        double multiplier = Math.pow(factor, setIndex - freeSets);
        return Math.max(floor, multiplier);
    }

    /**
     * Session-wide diminishing returns, applied on top of the per-exercise curve.
     *
     * The per-exercise rule alone is trivially dodged by spreading the same work
     * across invented exercise names - measured at 3.2x inflation before this
     * existed. This second curve is deliberately gentler (0.8 per set past 20 in
     * a day, floored at 0.1) so a genuinely hard 25-set session barely notices it,
     * while 40 sets of invented movements collapses.
     */
    public static double dailyVolumeMultiplier(int totalSetsToday, int freeSetsPerDay,
                                               double factor, double floor) {
        if (totalSetsToday <= freeSetsPerDay) {
            return 1d;
        }
        return Math.max(floor, Math.pow(factor, totalSetsToday - freeSetsPerDay));
    }

    /** XP needed to go from {@code level} to {@code level + 1}. */
    public static long xpForNextLevel(int level) {
        return (long) Math.floor(100d * Math.pow(level, 1.5d));
    }

    /** Total XP a character has ever needed to reach {@code level} from 1. */
    public static long cumulativeXpToLevel(int level) {
        long total = 0;
        for (int n = 1; n < level; n++) {
            total += xpForNextLevel(n);
        }
        return total;
    }

    public static int strengthStat(double strPoints) {
        return clampStat(BASE_STAT + (int) Math.floor(8d * Math.log10(1d + Math.max(0d, strPoints))));
    }

    public static int staminaStat(double staPoints) {
        return clampStat(BASE_STAT + (int) Math.floor(8d * Math.log10(1d + Math.max(0d, staPoints))));
    }

    /**
     * Consistency is the only stat that can go down. It reads a rolling 30-day
     * window, so two heroic weeks followed by a month off is worth less than
     * showing up three times a week forever - which is the behaviour the app exists to produce.
     */
    public static int consistencyStat(int activeDaysLast30, int currentStreak) {
        double density = Math.min(1d, activeDaysLast30 / 30d);
        int fromDensity = (int) Math.floor(25d * Math.pow(density, 1.2d));
        int fromStreak = Math.min(10, currentStreak / 3);
        return clampStat(BASE_STAT + fromDensity + fromStreak);
    }

    private static int clampStat(int value) {
        return Math.max(1, Math.min(MAX_STAT, value));
    }

    public static double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
