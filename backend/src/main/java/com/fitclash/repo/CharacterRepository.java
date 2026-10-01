// File: src/main/java/com/fitclash/repo/CharacterRepository.java
package com.fitclash.repo;

import com.fitclash.domain.Character;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CharacterRepository extends JpaRepository<Character, UUID> {

    @Query("select c from Character c join fetch c.user where c.user.id = :userId")
    Optional<Character> findByUserId(@Param("userId") UUID userId);

    /** Leaderboard: served straight from idx_characters_rank. */
    @Query("select c from Character c join fetch c.user order by c.level desc, c.lifetimeXp desc")
    List<Character> findLeaderboard(Pageable pageable);

    @Query("select c from Character c join fetch c.user where c.user.id in :userIds")
    List<Character> findAllByUserIds(@Param("userIds") List<UUID> userIds);
}
