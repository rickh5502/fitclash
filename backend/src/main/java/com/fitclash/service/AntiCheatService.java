// File: src/main/java/com/fitclash/service/AntiCheatService.java
package com.fitclash.service;

import com.fitclash.config.GameProperties;
import com.fitclash.domain.WorkoutLog;
import com.fitclash.web.dto.Dtos.SetEntry;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 2 - Integrity & Anti-Cheat.
 *
 * Two tiers, deliberately:
 *
 *   REJECT - the entry is outside human possibility (1,000 reps of 500 kg,
 *            a 400 kg bench, a workout logged next Tuesday). Nothing is
 *            persisted and no XP moves. 422 with a per-set reason.
 *
 *   FLAG   - the entry is possible but extraordinary (>=75% of a lift ceiling,
 *            a 25%+ jump over the athlete's own best). It is logged, it pays
 *            XP, and it is marked unverified: flagged sessions are excluded
 *            from duel scoring and from the leaderboard's tie-breakers.
 *
 * The reason for the split is that a hard wall at "elite" punishes the strongest
 * honest users, and a soft flag costs a cheater the only thing they wanted.
 */
@Service
public class AntiCheatService {

    /**
     * Per-lift ceilings in kg, matched by slug containment so "incline-bench-press"
     * inherits the bench ceiling. Set near world-record territory: above these,
     * the entry needs human verification, not an API call.
     */
    private static final Map<String, Double> LIFT_CEILINGS_KG = new LinkedHashMap<>();

    static {
        LIFT_CEILINGS_KG.put("leg-press", 800.0);
        LIFT_CEILINGS_KG.put("hip-thrust", 600.0);
        LIFT_CEILINGS_KG.put("deadlift", 550.0);
        LIFT_CEILINGS_KG.put("squat", 500.0);
        LIFT_CEILINGS_KG.put("bench-press", 350.0);
        LIFT_CEILINGS_KG.put("bench", 350.0);
        LIFT_CEILINGS_KG.put("row", 300.0);
        LIFT_CEILINGS_KG.put("overhead-press", 250.0);
        LIFT_CEILINGS_KG.put("shoulder-press", 250.0);
        LIFT_CEILINGS_KG.put("pulldown", 200.0);
        LIFT_CEILINGS_KG.put("curl", 120.0);
        LIFT_CEILINGS_KG.put("lateral-raise", 60.0);
    }

    private final GameProperties rules;

    public AntiCheatService(GameProperties rules) {
        this.rules = rules;
    }

    /** Per-set outcome. {@code flagReason} is null when the set is clean. */
    public record Verdict(List<String> rejections, Map<Integer, String> flagsBySetIndex) {

        public boolean rejected() {
            return !rejections.isEmpty();
        }
    }

    /**
     * @param workoutDate       the date claimed for the session
     * @param sets              the sets as submitted, in order
     * @param workoutsToday     sessions this user already logged on that date
     * @param setsAlreadyToday  slug -> sets of that exercise already logged that date
     * @param bestE1rmKg        the athlete's own best estimated 1RM so far (0 for a new user)
     */
    public Verdict inspect(LocalDate workoutDate,
                           List<SetEntry> sets,
                           int workoutsToday,
                           Map<String, Integer> setsAlreadyToday,
                           double bestE1rmKg) {

        List<String> rejections = new ArrayList<>();
        Map<Integer, String> flags = new HashMap<>();
        LocalDate today = LocalDate.now();

        // ---- session-level checks -------------------------------------------------
        if (workoutDate.isAfter(today)) {
            rejections.add("Workout date is in the future. Log it when you have done it.");
        }
        if (workoutDate.isBefore(today.minusDays(rules.getMaxBackdateDays()))) {
            rejections.add("Workouts can only be backdated " + rules.getMaxBackdateDays() + " days.");
        }
        if (workoutsToday >= rules.getMaxWorkoutsPerDay()) {
            rejections.add("You have already logged " + workoutsToday
                    + " sessions on " + workoutDate + ". The daily limit is " + rules.getMaxWorkoutsPerDay() + ".");
        }
        if (sets.size() > rules.getMaxSetsPerSession()) {
            rejections.add("A single session cannot contain more than "
                    + rules.getMaxSetsPerSession() + " sets (you sent " + sets.size() + ").");
        }

        // ---- set-level checks -----------------------------------------------------
        Map<String, Integer> running = new HashMap<>(setsAlreadyToday);

        for (int i = 0; i < sets.size(); i++) {
            SetEntry set = sets.get(i);
            String label = "Set " + (i + 1) + " (" + set.exerciseName().trim() + ")";
            String slug = Formulas.slug(set.exerciseName());
            int dailyIndex = running.merge(slug, 1, Integer::sum);

            if (dailyIndex > rules.getMaxSetsPerExercisePerDay()) {
                rejections.add(label + ": that is set " + dailyIndex + " of this exercise today; the limit is "
                        + rules.getMaxSetsPerExercisePerDay() + ".");
            }

            switch (set.kind()) {
                case STRENGTH -> {
                    if (set.reps() <= 0) {
                        rejections.add(label + ": a strength set needs at least one rep.");
                    }
                    if (set.weightKg() <= 0) {
                        rejections.add(label + ": a strength set needs a load. Log unloaded work as BODYWEIGHT.");
                    }
                }
                case BODYWEIGHT -> {
                    if (set.reps() <= 0) {
                        rejections.add(label + ": a bodyweight set needs at least one rep.");
                    }
                    // Unloaded reps carry a 35 kg nominal load, so a fabricated
                    // 140-rep set is worth real XP. Above 50 reps we want a clock on it.
                    if (set.reps() > 50 && set.durationSec() <= 0) {
                        rejections.add(label + ": sets over 50 reps need a duration, so the pace can be checked.");
                    }
                }
                case CARDIO -> {
                    if (set.durationSec() <= 0) {
                        rejections.add(label + ": a cardio entry needs a duration.");
                    }
                    if (set.durationSec() > rules.getMaxCardioSeconds()) {
                        rejections.add(label + ": " + (set.durationSec() / 60) + " minutes exceeds the "
                                + (rules.getMaxCardioSeconds() / 60) + " minute limit for one entry.");
                    }
                }
            }

            if (set.reps() > rules.getMaxRepsPerSet()) {
                rejections.add(label + ": " + set.reps() + " reps in one set is not a set, it is a typo (max "
                        + rules.getMaxRepsPerSet() + ").");
            }
            if (set.weightKg() < 0) {
                // A negative load produces negative volume, which slips past every
                // "greater than" ceiling and quietly subtracts XP.
                rejections.add(label + ": weight cannot be negative.");
            }
            if (set.weightKg() > rules.getMaxWeightKg()) {
                rejections.add(label + ": " + fmt(set.weightKg()) + " kg is above the "
                        + fmt(rules.getMaxWeightKg()) + " kg absolute ceiling.");
            }
            if (set.durationSec() > 0 && set.reps() > 0) {
                double repsPerMinute = set.reps() / (set.durationSec() / 60d);
                if (repsPerMinute > 60) {
                    rejections.add(label + ": " + Math.round(repsPerMinute)
                            + " reps per minute is faster than a human moves.");
                }
            }

            double volume = Formulas.volumeKg(set.kind(), set.weightKg(), set.reps());
            if (volume > rules.getMaxSetVolumeKg()) {
                rejections.add(label + ": " + fmt(volume) + " kg of volume in one set exceeds the "
                        + fmt(rules.getMaxSetVolumeKg()) + " kg cap.");
            }

            Double ceiling = ceilingFor(slug);
            if (ceiling != null && set.weightKg() > ceiling) {
                rejections.add(label + ": " + fmt(set.weightKg()) + " kg is past the "
                        + fmt(ceiling) + " kg verified limit for this lift.");
            }

            // ---- soft flags -------------------------------------------------------
            if (ceiling != null && set.weightKg() >= ceiling * rules.getReviewFlagRatio()) {
                flags.put(i, "Near-elite load, unverified - excluded from duel scoring.");
            } else if (volume >= rules.getMaxSetVolumeKg() * rules.getReviewFlagRatio()) {
                flags.put(i, "Unusually high single-set volume - excluded from duel scoring.");
            } else if (set.kind() == WorkoutLog.Kind.STRENGTH && bestE1rmKg > 0) {
                double e1rm = Formulas.epleyE1rm(set.weightKg(), set.reps());
                if (e1rm > bestE1rmKg * 1.25d) {
                    flags.put(i, "More than 25% above your own best - excluded from duel scoring.");
                }
            }
        }

        return new Verdict(rejections, flags);
    }

    private Double ceilingFor(String slug) {
        for (Map.Entry<String, Double> entry : LIFT_CEILINGS_KG.entrySet()) {
            if (slug.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String fmt(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }
}
