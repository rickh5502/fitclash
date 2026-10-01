// File: src/main/java/com/fitclash/domain/Duel.java
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "duels")
public class Duel {

    public enum Status { PENDING, ACTIVE, COMPLETED, DECLINED, EXPIRED, CANCELLED }

    /** What the two fighters are actually racing on. */
    public enum Metric {
        TOTAL_XP,
        TOTAL_VOLUME,
        TOTAL_SETS,
        ACTIVE_DAYS
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challenger_id", nullable = false)
    private User challenger;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opponent_id", nullable = false)
    private User opponent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Metric metric = Metric.TOTAL_XP;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "stake_xp", nullable = false)
    private int stakeXp = 100;

    @Column(name = "duration_days", nullable = false)
    private int durationDays = 7;

    /** Unrated duels still resolve, they just pay nothing (weekly rated quota spent). */
    @Column(nullable = false)
    private boolean rated = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "challenger_score", nullable = false)
    private double challengerScore;

    @Column(name = "opponent_score", nullable = false)
    private double opponentScore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private User winner;

    @Column(name = "resolution_note", length = 160)
    private String resolutionNote;

    protected Duel() {
        // JPA
    }

    public Duel(User challenger, User opponent, Metric metric, int stakeXp, int durationDays) {
        this.challenger = challenger;
        this.opponent = opponent;
        this.metric = metric;
        this.stakeXp = stakeXp;
        this.durationDays = durationDays;
    }

    public boolean involves(UUID userId) {
        return challenger.getId().equals(userId) || opponent.getId().equals(userId);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getChallenger() { return challenger; }
    public void setChallenger(User challenger) { this.challenger = challenger; }

    public User getOpponent() { return opponent; }
    public void setOpponent(User opponent) { this.opponent = opponent; }

    public Metric getMetric() { return metric; }
    public void setMetric(Metric metric) { this.metric = metric; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public int getStakeXp() { return stakeXp; }
    public void setStakeXp(int stakeXp) { this.stakeXp = stakeXp; }

    public int getDurationDays() { return durationDays; }
    public void setDurationDays(int durationDays) { this.durationDays = durationDays; }

    public boolean isRated() { return rated; }
    public void setRated(boolean rated) { this.rated = rated; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }

    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }

    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public double getChallengerScore() { return challengerScore; }
    public void setChallengerScore(double challengerScore) { this.challengerScore = challengerScore; }

    public double getOpponentScore() { return opponentScore; }
    public void setOpponentScore(double opponentScore) { this.opponentScore = opponentScore; }

    public User getWinner() { return winner; }
    public void setWinner(User winner) { this.winner = winner; }

    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }
}
