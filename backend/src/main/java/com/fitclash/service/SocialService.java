// File: src/main/java/com/fitclash/service/SocialService.java
package com.fitclash.service;

import com.fitclash.domain.Character;
import com.fitclash.domain.Friendship;
import com.fitclash.domain.User;
import com.fitclash.repo.CharacterRepository;
import com.fitclash.repo.FriendshipRepository;
import com.fitclash.repo.UserRepository;
import com.fitclash.repo.XpEventRepository;
import com.fitclash.web.dto.Dtos.CharacterView;
import com.fitclash.web.dto.Dtos.FriendView;
import com.fitclash.web.dto.Dtos.LeaderboardRow;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SocialService {

    private final UserRepository users;
    private final CharacterRepository characters;
    private final FriendshipRepository friendships;
    private final XpEventRepository xpEvents;
    private final GamificationService gamification;

    public SocialService(UserRepository users,
                         CharacterRepository characters,
                         FriendshipRepository friendships,
                         XpEventRepository xpEvents,
                         GamificationService gamification) {
        this.users = users;
        this.characters = characters;
        this.friendships = friendships;
        this.xpEvents = xpEvents;
        this.gamification = gamification;
    }

    @Transactional(readOnly = true)
    public CharacterView me(UUID userId) {
        Character character = characters.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Character not found"));
        return gamification.toView(character, xpEvents.sumCappedXpOn(userId, LocalDate.now()));
    }

    @Transactional(readOnly = true)
    public List<LeaderboardRow> leaderboard(int limit) {
        List<Character> top = characters.findLeaderboard(PageRequest.of(0, Math.min(100, Math.max(1, limit))));
        List<LeaderboardRow> rows = new ArrayList<>(top.size());
        for (int i = 0; i < top.size(); i++) {
            Character c = top.get(i);
            rows.add(new LeaderboardRow(
                    i + 1,
                    c.getUser().getId(),
                    c.getUser().getUsername(),
                    c.getUser().getDisplayName(),
                    c.getArchetype(),
                    c.getLevel(),
                    c.getLifetimeXp(),
                    c.getStr(),
                    c.getSta(),
                    c.getCon(),
                    c.getCurrentStreak(),
                    c.getDuelsWon()));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<FriendView> friends(UUID userId) {
        List<Friendship> all = friendships.findAllForUser(userId);

        List<UUID> otherIds = all.stream()
                .map(f -> f.getRequester().getId().equals(userId)
                        ? f.getAddressee().getId() : f.getRequester().getId())
                .distinct()
                .toList();

        Map<UUID, Integer> levels = new HashMap<>();
        if (!otherIds.isEmpty()) {
            for (Character c : characters.findAllByUserIds(otherIds)) {
                levels.put(c.getUser().getId(), c.getLevel());
            }
        }

        List<FriendView> views = new ArrayList<>(all.size());
        for (Friendship f : all) {
            boolean incoming = f.getAddressee().getId().equals(userId);
            User other = incoming ? f.getRequester() : f.getAddressee();
            views.add(new FriendView(
                    f.getId(),
                    other.getId(),
                    other.getUsername(),
                    other.getDisplayName(),
                    f.getStatus().name(),
                    incoming && f.getStatus() == Friendship.Status.PENDING,
                    levels.get(other.getId())));
        }
        return views;
    }

    @Transactional
    public FriendView requestFriend(UUID userId, String username) {
        User me = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        User target = users.findByUsernameIgnoreCase(username.trim())
                .orElseThrow(() -> ApiException.notFound("No fighter called " + username + "."));

        if (target.getId().equals(userId)) {
            throw ApiException.badRequest("You are already your own best training partner.");
        }

        var existing = friendships.findBetween(userId, target.getId());
        if (existing.isPresent()) {
            Friendship f = existing.get();
            if (f.getStatus() == Friendship.Status.ACCEPTED) {
                throw ApiException.conflict("You are already friends with " + target.getUsername() + ".");
            }
            if (f.getStatus() == Friendship.Status.PENDING) {
                // They asked first: treat this as an acceptance.
                if (f.getAddressee().getId().equals(userId)) {
                    return respond(userId, f.getId(), true);
                }
                throw ApiException.conflict("That request is already pending.");
            }
            // Declined before - let them try again.
            f.setStatus(Friendship.Status.PENDING);
            f.setRequester(me);
            f.setAddressee(target);
            f.setRespondedAt(null);
            friendships.save(f);
            return toView(f, userId, target);
        }

        Friendship friendship = new Friendship(me, target);
        friendships.save(friendship);
        return toView(friendship, userId, target);
    }

    @Transactional
    public FriendView respond(UUID userId, UUID friendshipId, boolean accept) {
        Friendship f = friendships.findById(friendshipId)
                .orElseThrow(() -> ApiException.notFound("Friend request not found"));
        if (!f.getAddressee().getId().equals(userId)) {
            throw ApiException.forbidden("That request was not sent to you.");
        }
        if (f.getStatus() != Friendship.Status.PENDING) {
            throw ApiException.conflict("That request has already been answered.");
        }

        f.setStatus(accept ? Friendship.Status.ACCEPTED : Friendship.Status.DECLINED);
        f.setRespondedAt(Instant.now());
        friendships.save(f);
        return toView(f, userId, f.getRequester());
    }

    @Transactional(readOnly = true)
    public List<FriendView> search(String fragment) {
        return users.findTop10ByUsernameContainingIgnoreCaseOrderByUsernameAsc(fragment.trim()).stream()
                .map(u -> new FriendView(null, u.getId(), u.getUsername(), u.getDisplayName(),
                        "NONE", false, null))
                .toList();
    }

    private FriendView toView(Friendship f, UUID viewerId, User other) {
        boolean incoming = f.getAddressee().getId().equals(viewerId);
        return new FriendView(f.getId(), other.getId(), other.getUsername(), other.getDisplayName(),
                f.getStatus().name(), incoming && f.getStatus() == Friendship.Status.PENDING, null);
    }
}
