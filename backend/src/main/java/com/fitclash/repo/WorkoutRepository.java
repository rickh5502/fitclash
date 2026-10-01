// File: src/main/java/com/fitclash/repo/WorkoutRepository.java
package com.fitclash.repo;

import com.fitclash.domain.Workout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WorkoutRepository extends JpaRepository<Workout, UUID> {

    int countByUserIdAndWorkoutDate(UUID userId, LocalDate workoutDate);

    List<Workout> findTop20ByUserIdOrderByWorkoutDateDescLoggedAtDesc(UUID userId);

    /**
     * Consistency input: distinct training days in the trailing window.
     * Backfilled days are excluded, so CON cannot be bought retroactively.
     */
    @Query("""
           select count(distinct w.workoutDate) from Workout w
           where w.user.id = :userId
             and w.workoutDate >= :since
             and w.countsForConsistency = true
           """)
    int countActiveDaysSince(@Param("userId") UUID userId, @Param("since") LocalDate since);

    /** Duel metric TOTAL_VOLUME. Flagged sessions are excluded from competitive scoring. */
    @Query("""
           select coalesce(sum(w.totalVolumeKg), 0) from Workout w
           where w.user.id = :userId
             and w.workoutDate between :from and :to
             and w.flaggedForReview = false
           """)
    double sumVolumeBetween(@Param("userId") UUID userId,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to);

    /** Duel metric TOTAL_SETS. */
    @Query("""
           select coalesce(sum(w.totalSets), 0) from Workout w
           where w.user.id = :userId
             and w.workoutDate between :from and :to
             and w.flaggedForReview = false
           """)
    long sumSetsBetween(@Param("userId") UUID userId,
                        @Param("from") LocalDate from,
                        @Param("to") LocalDate to);

    /** Duel metric ACTIVE_DAYS, and the tie-breaker for every other metric. */
    @Query("""
           select count(distinct w.workoutDate) from Workout w
           where w.user.id = :userId
             and w.workoutDate between :from and :to
             and w.flaggedForReview = false
           """)
    long countActiveDaysBetween(@Param("userId") UUID userId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to);
}
