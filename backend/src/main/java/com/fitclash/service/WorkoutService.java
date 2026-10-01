// File: src/main/java/com/fitclash/service/WorkoutService.java
package com.fitclash.service;

import com.fitclash.config.GameProperties;
import com.fitclash.domain.Character;
import com.fitclash.domain.User;
import com.fitclash.domain.Workout;
import com.fitclash.domain.WorkoutLog;
import com.fitclash.domain.XpEvent;
import com.fitclash.repo.CharacterRepository;
import com.fitclash.repo.UserRepository;
import com.fitclash.repo.WorkoutLogRepository;
import com.fitclash.repo.WorkoutRepository;
import com.fitclash.repo.XpEventRepository;
import com.fitclash.web.dto.Dtos.CharacterView;
import com.fitclash.web.dto.Dtos.LogWorkoutRequest;
import com.fitclash.web.dto.Dtos.LogWorkoutResponse;
import com.fitclash.web.dto.Dtos.SetEntry;
import com.fitclash.web.dto.Dtos.SetResult;
import com.fitclash.web.dto.Dtos.StatDelta;
import com.fitclash.web.dto.Dtos.WorkoutSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Agent 6 - the heart of the backend.
 *
 * Order of operations when a session is logged, and why:
 *
 *   1. Integrity check first. A rejected session never touches the database,
 *      so there is no partial write to unwind.
 *   2. Score every set with its own diminishing-returns multiplier, using the
 *      count of that exercise ALREADY logged today plus its position in this
 *      request. Submitting 10 sets in one call and 10 calls of one set must
 *      produce identical XP - otherwise the client chooses its own reward.
 *   3. Apply the daily cap last, as a single proportional scale across the
 *      session, so no set is silently worth zero while its neighbour pays full.
 *   4. Persist, then derive stats, then bank XP. Stats read from the database
 *      (including this session), so the workout is flushed before they run.
 *
 * The whole method is one transaction: XP, stats, streak, ledger and logs
 * commit together or not at all.
 */
@Service
public class WorkoutService {

    private final UserRepository users;
    private final CharacterRepository characters;
    private final WorkoutRepository workouts;
    private final WorkoutLogRepository workoutLogs;
    private final XpEventRepository xpEvents;
    private final AntiCheatService antiCheat;
    private final GamificationService gamification;
    private final GameProperties rules;

    public WorkoutService(UserRepository users,
                          CharacterRepository characters,
                          WorkoutRepository workouts,
                          WorkoutLogRepository workoutLogs,
                          XpEventRepository xpEvents,
                          AntiCheatService antiCheat,
                          GamificationService gamification,
                          GameProperties rules) {
        this.users = users;
        this.characters = characters;
        this.workouts = workouts;
        this.workoutLogs = workoutLogs;
        this.xpEvents = xpEvents;
        this.antiCheat = antiCheat;
        this.gamification = gamification;
        this.rules = rules;
    }

