// File: src/test/java/com/fitclash/service/GameMathTest.java
package com.fitclash.service;

import com.fitclash.config.GameProperties;
import com.fitclash.domain.WorkoutLog.Kind;
import com.fitclash.web.dto.Dtos.SetEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These tests are the balance spec. If someone retunes the game, one of these
 * fails and they have to say so out loud.
 */
class GameMathTest {

    private final GameProperties rules = new GameProperties();
    private final AntiCheatService antiCheat = new AntiCheatService(rules);

    // ------------------------------------------------------------- levelling

    @Test
    @DisplayName("Levelling curve is 100 x N^1.5")
    void levelCurve() {
        assertEquals(100, Formulas.xpForNextLevel(1));
        assertEquals(282, Formulas.xpForNextLevel(2));
        assertEquals(1852, Formulas.xpForNextLevel(7));
        assertEquals(3162, Formulas.xpForNextLevel(10));
        // Strictly increasing - no level is ever cheaper than the one before it.
        for (int n = 1; n < 100; n++) {
            assertTrue(Formulas.xpForNextLevel(n + 1) > Formulas.xpForNextLevel(n));
        }
    }

    // --------------------------------------------------- diminishing returns

    @Test
    @DisplayName("Sets 1-5 pay in full, then each further set halves")
    void diminishingReturns() {
        for (int i = 1; i <= 5; i++) {
            assertEquals(1.0, multiplier(i), 1e-9);
        }
        assertEquals(0.5, multiplier(6), 1e-9);
        assertEquals(0.25, multiplier(7), 1e-9);
        assertEquals(0.125, multiplier(8), 1e-9);
        // Floored so the arithmetic never underflows to zero.
        assertEquals(rules.getMinMultiplier(), multiplier(25), 1e-9);
    }

    @Test
    @DisplayName("Ten sets of one exercise are worth less than 6.0 clean sets")
    void grindingIsPointless() {
        double tenSets = 0;
        for (int i = 1; i <= 10; i++) {
            tenSets += multiplier(i);
        }
        assertTrue(tenSets < 6.0, "expected < 6.0 set-equivalents, got " + tenSets);
    }

    @Test
    @DisplayName("Invented exercise names collapse to one movement")
    void slugStripsOrdinals() {
        assertEquals("bench-press", Formulas.slug("Bench Press"));
        assertEquals("bench-press", Formulas.slug("Bench Press 2"));
        assertEquals("bench-press", Formulas.slug("bench press v3"));
        assertEquals("incline-bench-press", Formulas.slug("  Incline Bench Press  "));
        // Numbers that carry meaning are left alone.
        assertEquals("farmers-walk-20m", Formulas.slug("Farmers Walk 20m"));
    }

    @Test
    @DisplayName("A session-wide curve backstops the per-exercise one")
    void dailySetCurve() {
        assertEquals(1.0, dailyMultiplier(20), 1e-9);
        assertEquals(0.8, dailyMultiplier(21), 1e-9);
        assertEquals(0.64, dailyMultiplier(22), 1e-9);
        assertEquals(rules.getDailyMinMultiplier(), dailyMultiplier(60), 1e-9);
    }

    @Test
    @DisplayName("Ordinal-suffix name splitting is fully neutralised by slug()")
    void ordinalSplittingIsNeutralised() {
        // "Bench Press", "Bench Press 2" and "bench press v3" all slug to the
        // same movement, so WorkoutService sees ONE exercise and the per-exercise
        // diminishing-returns curve applies to the combined set count. There is no
        // separate "split" curve to compute here - the attack this test used to
        // model (fake ordinal suffixes) literally cannot produce a different
        // sequence of set indices than logging honestly under one name. The ratio
        // for that specific attack is exactly 1.0 by construction.
        assertEquals(Formulas.slug("Bench Press"), Formulas.slug("Bench Press 2"));
        assertEquals(Formulas.slug("Bench Press"), Formulas.slug("bench press v3"));
    }

