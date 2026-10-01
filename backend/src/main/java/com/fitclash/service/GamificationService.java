// File: src/main/java/com/fitclash/service/GamificationService.java
package com.fitclash.service;

import com.fitclash.config.GameProperties;
import com.fitclash.domain.Character;
import com.fitclash.domain.WorkoutLog;
import com.fitclash.web.dto.Dtos.CharacterView;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Agent 3 - RPG mechanics. Owns levelling, stat derivation and streaks.
 * All of the arithmetic lives in {@link Formulas}; this class is where it
 * meets a persistent character.
 */
@Service
public class GamificationService {

    private final GameProperties rules;

    public GamificationService(GameProperties rules) {
        this.rules = rules;
    }

    public record LevelUp(int levelsGained, int newLevel) {

        public boolean happened() {
            return levelsGained > 0;
        }
    }

    /**
     * Banks XP and rolls the level as many times as the grant covers - a single
     * monster session can take a level-3 character to 5 in one response.
     */
    public LevelUp awardXp(Character character, long xp) {
        if (xp <= 0) {
            return new LevelUp(0, character.getLevel());
        }

        character.setLifetimeXp(character.getLifetimeXp() + xp);

        long pool = character.getCurrentXp() + xp;
        int level = character.getLevel();
        int gained = 0;

        long needed = Formulas.xpForNextLevel(level);
        while (pool >= needed && level < Formulas.MAX_LEVEL) {
            pool -= needed;
            level++;
            gained++;
            needed = Formulas.xpForNextLevel(level);
        }

        character.setCurrentXp(pool);
        character.setLevel(level);
        return new LevelUp(gained, level);
    }

    /** Credits the raw accumulators that STR and STA are derived from. */
    public void addTrainingPoints(Character character, double strPointsDelta, double staPointsDelta) {
        character.setStrPoints(character.getStrPoints() + Math.max(0d, strPointsDelta));
        character.setStaPoints(character.getStaPoints() + Math.max(0d, staPointsDelta));
    }

    /**
     * A new estimated 1RM above the athlete's own best is worth bonus STR points:
     * half a point per kilo of new best. Strength progress, not tonnage, is what
     * STR is supposed to measure.
     */
    public double registerE1rm(Character character, double e1rmKg) {
        double previousBest = character.getBestE1rmKg();
        if (e1rmKg <= previousBest) {
            return 0d;
        }
        character.setBestE1rmKg(e1rmKg);
        double bonus = previousBest > 0 ? (e1rmKg - previousBest) * 0.5d : 0d;
        character.setStrPoints(character.getStrPoints() + bonus);
        return bonus;
    }

    /**
     * Streak rule: consecutive calendar days only. Logging twice in a day does
     * not advance it, and a backdated session never rewrites history.
     *
     * Sessions older than the grace window still bank XP but earn no consistency
     * credit - otherwise a new account backfills 14 one-set days and buys CON 20
     * in a minute, which is exactly the behaviour CON exists to not reward.
     */
    public void registerTrainingDay(Character character, LocalDate date) {
        if (date.isBefore(LocalDate.now().minusDays(rules.getStreakCreditMaxAgeDays()))) {
            return;
        }
        LocalDate last = character.getLastWorkoutDate();
        if (last != null && !date.isAfter(last)) {
            return;
        }
        boolean consecutive = last != null && last.plusDays(1).equals(date);
        character.setCurrentStreak(consecutive ? character.getCurrentStreak() + 1 : 1);
        character.setLastWorkoutDate(date);
    }

    /** Recomputes the three stats from the accumulators plus the rolling window. */
    public void recomputeStats(Character character, int activeDaysLast30) {
        character.setStr(Formulas.strengthStat(character.getStrPoints()));
        character.setSta(Formulas.staminaStat(character.getStaPoints()));
        character.setCon(Formulas.consistencyStat(activeDaysLast30, character.getCurrentStreak()));
    }

    public double multiplierFor(int setIndexToday) {
        return Formulas.diminishingMultiplier(setIndexToday,
                rules.getFreeSetsPerExercise(),
                rules.getDiminishingFactor(),
                rules.getMinMultiplier());
    }

    /** The session-wide curve that runs on top of the per-exercise one. */
    public double dailyMultiplierFor(int setIndexTodayAllExercises) {
        return Formulas.dailyVolumeMultiplier(setIndexTodayAllExercises,
                rules.getFreeSetsPerDay(),
                rules.getDailyDecayFactor(),
                rules.getDailyMinMultiplier());
    }

    public double rawXpFor(WorkoutLog.Kind kind, double volumeKg, int durationSec, int intensity) {
        return Formulas.rawSetXp(kind, volumeKg, durationSec, intensity,
                rules.getStrengthXpDivisor(), rules.getCardioXpPerMinute());
    }

    public long dailyCap() {
        return rules.getDailyXpCap();
    }

    public CharacterView toView(Character c, long dailyXpEarned) {
        long needed = Formulas.xpForNextLevel(c.getLevel());
        double progress = needed > 0 ? Math.min(1d, (double) c.getCurrentXp() / needed) : 0d;

        return new CharacterView(
                c.getId(),
                c.getUser().getId(),
                c.getUser().getUsername(),
                c.getUser().getDisplayName(),
                c.getArchetype(),
                c.getLevel(),
                c.getCurrentXp(),
                needed,
                Formulas.round2(progress),
                c.getLifetimeXp(),
                c.getStr(),
                c.getSta(),
                c.getCon(),
                c.getCurrentStreak(),
                c.getLongestStreak(),
                c.getLastWorkoutDate(),
                Formulas.round2(c.getBestE1rmKg()),
                c.getDuelsWon(),
                c.getDuelsLost(),
                c.getDuelsDrawn(),
                dailyXpEarned,
                Math.max(0L, rules.getDailyXpCap() - dailyXpEarned));
    }
}
