// File: src/main/java/com/fitclash/repo/DuelRepository.java
package com.fitclash.repo;

import com.fitclash.domain.Duel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DuelRepository extends JpaRepository<Duel, UUID> {

    /** The resolver sweep. */
    List<Duel> findByStatusAndEndsAtBefore(Duel.Status status, Instant cutoff);

    @Query("""
           select d from Duel d
           join fetch d.challenger join fetch d.opponent
           where d.challenger.id = :userId or d.opponent.id = :userId
           order by d.createdAt desc
           """)
    List<Duel> findAllForUser(@Param("userId") UUID userId);

    @Query("""
           select count(d) from Duel d
           where ((d.challenger.id = :a and d.opponent.id = :b)
               or (d.challenger.id = :b and d.opponent.id = :a))
             and d.status in :statuses
           """)
    long countBetweenWithStatus(@Param("a") UUID a,
                                @Param("b") UUID b,
                                @Param("statuses") List<Duel.Status> statuses);

    /** One live duel per pair: a challenge cannot be spammed. */
    default boolean hasLiveDuelBetween(UUID a, UUID b) {
        return countBetweenWithStatus(a, b, List.of(Duel.Status.PENDING, Duel.Status.ACTIVE)) > 0;
    }

    @Query("""
           select count(d) from Duel d
           where (d.challenger.id = :userId or d.opponent.id = :userId)
             and d.rated = true
             and d.status = :status
             and d.resolvedAt >= :since
           """)
    long countSettledSince(@Param("userId") UUID userId,
                           @Param("status") Duel.Status status,
                           @Param("since") Instant since);

    /** Rated-duel quota: how many rated duels this user has settled in the last 7 days. */
    default long countRatedSettledSince(UUID userId, Instant since) {
        return countSettledSince(userId, Duel.Status.COMPLETED, since);
    }

    @Query("""
           select count(d) from Duel d
           where (d.challenger.id = :userId or d.opponent.id = :userId)
             and d.rated = true
             and d.createdAt >= :since
             and d.status not in :ignored
           """)
    long countRatedOpenedSince(@Param("userId") UUID userId,
                               @Param("since") Instant since,
                               @Param("ignored") List<Duel.Status> ignored);

    /**
     * Rated duels this user has in flight or settled in the window. Counting
     * opened - not just settled - duels is what stops someone opening twenty
     * challenges at once to farm the win bonus.
     */
    default long countRatedInWeek(UUID userId, Instant since) {
        return countRatedOpenedSince(userId, since,
                List.of(Duel.Status.DECLINED, Duel.Status.CANCELLED, Duel.Status.EXPIRED));
    }
}
