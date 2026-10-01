// File: src/main/java/com/fitclash/web/SocialController.java
package com.fitclash.web;

import com.fitclash.security.AuthPrincipal;
import com.fitclash.service.SocialService;
import com.fitclash.web.dto.Dtos.CharacterView;
import com.fitclash.web.dto.Dtos.FriendRequestBody;
import com.fitclash.web.dto.Dtos.FriendView;
import com.fitclash.web.dto.Dtos.LeaderboardRow;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Character sheet, friends and the leaderboard.
 * The leaderboard is the one open endpoint: it is the shareable part of the game.
 */
@RestController
public class SocialController {

    private final SocialService social;

    public SocialController(SocialService social) {
        this.social = social;
    }

    @GetMapping("/api/characters/me")
    public CharacterView me(@AuthenticationPrincipal AuthPrincipal principal) {
        return social.me(principal.userId());
    }

    @GetMapping("/api/leaderboard")
    public List<LeaderboardRow> leaderboard(@RequestParam(defaultValue = "25") int limit) {
        return social.leaderboard(limit);
    }

    @GetMapping("/api/friends")
    public List<FriendView> friends(@AuthenticationPrincipal AuthPrincipal principal) {
        return social.friends(principal.userId());
    }

    @GetMapping("/api/friends/search")
    public List<FriendView> search(@RequestParam("q") String query) {
        return social.search(query);
    }

    @PostMapping("/api/friends/requests")
    public FriendView request(@AuthenticationPrincipal AuthPrincipal principal,
                              @Valid @RequestBody FriendRequestBody body) {
        return social.requestFriend(principal.userId(), body.username());
    }

    @PostMapping("/api/friends/requests/{friendshipId}/accept")
    public FriendView accept(@AuthenticationPrincipal AuthPrincipal principal,
                             @PathVariable UUID friendshipId) {
        return social.respond(principal.userId(), friendshipId, true);
    }

    @PostMapping("/api/friends/requests/{friendshipId}/decline")
    public FriendView decline(@AuthenticationPrincipal AuthPrincipal principal,
                              @PathVariable UUID friendshipId) {
        return social.respond(principal.userId(), friendshipId, false);
    }
}