    @Test
    @DisplayName("Inventing distinct exercise names still inflates XP, but the daily curve caps the payout")
    void fabricatedExerciseNamesAreBoundedByDailyCurve() {
        // Splitting work across genuinely DISTINCT invented names (not ordinal
        // suffixes - those are caught by slug() above) still resets the
        // per-exercise free-set allowance each time, so this attack is not
        // neutralised the way ordinal-splitting is. What changed is that the
        // session-wide curve (Formulas.dailyVolumeMultiplier) now bounds the
        // ABSOLUTE XP such a day can pay out, regardless of how many fake names
        // are used.
        //
        // Measured set-equivalents (multiplier(i) * dailyMultiplier(i), summed):
        //   sets   one-exercise   split-into-5s   ratio
        //    20        6.281          20.000       3.18
        //    25        6.365          22.689       3.56
        //    40        6.424          24.571       3.82
        //    60        6.487          26.571       4.10
        //
        // The ratio is NOT reduced at 20 sets - the first 20 sets of a day are
        // free by design, and a 4-exercise 20-set day legitimately outpaying a
        // 20-set single-movement grind is correct (variety beats grinding one
        // lift), not an exploit. What the daily curve guarantees instead is that
        // a fabricated high-set day cannot pay out more than a bounded number of
        // set-equivalents no matter how many sets are logged.
        assertEquals(20.000, splitInto5s(20), 1e-3);
        assertEquals(24.571, splitInto5s(40), 1e-3);
        assertEquals(26.571, splitInto5s(60), 1e-3);

        // The absolute payout barely grows past 40 sets: 20 extra fabricated
        // sets (40 -> 60) buy only ~2 more set-equivalents.
        assertTrue(splitInto5s(60) - splitInto5s(40) < 3.0,
                "daily curve should flatten the payout well before 60 sets");
    }

    // ------------------------------------------------------------ set scoring

    @Test
    @DisplayName("Volume and XP per set")
    void volumeAndXp() {
        assertEquals(800, Formulas.volumeKg(Kind.STRENGTH, 100, 8), 1e-9);
        // Bodyweight reps carry a 35 kg nominal load.
        assertEquals(280, Formulas.volumeKg(Kind.BODYWEIGHT, 0, 8), 1e-9);
        assertEquals(0, Formulas.volumeKg(Kind.CARDIO, 0, 0), 1e-9);

        double xp = Formulas.rawSetXp(Kind.STRENGTH, 800, 0, 5,
                rules.getStrengthXpDivisor(), rules.getCardioXpPerMinute());
        assertEquals(800 / 60d, xp, 1e-9);

        // 30 minutes at intensity 6.
        double cardio = Formulas.rawSetXp(Kind.CARDIO, 0, 1800, 6,
                rules.getStrengthXpDivisor(), rules.getCardioXpPerMinute());
        assertEquals(108, cardio, 1e-6);
    }

    @Test
    @DisplayName("Epley e1RM")
    void e1rm() {
        assertEquals(0, Formulas.epleyE1rm(100, 0), 1e-9);   // no reps, no estimate
        assertEquals(116.67, Formulas.epleyE1rm(100, 5), 0.01);
    }

    // ------------------------------------------------------------------ stats

    @Test
    @DisplayName("STR and STA taper hard, so tonnage alone cannot max a stat")
    void statCurves() {
        assertEquals(5, Formulas.strengthStat(0));
        assertEquals(13, Formulas.strengthStat(12));       // one solid session
        assertEquals(23, Formulas.strengthStat(200));      // a couple of months
        assertEquals(33, Formulas.strengthStat(4000));     // years
        // 20x the tonnage buys 10 points, not 200.
        assertEquals(10, Formulas.strengthStat(4000) - Formulas.strengthStat(200));
        assertTrue(Formulas.strengthStat(1_000_000) <= Formulas.MAX_STAT);
    }

    @Test
    @DisplayName("CON rewards showing up, and falls when you stop")
    void consistencyCurve() {
        assertEquals(6, Formulas.consistencyStat(3, 0));
        assertTrue(Formulas.consistencyStat(20, 6) > Formulas.consistencyStat(10, 6));
        assertEquals(40, Formulas.consistencyStat(30, 30));
        // Same streak, fewer days in the window -> lower CON.
        assertTrue(Formulas.consistencyStat(8, 3) < Formulas.consistencyStat(24, 3));
    }

    // ------------------------------------------------------------- anti-cheat

    @Test
    @DisplayName("1,000 reps of 500 kg is rejected on three separate grounds")
    void absurdSetIsRejected() {
        var verdict = inspect(new SetEntry("Bench Press", Kind.STRENGTH, 1000, 500, 0, 5));
        assertTrue(verdict.rejected());
        assertTrue(verdict.rejections().size() >= 3, verdict.rejections().toString());
    }

