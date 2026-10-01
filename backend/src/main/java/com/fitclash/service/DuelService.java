// File: src/main/java/com/fitclash/service/DuelService.java
package com.fitclash.service;

import com.fitclash.config.GameProperties;
import com.fitclash.domain.Character;
import com.fitclash.domain.Duel;
import com.fitclash.domain.User;
import com.fitclash.domain.XpEvent;
import com.fitclash.repo.CharacterRepository;
import com.fitclash.repo.DuelRepository;
import com.fitclash.repo.FriendshipRepository;
import com.fitclash.repo.UserRepository;
import com.fitclash.repo.WorkoutRepository;
import com.fitclash.repo.XpEventRepository;
import com.fitclash.web.dto.Dtos.CreateDuelRequest;
import com.fitclash.web.dto.Dtos.DuelSide;
import com.fitclash.web.dto.Dtos.DuelView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 3 - 1v1 duels.
 *
 * Rules
 *   * You may only duel an accepted friend. No cold-calling strangers.
 *   * One live duel per pair at a time (also a unique index in the schema).
 *   * A duel runs over whole calendar days: accepting at 21:00 gives you the
 *     rest of today plus N-1 full days, the same deal for both sides.
 *   * Only unflagged sessions score. A duel is exactly where a cheater would
 *     spend a fake 400 kg bench, so verification-flagged work is worth nothing here.
 *   * Victory: higher score on the chosen metric.
 *     Tie-breakers, in order:  1. more active days in the window
 *                              2. more total volume in the window
 *                              3. higher CON (consistency breaks ties, thematically)
 *                              4. genuine draw - both sides get the consolation payout
 *   * Payout is exempt from the 1,000/day cap - you earned it in the arena, not
 *     the gym - but the weekly rated quota (3) stops duel-farming outright.
 *     Duels past the quota still run and still resolve; they just pay nothing.
 */
@Service
public class DuelService {

    private static final Logger log = LoggerFactory.getLogger(DuelService.class);

    private final DuelRepository duels;
    private final UserRepository users;
    private final CharacterRepository characters;
    private final FriendshipRepository friendships;
    private final WorkoutRepository workouts;
    private final XpEventRepository xpEvents;
    private final GamificationService gamification;
    private final GameProperties rules;

    public DuelService(DuelRepository duels,
                       UserRepository users,
                       CharacterRepository characters,
                       FriendshipRepository friendships,
                       WorkoutRepository workouts,
                       XpEventRepository xpEvents,
                       GamificationService gamification,
                       GameProperties rules) {
        this.duels = duels;
        this.users = users;
        this.characters = characters;
        this.friendships = friendships;
        this.workouts = workouts;
        this.xpEvents = xpEvents;
        this.gamification = gamification;
        this.rules = rules;
    }

    // ------------------------------------------------------------------ lifecycle

