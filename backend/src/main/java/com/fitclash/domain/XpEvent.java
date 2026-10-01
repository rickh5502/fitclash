// File: src/main/java/com/fitclash/domain/XpEvent.java
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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Append-only XP ledger.
 *
 * The 1,000 XP daily cap is a SUM over this table for today, not a counter on
 * the character - a counter drifts, a ledger can be replayed and audited.
 * Duel payouts are recorded here too, with countsTowardDailyCap = false so
 * winning a duel can never eat tomorrow's training budget.
 */
@Entity
@Table(name = "xp_events")
public class XpEvent {

    public enum Source { WORKOUT, DUEL_WIN, DUEL_DRAW, DUEL_LOSS, ACHIEVEMENT, ADJUSTMENT }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Source source;

    @Column(nullable = false)
    private long amount;

    @Column(name = "counts_toward_daily_cap", nullable = false)
    private boolean countsTowardDailyCap = true;

    @Column(name = "reference_id", columnDefinition = "uuid")
    private UUID referenceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected XpEvent() {
        // JPA
    }

    public XpEvent(User user, LocalDate eventDate, Source source, long amount,
                   boolean countsTowardDailyCap, UUID referenceId) {
        this.user = user;
        this.eventDate = eventDate;
        this.source = source;
        this.amount = amount;
        this.countsTowardDailyCap = countsTowardDailyCap;
        this.referenceId = referenceId;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public LocalDate getEventDate() { return eventDate; }
    public Source getSource() { return source; }
    public long getAmount() { return amount; }
    public boolean isCountsTowardDailyCap() { return countsTowardDailyCap; }
    public UUID getReferenceId() { return referenceId; }
    public Instant getCreatedAt() { return createdAt; }
}
