// File: src/main/java/com/fitclash/repo/WorkoutLogRepository.java
package com.fitclash.repo;

import com.fitclash.domain.WorkoutLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

public interface WorkoutLogRepository extends JpaRepository<WorkoutLog, UUID> {

    /**
     * How many sets of this exercise the user already banked today.
     * This is the single number diminishing returns hangs on, so it gets its
     * own covering index (idx_logs_daily_sets).
     */
    int countByUserIdAndWorkoutDateAndExerciseSlug(UUID userId, LocalDate workoutDate, String exerciseSlug);

    /** Session-wide set count for the day, for the anti-splitting curve. */
    int countByUserIdAndWorkoutDate(UUID userId, LocalDate workoutDate);

    /** How much XP one kind of work has already paid today - used for the cardio sub-cap. */
    @Query("""
           select coalesce(sum(l.xpAwarded), 0) from WorkoutLog l
           where l.userId = :userId and l.workoutDate = :day and l.kind = :kind
           """)
    double sumXpForKindOn(@Param("userId") UUID userId,
                          @Param("day") LocalDate day,
                          @Param("kind") WorkoutLog.Kind kind);

    @Query("""
           select coalesce(max(l.e1rmKg), 0) from WorkoutLog l
           where l.userId = :userId and l.exerciseSlug = :slug
           """)
    double bestE1rmForExercise(@Param("userId") UUID userId, @Param("slug") String slug);
}
