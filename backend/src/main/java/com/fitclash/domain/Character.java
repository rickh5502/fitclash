// File: src/main/java/com/fitclash/domain/Character.java
package com.fitclash.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The RPG sheet.
 *
 * NOTE: this type shadows java.lang.Character inside com.fitclash.domain.
 * That is deliberate (the domain language says "character"), and every other
 * package imports it explicitly. Do not use boxed chars in this package.
 *
 * str_points / sta_points are raw accumulators; STR and STA are derived from
 * them by GamificationService so the curve can be retuned without a migration.
 */
@Entity
@Table(name = "characters")
public class Character {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 32)
    private String archetype = "VANGUARD";

    @Column(nullable = false)
    private int level = 1;

    /** XP banked inside the current level, always < xpForNextLevel(level). */
    @Column(name = "current_xp", nullable = false)
    private long currentXp = 0L;

    @Column(name = "lifetime_xp", nullable = false)
    private long lifetimeXp = 0L;

    @Column(name = "str", nullable = false)
    private int str = 5;

    @Column(name = "sta", nullable = false)
    private int sta = 5;

    @Column(name = "con", nullable = false)
    private int con = 5;

    @Column(name = "str_points", nullable = false)
    private double strPoints = 0d;

    @Column(name = "sta_points", nullable = false)
    private double staPoints = 0d;

    @Column(name = "best_e1rm_kg", nullable = false)
    private double bestE1rmKg = 0d;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak = 0;

    @Column(name = "longest_streak", nullable = false)
    private int longestStreak = 0;

    @Column(name = "last_workout_date")
    private LocalDate lastWorkoutDate;

    @Column(name = "duels_won", nullable = false)
    private int duelsWon = 0;

    @Column(name = "duels_lost", nullable = false)
    private int duelsLost = 0;

    @Column(name = "duels_drawn", nullable = false)
    private int duelsDrawn = 0;

    /** Two phones logging the same session must not both read-modify-write the XP. */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Character() {
        // JPA
    }

    public Character(User user, String archetype) {
        this.user = user;
        this.archetype = archetype;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getArchetype() { return archetype; }
    public void setArchetype(String archetype) { this.archetype = archetype; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public long getCurrentXp() { return currentXp; }
    public void setCurrentXp(long currentXp) { this.currentXp = currentXp; }

    public long getLifetimeXp() { return lifetimeXp; }
    public void setLifetimeXp(long lifetimeXp) { this.lifetimeXp = lifetimeXp; }

    public int getStr() { return str; }
    public void setStr(int str) { this.str = str; }

    public int getSta() { return sta; }
    public void setSta(int sta) { this.sta = sta; }

    public int getCon() { return con; }
    public void setCon(int con) { this.con = con; }

    public double getStrPoints() { return strPoints; }
    public void setStrPoints(double strPoints) { this.strPoints = strPoints; }

    public double getStaPoints() { return staPoints; }
    public void setStaPoints(double staPoints) { this.staPoints = staPoints; }

    public double getBestE1rmKg() { return bestE1rmKg; }
    public void setBestE1rmKg(double bestE1rmKg) { this.bestE1rmKg = bestE1rmKg; }

    public int getCurrentStreak() { return currentStreak; }

    public void setCurrentStreak(int currentStreak) {
        this.currentStreak = currentStreak;
        if (currentStreak > longestStreak) {
            this.longestStreak = currentStreak;
        }
    }

    public int getLongestStreak() { return longestStreak; }
    public void setLongestStreak(int longestStreak) { this.longestStreak = longestStreak; }

    public LocalDate getLastWorkoutDate() { return lastWorkoutDate; }
    public void setLastWorkoutDate(LocalDate lastWorkoutDate) { this.lastWorkoutDate = lastWorkoutDate; }

    public int getDuelsWon() { return duelsWon; }
    public void setDuelsWon(int duelsWon) { this.duelsWon = duelsWon; }

    public int getDuelsLost() { return duelsLost; }
    public void setDuelsLost(int duelsLost) { this.duelsLost = duelsLost; }

    public int getDuelsDrawn() { return duelsDrawn; }
    public void setDuelsDrawn(int duelsDrawn) { this.duelsDrawn = duelsDrawn; }

    public long getVersion() { return version; }

    public Instant getUpdatedAt() { return updatedAt; }
}