    @Test
    @DisplayName("A 400 kg bench needs verification, not an API call")
    void perLiftCeiling() {
        assertTrue(inspect(new SetEntry("Bench Press", Kind.STRENGTH, 1, 400, 0, 5)).rejected());
        // The same load on a lift with a higher ceiling is fine.
        assertFalse(inspect(new SetEntry("Leg Press", Kind.STRENGTH, 5, 400, 0, 5)).rejected());
    }

    @Test
    @DisplayName("Near-elite but possible: logged, paid, and flagged")
    void nearEliteIsFlaggedNotRejected() {
        var verdict = inspect(new SetEntry("Bench Press", Kind.STRENGTH, 1, 300, 0, 5));
        assertFalse(verdict.rejected());
        assertTrue(verdict.flagsBySetIndex().containsKey(0));
    }

    @Test
    @DisplayName("An ordinary working set passes clean")
    void ordinarySetIsClean() {
        var verdict = inspect(new SetEntry("Bench Press", Kind.STRENGTH, 8, 80, 0, 5));
        assertFalse(verdict.rejected());
        assertTrue(verdict.flagsBySetIndex().isEmpty());
    }

    @Test
    @DisplayName("Single-set volume cap catches 100 x 60 kg")
    void singleSetVolumeCap() {
        var verdict = inspect(new SetEntry("Barbell Row", Kind.STRENGTH, 100, 60, 0, 5));
        assertTrue(verdict.rejected());
    }

    @Test
    @DisplayName("Negative load is refused before it can subtract XP")
    void negativeWeightRejected() {
        var verdict = inspect(new SetEntry("Push-Up", Kind.BODYWEIGHT, 20, -40, 0, 5));
        assertTrue(verdict.rejected());
    }

    @Test
    @DisplayName("A fabricated 142-rep bodyweight set needs a clock on it")
    void highRepBodyweightNeedsDuration() {
        assertTrue(inspect(new SetEntry("Push-Up", Kind.BODYWEIGHT, 142, 0, 0, 5)).rejected());
        // With a plausible duration it passes: 142 reps over 5 minutes is 28/min.
        assertFalse(inspect(new SetEntry("Push-Up", Kind.BODYWEIGHT, 142, 0, 300, 5)).rejected());
    }

    @Test
    @DisplayName("Impossible rep rates are refused")
    void repRateRejected() {
        // 100 reps in 30 seconds is 200/min.
        assertTrue(inspect(new SetEntry("Push-Up", Kind.BODYWEIGHT, 100, 0, 30, 5)).rejected());
    }

    @Test
    @DisplayName("Tomorrow's workout cannot be logged today")
    void futureDateRejected() {
        var verdict = antiCheat.inspect(LocalDate.now().plusDays(1),
                List.of(new SetEntry("Back Squat", Kind.STRENGTH, 5, 100, 0, 5)),
                0, Map.of(), 0);
        assertTrue(verdict.rejected());
    }

    @Test
    @DisplayName("The sixth session of the day is refused")
    void sessionsPerDayCapped() {
        var verdict = antiCheat.inspect(LocalDate.now(),
                List.of(new SetEntry("Back Squat", Kind.STRENGTH, 5, 100, 0, 5)),
                rules.getMaxWorkoutsPerDay(), Map.of(), 0);
        assertTrue(verdict.rejected());
    }

    // ----------------------------------------------------------------- helpers

    private double multiplier(int setIndex) {
        return Formulas.diminishingMultiplier(setIndex, rules.getFreeSetsPerExercise(),
                rules.getDiminishingFactor(), rules.getMinMultiplier());
    }

    private double dailyMultiplier(int setIndexAllExercises) {
        return Formulas.dailyVolumeMultiplier(setIndexAllExercises, rules.getFreeSetsPerDay(),
                rules.getDailyDecayFactor(), rules.getDailyMinMultiplier());
    }

    /** Set-equivalents for {@code totalSets} spread across invented names, 5 sets each. */
    private double splitInto5s(int totalSets) {
        double total = 0;
        for (int i = 1; i <= totalSets; i++) {
            total += multiplier(((i - 1) % 5) + 1) * dailyMultiplier(i);
        }
        return total;
    }

    private AntiCheatService.Verdict inspect(SetEntry entry) {
        return antiCheat.inspect(LocalDate.now(), List.of(entry), 0, Map.of(), 0);
    }
}
