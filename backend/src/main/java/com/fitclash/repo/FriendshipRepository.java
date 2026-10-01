// File: src/main/java/com/fitclash/repo/FriendshipRepository.java
package com.fitclash.repo;

import com.fitclash.domain.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    @Query("""
           select f from Friendship f
           join fetch f.requester join fetch f.addressee
           where f.requester.id = :userId or f.addressee.id = :userId
           order by f.createdAt desc
           """)
    List<Friendship> findAllForUser(@Param("userId") UUID userId);

    @Query("""
           select f from Friendship f
           join fetch f.requester join fetch f.addressee
           where (f.requester.id = :a and f.addressee.id = :b)
              or (f.requester.id = :b and f.addressee.id = :a)
           """)
    Optional<Friendship> findBetween(@Param("a") UUID a, @Param("b") UUID b);

    @Query("""
           select case when count(f) > 0 then true else false end from Friendship f
           where f.status = :status
             and ((f.requester.id = :a and f.addressee.id = :b)
               or (f.requester.id = :b and f.addressee.id = :a))
           """)
    boolean existsBetweenWithStatus(@Param("a") UUID a,
                                    @Param("b") UUID b,
                                    @Param("status") Friendship.Status status);

    /** You can only duel someone who agreed to be your friend. */
    default boolean areFriends(UUID a, UUID b) {
        return existsBetweenWithStatus(a, b, Friendship.Status.ACCEPTED);
    }
}
