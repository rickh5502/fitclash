// File: src/main/java/com/fitclash/repo/XpEventRepository.java
package com.fitclash.repo;

import com.fitclash.domain.XpEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

public interface XpEventRepository extends JpaRepository<XpEvent, UUID> {

    /** The daily cap is this number. Only capped sources count toward it. */
    @Query("""
           select coalesce(sum(e.amount), 0) from XpEvent e
           where e.user.id = :userId
             and e.eventDate = :day
             and e.countsTowardDailyCap = true
           """)
    long sumCappedXpOn(@Param("userId") UUID userId, @Param("day") LocalDate day);

    @Query("""
           select coalesce(sum(e.amount), 0) from XpEvent e
           where e.user.id = :userId
             and e.eventDate between :from and :to
             and e.source = :source
           """)
    long sumBetweenForSource(@Param("userId") UUID userId,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             @Param("source") XpEvent.Source source);

    /** Duel metric TOTAL_XP - training XP only, duel payouts are not re-wagerable. */
    default long sumWorkoutXpBetween(UUID userId, LocalDate from, LocalDate to) {
        return sumBetweenForSource(userId, from, to, XpEvent.Source.WORKOUT);
    }

    boolean existsByUserIdAndReferenceId(UUID userId, UUID referenceId);
}