    @Transactional
    public DuelView challenge(UUID challengerId, CreateDuelRequest request) {
        if (challengerId.equals(request.opponentId())) {
            throw ApiException.badRequest("You cannot duel yourself.");
        }
        User challenger = users.findById(challengerId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        User opponent = users.findById(request.opponentId())
                .orElseThrow(() -> ApiException.notFound("Opponent not found"));

        if (!friendships.areFriends(challengerId, opponent.getId())) {
            throw ApiException.forbidden("You can only duel friends. Send a friend request first.");
        }
        if (duels.hasLiveDuelBetween(challengerId, opponent.getId())) {
            throw ApiException.conflict("You already have a duel running with " + opponent.getUsername() + ".");
        }

        Duel duel = new Duel(challenger, opponent, request.metric(),
                request.stakeXp(), request.durationDays());

        Instant weekAgo = Instant.now().minus(Duration.ofDays(7));
        boolean quotaLeft = duels.countRatedInWeek(challengerId, weekAgo) < rules.getMaxRatedDuelsPerWeek()
                && duels.countRatedInWeek(opponent.getId(), weekAgo) < rules.getMaxRatedDuelsPerWeek();
        duel.setRated(quotaLeft);
        if (!quotaLeft) {
            duel.setResolutionNote("Unrated: weekly rated-duel quota reached.");
        }

        duels.save(duel);
        return toView(duel, challengerId, levelsFor(List.of(challenger.getId(), opponent.getId())));
    }

    @Transactional
    public DuelView accept(UUID userId, UUID duelId) {
        Duel duel = load(duelId, userId);
        if (!duel.getOpponent().getId().equals(userId)) {
            throw ApiException.forbidden("Only the challenged fighter can accept.");
        }
        if (duel.getStatus() != Duel.Status.PENDING) {
            throw ApiException.conflict("This duel is " + duel.getStatus().name().toLowerCase() + ".");
        }

        Instant now = Instant.now();
        duel.setStatus(Duel.Status.ACTIVE);
        duel.setAcceptedAt(now);
        duel.setStartsAt(now);
        // Whole calendar days: the rest of today plus the remaining days.
        LocalDate lastDay = LocalDate.now(ZoneOffset.UTC).plusDays(duel.getDurationDays() - 1L);
        duel.setEndsAt(lastDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());

        duels.save(duel);
        return toView(duel, userId, levelsFor(participants(duel)));
    }

    @Transactional
    public DuelView respondNegatively(UUID userId, UUID duelId, boolean cancel) {
        Duel duel = load(duelId, userId);
        if (duel.getStatus() != Duel.Status.PENDING) {
            throw ApiException.conflict("Only a pending duel can be " + (cancel ? "cancelled" : "declined") + ".");
        }
        if (cancel && !duel.getChallenger().getId().equals(userId)) {
            throw ApiException.forbidden("Only the challenger can withdraw a challenge.");
        }
        if (!cancel && !duel.getOpponent().getId().equals(userId)) {
            throw ApiException.forbidden("Only the challenged fighter can decline.");
        }

        duel.setStatus(cancel ? Duel.Status.CANCELLED : Duel.Status.DECLINED);
        duel.setResolvedAt(Instant.now());
        duels.save(duel);
        return toView(duel, userId, levelsFor(participants(duel)));
    }

    // ------------------------------------------------------------------ resolution

    /** Sweeps expired duels once a minute. Idempotent: a duel pays out exactly once. */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    @Transactional
    public void resolveExpiredDuels() {
        List<Duel> expired = duels.findByStatusAndEndsAtBefore(Duel.Status.ACTIVE, Instant.now());
        for (Duel duel : expired) {
            try {
                resolve(duel);
            } catch (RuntimeException ex) {
                log.error("Failed to resolve duel {}", duel.getId(), ex);
            }
        }
        if (!expired.isEmpty()) {
            log.info("Resolved {} duel(s)", expired.size());
        }
    }

    @Transactional
    public DuelView resolve(Duel duel) {
        LocalDate from = LocalDate.ofInstant(duel.getStartsAt(), ZoneOffset.UTC);
        LocalDate to = LocalDate.ofInstant(duel.getEndsAt().minusSeconds(1), ZoneOffset.UTC);

        UUID challengerId = duel.getChallenger().getId();
        UUID opponentId = duel.getOpponent().getId();

        double challengerScore = score(challengerId, duel.getMetric(), from, to);
        double opponentScore = score(opponentId, duel.getMetric(), from, to);

        duel.setChallengerScore(Formulas.round2(challengerScore));
        duel.setOpponentScore(Formulas.round2(opponentScore));

        Character challengerChar = characters.findByUserId(challengerId).orElseThrow();
        Character opponentChar = characters.findByUserId(opponentId).orElseThrow();

        int verdict = compare(duel, from, to, challengerChar, opponentChar);

        duel.setStatus(Duel.Status.COMPLETED);
        duel.setResolvedAt(Instant.now());

        double margin = Math.abs(challengerScore - opponentScore);

        // Nobody trained. Without this, two friends open a duel, do nothing, let
        // the tie-breakers pick a "winner", and collect the full purse for it.
        if (challengerScore <= 0 && opponentScore <= 0) {
            duel.setWinner(null);
            duel.setResolutionNote("No work logged by either fighter. No result, no XP.");
            duels.save(duel);
            return toView(duel, challengerId, levelsFor(participants(duel)));
        }

        if (verdict == 0) {
            duel.setWinner(null);
            duel.setResolutionNote(duel.isRated()
                    ? "Draw - dead even after every tie-breaker."
                    : "Draw (unrated).");
            challengerChar.setDuelsDrawn(challengerChar.getDuelsDrawn() + 1);
            opponentChar.setDuelsDrawn(opponentChar.getDuelsDrawn() + 1);
            if (duel.isRated()) {
                // Flat, and deliberately NOT a function of the stake. The stake is
                // never escrowed from either account, so paying out a share of it on
                // a draw mints XP from nothing.
                int drawXp = rules.getDuelConsolationXp();
                payout(duel, duel.getChallenger(), challengerChar, drawXp, XpEvent.Source.DUEL_DRAW);
                payout(duel, duel.getOpponent(), opponentChar, drawXp, XpEvent.Source.DUEL_DRAW);
            }
        } else {
            boolean challengerWon = verdict > 0;
            User winner = challengerWon ? duel.getChallenger() : duel.getOpponent();
            Character winnerChar = challengerWon ? challengerChar : opponentChar;
            Character loserChar = challengerWon ? opponentChar : challengerChar;
            User loser = challengerWon ? duel.getOpponent() : duel.getChallenger();

            duel.setWinner(winner);
            duel.setResolutionNote(winner.getUsername() + " takes it by "
                    + Formulas.round2(margin) + " " + metricUnit(duel.getMetric())
                    + (duel.isRated() ? "." : " (unrated)."));

            winnerChar.setDuelsWon(winnerChar.getDuelsWon() + 1);
            loserChar.setDuelsLost(loserChar.getDuelsLost() + 1);

            if (duel.isRated()) {
                int reward = Math.min(rules.getDuelMaxRewardXp(),
                        rules.getDuelBaseRewardXp() + duel.getStakeXp() + (int) Math.round(margin * 0.1d));
                payout(duel, winner, winnerChar, reward, XpEvent.Source.DUEL_WIN);
                payout(duel, loser, loserChar, rules.getDuelConsolationXp(), XpEvent.Source.DUEL_LOSS);
            }
        }

        characters.save(challengerChar);
        characters.save(opponentChar);
        duels.save(duel);
        return toView(duel, challengerId, levelsFor(participants(duel)));
    }

    /** @return &gt;0 challenger wins, &lt;0 opponent wins, 0 draw. */
    private int compare(Duel duel, LocalDate from, LocalDate to, Character challenger, Character opponent) {
        int byScore = Double.compare(duel.getChallengerScore(), duel.getOpponentScore());
        if (byScore != 0) {
            return byScore;
        }
        UUID c = duel.getChallenger().getId();
        UUID o = duel.getOpponent().getId();

        int byDays = Long.compare(workouts.countActiveDaysBetween(c, from, to),
                workouts.countActiveDaysBetween(o, from, to));
        if (byDays != 0) {
            return byDays;
        }
        int byVolume = Double.compare(workouts.sumVolumeBetween(c, from, to),
                workouts.sumVolumeBetween(o, from, to));
        if (byVolume != 0) {
            return byVolume;
        }
        return Integer.compare(challenger.getCon(), opponent.getCon());
    }

    private double score(UUID userId, Duel.Metric metric, LocalDate from, LocalDate to) {
        return switch (metric) {
            case TOTAL_XP -> xpEvents.sumWorkoutXpBetween(userId, from, to);
            case TOTAL_VOLUME -> workouts.sumVolumeBetween(userId, from, to);
            case TOTAL_SETS -> workouts.sumSetsBetween(userId, from, to);
            case ACTIVE_DAYS -> workouts.countActiveDaysBetween(userId, from, to);
        };
    }

    /** Duel XP does not count toward the daily cap, and never pays twice. */
    private void payout(Duel duel, User user, Character character, int amount, XpEvent.Source source) {
        if (amount <= 0 || xpEvents.existsByUserIdAndReferenceId(user.getId(), duel.getId())) {
            return;
        }
        gamification.awardXp(character, amount);
        xpEvents.save(new XpEvent(user, LocalDate.now(), source, amount, false, duel.getId()));
    }

    // ----------------------------------------------------------------------- views

    @Transactional(readOnly = true)
    public List<DuelView> listForUser(UUID userId) {
        List<Duel> all = duels.findAllForUser(userId);
        List<UUID> userIds = new ArrayList<>();
        all.forEach(d -> userIds.addAll(participants(d)));
        Map<UUID, Character> levels = levelsFor(userIds);
        return all.stream().map(d -> toView(d, userId, levels)).toList();
    }

    @Transactional(readOnly = true)
    public DuelView get(UUID userId, UUID duelId) {
        Duel duel = load(duelId, userId);
        return toView(duel, userId, levelsFor(participants(duel)));
    }

    private Duel load(UUID duelId, UUID userId) {
        Duel duel = duels.findById(duelId)
                .orElseThrow(() -> ApiException.notFound("Duel not found"));
        if (!duel.involves(userId)) {
            throw ApiException.forbidden("That duel is not yours.");
        }
        return duel;
    }

    private List<UUID> participants(Duel duel) {
        return List.of(duel.getChallenger().getId(), duel.getOpponent().getId());
    }

    private Map<UUID, Character> levelsFor(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Character> map = new HashMap<>();
        for (Character c : characters.findAllByUserIds(userIds.stream().distinct().toList())) {
            map.put(c.getUser().getId(), c);
        }
        return map;
    }

    private DuelView toView(Duel duel, UUID viewerId, Map<UUID, Character> chars) {
        double challengerScore = duel.getChallengerScore();
        double opponentScore = duel.getOpponentScore();

        // An active duel shows the live standings, recomputed on read.
        if (duel.getStatus() == Duel.Status.ACTIVE && duel.getStartsAt() != null) {
            LocalDate from = LocalDate.ofInstant(duel.getStartsAt(), ZoneOffset.UTC);
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            challengerScore = score(duel.getChallenger().getId(), duel.getMetric(), from, today);
            opponentScore = score(duel.getOpponent().getId(), duel.getMetric(), from, today);
        }

        User challenger = duel.getChallenger();
        User opponent = duel.getOpponent();
        boolean viewerIsChallenger = challenger.getId().equals(viewerId);

        String outcome = switch (duel.getStatus()) {
            case COMPLETED -> duel.getWinner() == null ? "DRAW"
                    : duel.getWinner().getId().equals(viewerId) ? "WIN" : "LOSS";
            default -> duel.getStatus().name();
        };

        return new DuelView(
                duel.getId(),
                duel.getMetric(),
                duel.getStatus(),
                duel.getStakeXp(),
                duel.getDurationDays(),
                duel.isRated(),
                duel.getCreatedAt(),
                duel.getStartsAt(),
                duel.getEndsAt(),
                duel.getResolvedAt(),
                side(challenger, chars, Formulas.round2(challengerScore)),
                side(opponent, chars, Formulas.round2(opponentScore)),
                duel.getWinner() != null ? duel.getWinner().getId() : null,
                duel.getResolutionNote(),
                viewerIsChallenger,
                outcome);
    }

    private DuelSide side(User user, Map<UUID, Character> chars, double score) {
        Character c = chars.get(user.getId());
        return new DuelSide(user.getId(), user.getUsername(), user.getDisplayName(),
                c != null ? c.getLevel() : 1, score);
    }

    private static String metricUnit(Duel.Metric metric) {
        return switch (metric) {
            case TOTAL_XP -> "XP";
            case TOTAL_VOLUME -> "kg";
            case TOTAL_SETS -> "sets";
            case ACTIVE_DAYS -> "days";
        };
    }
}
