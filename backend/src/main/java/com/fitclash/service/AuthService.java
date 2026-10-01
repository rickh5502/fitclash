// File: src/main/java/com/fitclash/service/AuthService.java
package com.fitclash.service;

import com.fitclash.domain.Character;
import com.fitclash.domain.User;
import com.fitclash.repo.CharacterRepository;
import com.fitclash.repo.UserRepository;
import com.fitclash.repo.XpEventRepository;
import com.fitclash.security.JwtService;
import com.fitclash.web.dto.Dtos.AuthResponse;
import com.fitclash.web.dto.Dtos.LoginRequest;
import com.fitclash.web.dto.Dtos.RegisterRequest;
import com.fitclash.web.dto.Dtos.UserSummary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

@Service
public class AuthService {

    private static final String[] ARCHETYPES = {"VANGUARD", "BERSERKER", "RANGER", "MONK", "SENTINEL"};

    private final UserRepository users;
    private final CharacterRepository characters;
    private final XpEventRepository xpEvents;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GamificationService gamification;

    public AuthService(UserRepository users,
                       CharacterRepository characters,
                       XpEventRepository xpEvents,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       GamificationService gamification) {
        this.users = users;
        this.characters = characters;
        this.xpEvents = xpEvents;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.gamification = gamification;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();

        if (users.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("That username is taken.");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("That email already has an account.");
        }

        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? username
                : request.displayName().trim();

        User user = new User(username, email, passwordEncoder.encode(request.password()), displayName);
        // Cosmetic starting class, derived from the username so it is stable.
        Character character = new Character(user, ARCHETYPES[Math.floorMod(username.hashCode(), ARCHETYPES.length)]);
        user.setCharacter(character);

        // CascadeType.ALL on User#character persists the sheet with the account,
        // in one transaction. No account can exist without a character.
        users.save(user);

        return buildResponse(user, character);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String identifier = request.usernameOrEmail().trim();

        Optional<User> found = identifier.contains("@")
                ? users.findByEmailIgnoreCase(identifier)
                : users.findByUsernameIgnoreCase(identifier);

        User user = found.orElseThrow(() -> ApiException.unauthorized("Wrong username or password."));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Wrong username or password.");
        }

        user.setLastLoginAt(Instant.now());
        users.save(user);

        Character character = characters.findByUserId(user.getId())
                .orElseThrow(() -> ApiException.notFound("Character not found"));

        return buildResponse(user, character);
    }

    private AuthResponse buildResponse(User user, Character character) {
        long dailyXp = xpEvents.sumCappedXpOn(user.getId(), LocalDate.now());
        return new AuthResponse(
                jwtService.issue(user.getId(), user.getUsername()),
                "Bearer",
                jwtService.getTtlSeconds(),
                new UserSummary(user.getId(), user.getUsername(), user.getDisplayName()),
                gamification.toView(character, dailyXp));
    }
}
