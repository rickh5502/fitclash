// File: src/main/java/com/fitclash/security/AuthRateLimitFilter.java
package com.fitclash.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cheap abuse guard for the two endpoints that have no auth of their own:
 * {@code /api/auth/login} and {@code /api/auth/register}. Without this, both
 * are open to unlimited-speed credential stuffing / password guessing and to
 * registration spam, from a single caller.
 *
 * This is an in-memory, per-process, per-client-IP fixed window. It is not a
 * substitute for a real rate limiter (Redis-backed, shared across instances,
 * aware of proxies) in a real deployment - it is the cheapest thing that
 * meaningfully raises the cost of a brute-force loop from a single machine
 * for a prototype that runs as one instance. See docs/security-review.md.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS = 10;
    private static final long WINDOW_MILLIS = 60_000L;

    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return !(uri.equals("/api/auth/login") || uri.equals("/api/auth/register"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = clientKey(request);
        long now = Instant.now().toEpochMilli();

        Deque<Long> window = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        boolean limited;
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MILLIS) {
                window.pollFirst();
            }
            limited = window.size() >= MAX_REQUESTS;
            if (!limited) {
                window.addLast(now);
            }
        }

        if (limited) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"error\":\"rate_limited\",\"message\":"
                    + "\"Too many attempts. Try again in a minute.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    /** Best-effort client identity: trust X-Forwarded-For only as a hint, never alone. */
    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String remote = request.getRemoteAddr();
        if (forwarded != null && !forwarded.isBlank()) {
            // The first hop is attacker-controlled when there is no trusted proxy in
            // front of this app, so it is combined with (not substituted for) the
            // socket address rather than trusted on its own.
            return remote + "|" + forwarded.split(",")[0].trim();
        }
        return remote;
    }
}
