// File: src/main/java/com/fitclash/domain/WorkoutLog.java
package com.fitclash.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One set of one exercise, or one block of cardio.
 *
 * user_id and workout_date are denormalised from the parent workout: the
 * diminishing-returns rule needs "how many sets of THIS exercise has this user
 * already done TODAY", and that must not turn into a join on the hot path.
 */
@Entity
@Table(name = "workout_logs")
public class WorkoutLog {

    public enum Kind {
        /** Loaded work: XP comes from weight x reps. */
        STRENGTH,
        /** Unloaded reps: XP comes from reps against a nominal bodyweight load. */
        BODYWEIGHT,
        /** Time under effort: XP comes from duration x intensity. */
        CARDIO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_id", nullable = false)
    private Workout workout;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "workout_date", nullable = false)
    private LocalDate workoutDate;

    @Column(name = "exercise_name", nullable = false, length = 80)
    private String exerciseName;

    @Column(name = "exercise_slug", nullable = false, length = 80)
    private String exerciseSlug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind = Kind.STRENGTH;

    /** 1-based ordinal for this exercise on this day - drives diminishing returns. */
    @Column(name = "set_index", nullable = false)
    private int setIndex = 1;

    @Column(nullable = false)
    private int reps;

    @Column(name = "weight_kg", nullable = false)
    private double weightKg;

    @Column(name = "duration_sec", nullable = false)
    private int durationSec;

    @Column(nullable = false)
    private int intensity = 5;

    @Column(name = "volume_kg", nullable = false)
    private double volumeKg;

    /** Epley estimated 1-rep max, used for PR detection. */
    @Column(name = "e1rm_kg", nullable = false)
    private double e1rmKg;

    @Column(name = "xp_raw", nullable = false)
    private double xpRaw;

    @Column(name = "xp_multiplier", nullable = false)
    private double xpMultiplier = 1d;

    @Column(name = "xp_awarded", nullable = false)
    private double xpAwarded;

    @Column(nullable = false)
    private boolean flagged;

    @Column(name = "flag_reason", length = 160)
    private String flagReason;

    protected WorkoutLog() {
        // JPA
    }

    public WorkoutLog(String exerciseName, String exerciseSlug, Kind kind) {
        this.exerciseName = exerciseName;
        this.exerciseSlug = exerciseSlug;
        this.kind = kind;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Workout getWorkout() { return workout; }

    public void setWorkout(Workout workout) {
        this.workout = workout;
        if (workout != null) {
            this.userId = workout.getUser() != null ? workout.getUser().getId() : this.userId;
            this.workoutDate = workout.getWorkoutDate();
        }
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public LocalDate getWorkoutDate() { return workoutDate; }
    public void setWorkoutDate(LocalDate workoutDate) { this.workoutDate = workoutDate; }

    public String getExerciseName() { return exerciseName; }
    public void setExerciseName(String exerciseName) { this.exerciseName = exerciseName; }

    public String getExerciseSlug() { return exerciseSlug; }
    public void setExerciseSlug(String exerciseSlug) { this.exerciseSlug = exerciseSlug; }

    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }

    public int getSetIndex() { return setIndex; }
    public void setSetIndex(int setIndex) { this.setIndex = setIndex; }

    public int getReps() { return reps; }
    public void setReps(int reps) { this.reps = reps; }

    public double getWeightKg() { return weightKg; }
    public void setWeightKg(double weightKg) { this.weightKg = weightKg; }

    public int getDurationSec() { return durationSec; }
    public void setDurationSec(int durationSec) { this.durationSec = durationSec; }

    public int getIntensity() { return intensity; }
    public void setIntensity(int intensity) { this.intensity = intensity; }

    public double getVolumeKg() { return volumeKg; }
    public void setVolumeKg(double volumeKg) { this.volumeKg = volumeKg; }

    public double getE1rmKg() { return e1rmKg; }
    public void setE1rmKg(double e1rmKg) { this.e1rmKg = e1rmKg; }

    public double getXpRaw() { return xpRaw; }
    public void setXpRaw(double xpRaw) { this.xpRaw = xpRaw; }

    public double getXpMultiplier() { return xpMultiplier; }
    public void setXpMultiplier(double xpMultiplier) { this.xpMultiplier = xpMultiplier; }

    public double getXpAwarded() { return xpAwarded; }
    public void setXpAwarded(double xpAwarded) { this.xpAwarded = xpAwarded; }

    public boolean isFlagged() { return flagged; }
    public void setFlagged(boolean flagged) { this.flagged = flagged; }

    public String getFlagReason() { return flagReason; }
    public void setFlagReason(String flagReason) { this.flagReason = flagReason; }
}
