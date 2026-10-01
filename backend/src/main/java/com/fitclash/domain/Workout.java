// File: src/main/java/com/fitclash/domain/Workout.java
package com.fitclash.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "workouts")
public class Workout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "workout_date", nullable = false)
    private LocalDate workoutDate;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt = Instant.now();

    @Column(length = 80)
    private String title;

    @Column(length = 500)
    private String notes;

    @Column(name = "total_sets", nullable = false)
    private int totalSets;

    @Column(name = "total_volume_kg", nullable = false)
    private double totalVolumeKg;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    /** XP the session would have paid with no diminishing returns and no cap. */
    @Column(name = "xp_raw", nullable = false)
    private long xpRaw;

    @Column(name = "xp_awarded", nullable = false)
    private long xpAwarded;

    @Column(name = "daily_cap_reached", nullable = false)
    private boolean dailyCapReached;

    @Column(name = "flagged_for_review", nullable = false)
    private boolean flaggedForReview;

    /**
     * False for sessions backdated beyond the grace window. They still bank XP,
     * but they do not count toward CON - otherwise a new account backfills two
     * weeks of one-set days and buys the consistency stat outright.
     */
    @Column(name = "counts_for_consistency", nullable = false)
    private boolean countsForConsistency = true;

    @OneToMany(mappedBy = "workout", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("exerciseSlug ASC, setIndex ASC")
    private List<WorkoutLog> logs = new ArrayList<>();

    protected Workout() {
        // JPA
    }

    public Workout(User user, LocalDate workoutDate, String title, String notes) {
        this.user = user;
        this.workoutDate = workoutDate;
        this.title = title;
        this.notes = notes;
    }

    public void addLog(WorkoutLog log) {
        logs.add(log);
        log.setWorkout(this);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LocalDate getWorkoutDate() { return workoutDate; }
    public void setWorkoutDate(LocalDate workoutDate) { this.workoutDate = workoutDate; }

    public Instant getLoggedAt() { return loggedAt; }
    public void setLoggedAt(Instant loggedAt) { this.loggedAt = loggedAt; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public int getTotalSets() { return totalSets; }
    public void setTotalSets(int totalSets) { this.totalSets = totalSets; }

    public double getTotalVolumeKg() { return totalVolumeKg; }
    public void setTotalVolumeKg(double totalVolumeKg) { this.totalVolumeKg = totalVolumeKg; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public long getXpRaw() { return xpRaw; }
    public void setXpRaw(long xpRaw) { this.xpRaw = xpRaw; }

    public long getXpAwarded() { return xpAwarded; }
    public void setXpAwarded(long xpAwarded) { this.xpAwarded = xpAwarded; }

    public boolean isDailyCapReached() { return dailyCapReached; }
    public void setDailyCapReached(boolean dailyCapReached) { this.dailyCapReached = dailyCapReached; }

    public boolean isFlaggedForReview() { return flaggedForReview; }
    public void setFlaggedForReview(boolean flaggedForReview) { this.flaggedForReview = flaggedForReview; }

    public boolean isCountsForConsistency() { return countsForConsistency; }
    public void setCountsForConsistency(boolean v) { this.countsForConsistency = v; }

    public List<WorkoutLog> getLogs() { return logs; }
    public void setLogs(List<WorkoutLog> logs) { this.logs = logs; }
}
