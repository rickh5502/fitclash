// File: src/main/java/com/fitclash/web/DuelController.java
package com.fitclash.web;

import com.fitclash.security.AuthPrincipal;
import com.fitclash.service.DuelService;
import com.fitclash.web.dto.Dtos.CreateDuelRequest;
import com.fitclash.web.dto.Dtos.DuelView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/duels")
public class DuelController {

    private final DuelService duelService;

    public DuelController(DuelService duelService) {
        this.duelService = duelService;
    }

    @PostMapping
    public ResponseEntity<DuelView> challenge(@AuthenticationPrincipal AuthPrincipal principal,
                                              @Valid @RequestBody CreateDuelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(duelService.challenge(principal.userId(), request));
    }

    @GetMapping
    public List<DuelView> mine(@AuthenticationPrincipal AuthPrincipal principal) {
        return duelService.listForUser(principal.userId());
    }

    @GetMapping("/{duelId}")
    public DuelView one(@AuthenticationPrincipal AuthPrincipal principal,
                        @PathVariable UUID duelId) {
        return duelService.get(principal.userId(), duelId);
    }

    @PostMapping("/{duelId}/accept")
    public DuelView accept(@AuthenticationPrincipal AuthPrincipal principal,
                           @PathVariable UUID duelId) {
        return duelService.accept(principal.userId(), duelId);
    }

    @PostMapping("/{duelId}/decline")
    public DuelView decline(@AuthenticationPrincipal AuthPrincipal principal,
                            @PathVariable UUID duelId) {
        return duelService.respondNegatively(principal.userId(), duelId, false);
    }

    @PostMapping("/{duelId}/withdraw")
    public DuelView withdraw(@AuthenticationPrincipal AuthPrincipal principal,
                             @PathVariable UUID duelId) {
        return duelService.respondNegatively(principal.userId(), duelId, true);
    }
}