    @Transactional
    public LogWorkoutResponse logWorkout(UUID userId, LogWorkoutRequest request) {
        LocalDate today = LocalDate.now();
        LocalDate date = request.workoutDate() != null ? request.workoutDate() : today;

        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        Character character = characters.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Character not found"));

        // ---- 1. integrity ------------------------------------------------------
        Set<String> slugs = new LinkedHashSet<>();
        for (SetEntry set : request.sets()) {
            slugs.add(Formulas.slug(set.exerciseName()));
        }
        Map<String, Integer> setsAlreadyToday = new HashMap<>();
        for (String slug : slugs) {
            setsAlreadyToday.put(slug,
                    workoutLogs.countByUserIdAndWorkoutDateAndExerciseSlug(userId, date, slug));
        }

        int workoutsToday = workouts.countByUserIdAndWorkoutDate(userId, date);
        var verdict = antiCheat.inspect(date, request.sets(), workoutsToday,
                setsAlreadyToday, character.getBestE1rmKg());
        if (verdict.rejected()) {
            throw new AntiCheatException(verdict.rejections());
        }

        // ---- 2. score every set ------------------------------------------------
        Workout workout = new Workout(user, date, request.title(), request.notes());

        Map<String, Integer> running = new HashMap<>(setsAlreadyToday);
        Map<String, Integer> firstReducedSet = new TreeMap<>();
        double rawXpTotal = 0d;
        double effectiveXpTotal = 0d;
        double effectiveVolumeTotal = 0d;
        double staPointsRaw = 0d;
        double totalVolume = 0d;
        int totalSets = 0;
        int totalDurationSec = 0;
        double bestE1rmThisSession = 0d;
        boolean anyFlagged = false;

        double[] effectiveXp = new double[request.sets().size()];
        List<WorkoutLog> logs = new ArrayList<>();

        // Session-wide set counter. The per-exercise curve alone is dodged by
        // spreading one movement across invented names, so a second, gentler
        // curve runs across every set logged that day.
        int setsAlreadyTodayAllExercises = workoutLogs.countByUserIdAndWorkoutDate(userId, date);

        for (int i = 0; i < request.sets().size(); i++) {
            SetEntry entry = request.sets().get(i);
            String slug = Formulas.slug(entry.exerciseName());
            int setIndexToday = running.merge(slug, 1, Integer::sum);
            int setIndexTodayAllExercises = setsAlreadyTodayAllExercises + i + 1;

            double volume = Formulas.volumeKg(entry.kind(), entry.weightKg(), entry.reps());
            double e1rm = entry.kind() == WorkoutLog.Kind.STRENGTH
                    ? Formulas.epleyE1rm(entry.weightKg(), entry.reps())
                    : 0d;
            double rawXp = gamification.rawXpFor(entry.kind(), volume, entry.durationSec(), entry.intensity());
            double multiplier = gamification.multiplierFor(setIndexToday)
                    * gamification.dailyMultiplierFor(setIndexTodayAllExercises);

            if (multiplier < 1d) {
                firstReducedSet.putIfAbsent(entry.exerciseName().trim(), setIndexToday);
            }

            WorkoutLog log = new WorkoutLog(entry.exerciseName().trim(), slug, entry.kind());
            log.setSetIndex(Math.min(setIndexToday, rules.getMaxSetsPerExercisePerDay()));
            log.setReps(entry.reps());
            log.setWeightKg(entry.weightKg());
            log.setDurationSec(entry.durationSec());
            log.setIntensity(entry.intensity());
            log.setVolumeKg(Formulas.round2(volume));
            log.setE1rmKg(Formulas.round2(e1rm));
            log.setXpRaw(Formulas.round2(rawXp));
            log.setXpMultiplier(multiplier);

            String flagReason = verdict.flagsBySetIndex().get(i);
            if (flagReason != null) {
                log.setFlagged(true);
                log.setFlagReason(flagReason);
                anyFlagged = true;
            }

            workout.addLog(log);
            logs.add(log);
            effectiveXp[i] = rawXp * multiplier;

            rawXpTotal += rawXp;
            effectiveXpTotal += rawXp * multiplier;
            effectiveVolumeTotal += volume * multiplier;
            totalVolume += volume;
            totalSets++;
            totalDurationSec += entry.durationSec();
            bestE1rmThisSession = Math.max(bestE1rmThisSession, e1rm);

            // High-rep work builds stamina as well as strength.
            if (entry.kind() != WorkoutLog.Kind.CARDIO && entry.reps() >= 12) {
                staPointsRaw += (entry.reps() - 11) * 0.05d * multiplier;
            }
            if (entry.kind() == WorkoutLog.Kind.CARDIO) {
                staPointsRaw += (entry.durationSec() / 60d) * entry.intensity() / 10d * multiplier;
            }
        }

        // ---- 3a. cardio sub-cap -------------------------------------------------
        // Cardio is a self-reported duration and intensity with nothing to check it
        // against: one 166-minute "intensity 10" entry used to fill the entire day.
        double cardioAlreadyToday = workoutLogs.sumXpForKindOn(userId, date, WorkoutLog.Kind.CARDIO);
        double cardioThisSession = 0d;
        for (int i = 0; i < logs.size(); i++) {
            if (logs.get(i).getKind() == WorkoutLog.Kind.CARDIO) {
                cardioThisSession += effectiveXp[i];
            }
        }
        double cardioAllowance = Math.max(0d, rules.getDailyCardioXpCap() - cardioAlreadyToday);
        boolean cardioCapped = cardioThisSession > cardioAllowance;
        if (cardioCapped) {
            double cardioScale = cardioThisSession > 0 ? cardioAllowance / cardioThisSession : 0d;
            for (int i = 0; i < logs.size(); i++) {
                if (logs.get(i).getKind() == WorkoutLog.Kind.CARDIO) {
                    effectiveXpTotal -= effectiveXp[i] * (1 - cardioScale);
                    staPointsRaw -= (logs.get(i).getDurationSec() / 60d)
                            * logs.get(i).getIntensity() / 10d
                            * logs.get(i).getXpMultiplier() * (1 - cardioScale);
                    effectiveXp[i] *= cardioScale;
                }
            }
        }

        // ---- 3b. daily cap, applied proportionally ------------------------------
        long alreadyEarnedToday = xpEvents.sumCappedXpOn(userId, date);
        long remainingToday = Math.max(0L, rules.getDailyXpCap() - alreadyEarnedToday);
        long earned = Math.round(effectiveXpTotal);
        long granted = Math.min(earned, remainingToday);
        double capScale = effectiveXpTotal > 0 ? granted / effectiveXpTotal : 0d;
        boolean capReached = granted < earned || remainingToday - granted <= 0;

        for (int i = 0; i < logs.size(); i++) {
            logs.get(i).setXpAwarded(Formulas.round2(effectiveXp[i] * capScale));
        }

        workout.setTotalSets(totalSets);
        workout.setTotalVolumeKg(Formulas.round2(totalVolume));
        workout.setDurationMinutes(totalDurationSec / 60);
        workout.setXpRaw(Math.round(rawXpTotal));
        workout.setXpAwarded(granted);
        workout.setDailyCapReached(capReached);
        workout.setFlaggedForReview(anyFlagged);
        workout.setCountsForConsistency(!date.isBefore(today.minusDays(rules.getStreakCreditMaxAgeDays())));

        // ---- 4. persist, then derive -------------------------------------------
        workouts.saveAndFlush(workout);

        int strBefore = character.getStr();
        int staBefore = character.getSta();
        int conBefore = character.getCon();

        // Stat points are scaled by the same multipliers and cap as XP: junk volume
        // that earned no XP must not quietly inflate STR either.
        gamification.addTrainingPoints(character,
                (effectiveVolumeTotal * capScale) / 1000d,
                staPointsRaw * capScale);
        gamification.registerE1rm(character, bestE1rmThisSession);
        gamification.registerTrainingDay(character, date);

        int activeDays = workouts.countActiveDaysSince(userId, today.minusDays(29));
        gamification.recomputeStats(character, activeDays);

        GamificationService.LevelUp levelUp = gamification.awardXp(character, granted);
        characters.save(character);

        if (granted > 0) {
            xpEvents.save(new XpEvent(user, date, XpEvent.Source.WORKOUT, granted, true, workout.getId()));
        }

        // ---- 5. response --------------------------------------------------------
        List<SetResult> setResults = new ArrayList<>(logs.size());
        for (WorkoutLog log : logs) {
            setResults.add(new SetResult(
                    log.getExerciseName(),
                    log.getSetIndex(),
                    log.getVolumeKg(),
                    log.getXpRaw(),
                    log.getXpMultiplier(),
                    log.getXpAwarded(),
                    log.getE1rmKg() > 0 && log.getE1rmKg() >= character.getBestE1rmKg(),
                    log.isFlagged(),
                    log.getFlagReason()));
        }

        List<String> notices = new ArrayList<>();
        if (granted < earned) {
            notices.add("Daily cap reached: " + granted + " of " + earned
                    + " XP banked. The cap resets at midnight.");
        }
        firstReducedSet.forEach((exercise, setIndex) -> notices.add(
                exercise + ": set " + setIndex + " and beyond paid reduced XP today (diminishing returns)."));
        if (cardioCapped) {
            notices.add("Cardio pays at most " + rules.getDailyCardioXpCap()
                    + " XP a day. The rest of this session's cardio was not banked.");
        }
        if (!workout.isCountsForConsistency()) {
            notices.add("Backdated more than " + rules.getStreakCreditMaxAgeDays()
                    + " day(s): XP banked, but this session does not build your streak or CON.");
        }
        if (anyFlagged) {
            notices.add("Some sets are flagged for verification. They earned XP but do not count toward duels.");
        }
        if (levelUp.happened()) {
            notices.add("Level up! You are now level " + levelUp.newLevel() + ".");
        }

        long dailyEarned = alreadyEarnedToday + granted;
        CharacterView view = gamification.toView(character, dailyEarned);

        return new LogWorkoutResponse(
                workout.getId(),
                date,
                workout.getXpRaw(),
                granted,
                capReached,
                dailyEarned,
                Math.max(0L, rules.getDailyXpCap() - dailyEarned),
                setResults,
                notices,
                levelUp.happened(),
                levelUp.levelsGained(),
                levelUp.newLevel(),
                new StatDelta(character.getStr() - strBefore,
                        character.getSta() - staBefore,
                        character.getCon() - conBefore),
                view);
    }

    @Transactional(readOnly = true)
    public List<WorkoutSummary> recentWorkouts(UUID userId) {
        return workouts.findTop20ByUserIdOrderByWorkoutDateDescLoggedAtDesc(userId).stream()
                .map(w -> new WorkoutSummary(
                        w.getId(),
                        w.getWorkoutDate(),
                        w.getLoggedAt(),
                        w.getTitle(),
                        w.getTotalSets(),
                        w.getTotalVolumeKg(),
                        w.getXpAwarded(),
                        w.isFlaggedForReview()))
                .toList();
    }

    /** How much of today's 1,000 XP budget is left - the logger shows this live. */
    @Transactional(readOnly = true)
    public long remainingDailyXp(UUID userId) {
        long earned = xpEvents.sumCappedXpOn(userId, LocalDate.now());
        return Math.max(0L, rules.getDailyXpCap() - earned);
    }
}
