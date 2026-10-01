# FitClash security review — pre-public-release

Date: 2026-09-30
Scope: full repo + git history, ahead of making the GitHub repository public.
Context: this is a prototype with no production deployment and no real user
data. Severities below are weighted for that — a problem that would be
Critical in a live service with real accounts is marked lower here when the
actual, current blast radius is "a contributor's localhost Postgres."

Legend: **Fixed** = changed in this pass. **Documented** = real finding,
left as a recommendation (either out of this agent's file ownership, or a
deliberate judgment call not to touch for a prototype). **Dismissed** =
investigated and found not to be a reachable issue.

---

## Critical

None. Nothing in this codebase, as committed, gives an attacker direct
access to real user data or a live production system, because there is no
live production system. The one issue that *would* be critical the moment a
real deployment exists is handled below under High.

---

## High

### H1 — Hardcoded JWT signing secret shipped as a working default
**File:** `backend/src/main/resources/application.properties:50` (pre-fix)
**Attack:** Anyone who reads the public repo gets
`dev-only-fitclash-secret-please-rotate-me-0123456789`. If any real
deployment is ever started with `JWT_SECRET` unset — a copy-paste deploy, a
Docker image built without env wiring, a forgotten `.env` — that
deployment signs tokens with a key the whole internet already has. The
attacker crafts `Jwts.builder().subject(anyUserId).signWith(thatKey)...` and
has a valid bearer token for any account, including ones they don't control.
**Severity:** High. Not Critical only because there is currently no reachable
deployment for this to apply to — but it is a single missed env var away
from being a full auth bypass, which is why it gets fixed rather than just
documented.
**Fix — applied:**
- `application.properties` keeps the dev default, but is now explicitly
  documented as the local/dev profile only.
- Added `backend/src/main/resources/application-prod.properties`, activated
  with `SPRING_PROFILES_ACTIVE=prod`, which sets
  `fitclash.jwt.secret=${JWT_SECRET}` and `spring.datasource.password=${DB_PASSWORD}`
  with **no fallback**. Spring refuses to start ("Could not resolve
  placeholder") if those env vars are missing under that profile.
- Added `backend/src/main/java/com/fitclash/config/StartupSafetyGuard.java`,
  a `@PostConstruct` check that independently refuses to finish starting
  under any profile other than `default`/`local`/`dev`/`test` if the
  resolved JWT secret or DB password still equal the known committed
  defaults — so misconfiguration (e.g. `application-prod.properties` edited
  to add a fallback again, or the profile set without that file present)
  still fails loudly instead of booting with a public key.
- Contributors on localhost are unaffected: no profile set → `default`
  profile → same one-command `./mvnw spring-boot:run` as before.

### H2 — DB password default, same shape as H1, lower severity
**File:** `backend/src/main/resources/application.properties:19` (pre-fix)
**Attack:** Same mechanism as H1, but the asset at risk is direct DB access
rather than application-level auth bypass, and it additionally requires the
DB port to be reachable from the attacker (not true for `localhost:5432`
behind a NAT/firewall in virtually all real deployments, unlike a public
HTTP API). Scored as High-adjacent but filed as part of H1 because the fix
is identical and was applied in the same change.
**Fix — applied:** same `application-prod.properties` + `StartupSafetyGuard`
as H1.

---

## Medium

### M1 — No rate limiting on `/api/auth/login` and `/api/auth/register`
**File:** `backend/src/main/java/com/fitclash/web/AuthController.java`
**Attack:** An attacker with a target username (trivially discoverable —
usernames are shown everywhere: leaderboard, duels, friend search) sends
unlimited `POST /api/auth/login` requests at whatever speed their client can
manage, since bcrypt is the only cost on a wrong guess and there was no
request-level throttle before this pass. For a weak or reused password this
is a workable (if bcrypt-slowed) credential-stuffing / brute-force path.
`/api/auth/register` has the same exposure for registration spam /
username squatting.
**Severity:** Medium. BCrypt(strength 12) already imposes real per-guess
cost, and this is a prototype with no real credentials at stake, but the
fix was cheap enough to just do.
**Fix — applied:**
`backend/src/main/java/com/fitclash/security/AuthRateLimitFilter.java` — an
in-memory, per-client-IP fixed window (10 requests/60s) in front of exactly
those two endpoints, wired into `SecurityConfig` ahead of `JwtAuthFilter`.
Honest limitation, stated here rather than hidden: this is per-process
memory, so it resets on restart and does not share state across multiple
instances behind a load balancer. That's an acceptable gap for a
single-instance prototype; a real multi-instance deployment needs a shared
store (Redis `INCR`+`EXPIRE`, or an API-gateway-level limiter) instead.

### M2 — Login timing reveals account existence
**File:** `backend/src/main/java/com/fitclash/service/AuthService.java`
**Attack:** `login()` returned "Wrong username or password" for both an
unknown identifier and a known one with a wrong password — same message,
but the unknown-identifier path returned immediately while the known-user
path spent bcrypt's ~tens-of-milliseconds verifying the hash. An attacker
timing responses (even over a network, bcrypt's cost is large enough to
show up against jitter) can therefore enumerate valid usernames/emails
despite the identical error text.
**Fix — applied:** when no account is found, `AuthService.login()` now runs
`passwordEncoder.matches(...)` against a fixed dummy bcrypt hash before
throwing, so both branches pay the same bcrypt cost.

### M3 — Email enumeration via distinct registration error messages
**File:** `backend/src/main/java/com/fitclash/service/AuthService.java:53-58`
**Attack:** `POST /api/auth/register` returns `"That username is taken."`
vs. `"That email already has an account."` as distinct 409 messages. An
attacker can submit a guessed email with a throwaway username to learn
whether that email has a FitClash account.
**Severity:** Medium-Low. This is a common, usually-accepted UX/security
tradeoff (most consumer signup flows do this because the alternative is
worse UX), and FitClash has no password-reset-by-email flow today that
would turn this into something more serious.
**Status:** Documented, not changed. Collapsing both into one generic
"That username or email is already in use" message is the standard fix if
this ever needs to be fully closed; left alone here because it's a
judgment call on UX vs. a low-impact leak, not a clear bug.

### M4 — `server.error.include-message=always`
**File:** `backend/src/main/resources/application.properties:11` (pre-fix)
**Attack:** `ApiExceptionHandler` catches every exception the controllers
can throw and returns a safe, controlled message — so this setting had no
effect for anything that reaches a controller method. It *would* have
leaked a raw exception message (not a stack trace — `include-stacktrace`
defaults to `never` and was untouched) for a request that fails before
dispatch, such as `HttpMessageNotReadableException` from malformed JSON, if
Spring's default `/error` path were ever hit instead of the
`@RestControllerAdvice`. Low actual exposure (message text only, not
stack/SQL/paths), but there's no reason to leave the door open.
**Fix — applied:** `server.error.include-message` and
`server.error.include-binding-errors` both set to `never`. All real error
responses are already shaped entirely by `ApiExceptionHandler`.

---

## Low

### L1 — GitHub Actions pinned by tag, not SHA
**File:** `.github/workflows/pages.yml`
**Attack:** `actions/checkout@v4`, `actions/setup-node@v4`,
`actions/configure-pages@v5`, `actions/upload-pages-artifact@v3`,
`actions/deploy-pages@v4` are pinned to mutable version tags. If one of
these official actions were ever compromised upstream (tag re-pointed to
malicious code), the workflow would pull it automatically on the next run.
**Severity:** Low. These are first-party GitHub actions, not third-party
marketplace actions, and the workflow already has minimal `permissions:`
(`contents: read`, `pages: write`, `id-token: write` — no
`actions: write`, no PAT). The realistic risk here is low for a static-site
deploy pipeline with no secrets beyond the Pages OIDC token.
**Status:** Documented, not changed — pinning to a 40-character commit SHA
for every action is the standard hardening if this is worth doing, but it's
a maintenance cost (manual bumps) that doesn't obviously pay for itself on
a workflow this narrow. Flagging it rather than silently skipping it.

### L2 — No step timeout on CI jobs
**File:** `.github/workflows/pages.yml`
**Attack:** none directly — this is a hygiene/cost item, not a
vulnerability. A hung `npm ci`/build would run until GitHub's default job
timeout (6 hours) rather than failing fast.
**Status:** Documented, not changed; out of scope for a security pass.

---

## Considered and dismissed (no real attack found)

- **`frontend/tools/preview.preamble.jsx` uses `dangerouslySetInnerHTML`.**
  Every call site passes a hardcoded literal SVG path string (`icon('Dumbbell',
  '<path d="..."/>')` etc. — see lines 28–44). None of these strings come
  from user input, a URL parameter, or any runtime data; they're baked into
  the build. `dangerouslySetInnerHTML` here is exactly as safe as writing
  the SVG markup directly in JSX — it's a workaround for the preview shim
  not having access to the icon library's `React.createElement` tree, not a
  user-data sink. Not a finding.
- **`frontend/src/lib/api.js` keeps the JWT in a module-level variable.**
  This is already the right call, and the file says so in its own header
  comment. A variable is immune to the classic "read `localStorage` via
  injected `<script>`" XSS pattern; the real production answer (also
  already written in the comment) is an `httpOnly` cookie set by the
  backend so client-side JS never touches the token at all, which also
  survives a full-page reload without a re-login — something the in-memory
  approach does not do. Not a finding; the existing design and its own
  documented caveat are both correct. Flagging the production-cookie
  migration as the next step if/when a real backend goes live, not as a
  bug today.
- **React escaping bypass for user-rendered text (usernames, exercise
  names, duel notes).** Grepped `FitClash.jsx` for `dangerouslySetInnerHTML`,
  `innerHTML`, `eval(`, `new Function(` — zero matches. All user-supplied
  strings go through normal JSX text interpolation, which React escapes by
  default. Not a finding.
- **CORS `allowCredentials(true)`.** `SecurityConfig.corsConfigurationSource()`
  and `CorsConfig` both set `allowedOrigins` from a fixed, server-side
  property list (`fitclash.cors.allowed-origins`), never from reflecting the
  request's `Origin` header. Spring Security also rejects `allowCredentials(true)`
  combined with a wildcard origin at startup, so a wildcard-with-credentials
  misconfiguration isn't even deployable by accident. No header can widen
  the origin list — it's a fixed Java `List<String>` from one property. Not
  a finding.
- **CSRF disabled.** Correct for this app: `SessionCreationPolicy.STATELESS`,
  `formLogin` and `httpBasic` both disabled, and auth is a bearer token read
  from the `Authorization` header by `JwtAuthFilter` — never from a cookie.
  There is no cookie-based credential a cross-site form could silently
  attach, which is the entire premise CSRF protection exists to cover. Not
  a finding.
- **JWT algorithm confusion / missing expiry / issuer checks.**
  `JwtService.verify()` uses `Jwts.parser().verifyWith(key)...requireIssuer(issuer)`.
  jjwt 0.12's `verifyWith(SecretKey)` binds the parser to that specific key
  and algorithm family — there is no `alg: none` or RS256-vs-HS256 confusion
  surface here, unlike libraries that trust an `alg` header the token
  itself supplies. Expiration is checked by default by `parseSignedClaims`
  (throws `ExpiredJwtException`, caught and treated as invalid). Any
  malformed/forged/expired token hits the generic `catch (JwtException |
  IllegalArgumentException)` and returns `null` → 401. Not a finding.
- **IDOR across Duel/Friendship/Workout.** Every service method takes the
  caller's own id from the JWT principal (never from the request body or
  path for "whose account is this") and separately validates ownership of
  the *target* resource before acting:
  - `DuelService.load()` throws 403 unless `duel.involves(userId)`.
  - `DuelService.accept()` additionally checks the caller is the opponent;
    `respondNegatively()` checks challenger-vs-opponent depending on
    cancel/decline.
  - `DuelService.challenge()` requires `friendships.areFriends(...)` before
    a duel can even be created against someone else's id.
  - `SocialService.respond()` throws 403 unless the caller is the
    friendship's addressee.
  - `WorkoutService`/`SocialService.me()` always resolve the character via
    the JWT's own `userId`, never a path/body id — there is no
    "get workout/character by id" endpoint for someone else's data at all.
  No path found where a caller can act on or read another user's duel,
  friendship, or workout/character by supplying their UUID. Not a finding.
- **Mass assignment (client sets its own XP/level/stats/duel outcome).**
  All DTOs in `Dtos.java` are closed records with only user-input fields
  (`exerciseName`, `kind`, `reps`, `weightKg`, `durationSec`, `intensity`,
  `opponentId`, `metric`, `stakeXp`, `durationDays`, etc.). There is no
  `xp`, `level`, `str`/`sta`/`con`, or `outcome` field on any inbound
  request type — those are always server-computed in `GamificationService`
  / `WorkoutService` / `DuelService` from validated inputs, never
  deserialized from the client. Not a finding.
- **SQL/JPQL injection.** Every `@Query` across all six repositories uses
  named parameters (`:userId`, `:day`, `:slug`, etc.) bound via
  `@Param`/method-parameter-name binding — grepped for `nativeQuery` and
  string concatenation into any query string, zero hits. The one place a
  user-supplied string (`exerciseName`) reaches a query, it has already
  been through `Formulas.slug()`, which maps it to `[a-z0-9-]*` before use.
  Not a finding.
- **Dependency CVEs — Spring Boot 3.3.4 / jjwt 0.12.6.** No known advisory
  applicable to this app's usage was found for either pinned version as of
  this review. (Spring Framework's 2025 CVEs that postdate 3.3.4 are mostly
  about actuator/management endpoints and specific MVC path-matching
  configurations not used here; this app exposes no actuator endpoints and
  uses straightforward `@RequestMapping` patterns.) Worth re-checking
  against the live advisory database at actual deploy time rather than
  trusting this snapshot indefinitely — dependency versions age.
- **BCrypt strength.** `BCryptPasswordEncoder(12)` — strength 12 is the
  current reasonable default (10 is the library default; 12 is a sensible,
  not excessive, step up). Not a finding.
- **Secrets in git history.** Only one commit exists. `git log -p --all`
  grepped for secret-shaped strings, emails, internal hostnames — the only
  hits are the already-known `dev-only-fitclash-secret...` /
  `DB_PASSWORD:postgres` defaults (covered above) and ordinary code tokens
  (`PasswordEncoder`, `UsernamePasswordAuthenticationToken`, etc. — library
  class names, not secrets). No `.env` file, API key, or real email address
  is committed. `.gitignore` already excludes `.env`, `.env.local`, `*.local`,
  and the machine-specific `.claude/settings.local.json`. Not a finding.
- **GitHub Actions script injection via `${{ }}` in `run:` blocks.**
  The only interpolation inside a `run:` step is
  `BASE_PATH: /${{ github.event.repository.name }}/`, and it's injected as
  an **environment variable**, not spliced into the shell command text —
  `run: npm run build` never contains the `${{ }}` expression itself, so
  there's no shell-metacharacter injection surface even if the value were
  attacker-influenced. It isn't: `repository.name` is a repo-level property
  the pusher doesn't control per-run (unlike `github.event.pull_request.title`
  or a branch name, the classic injection vectors). The workflow also only
  triggers on `push` to `main` and `workflow_dispatch` — no `pull_request_target`,
  no untrusted-fork PR trigger. Not a finding.
- **Pages deployment scope.** `upload-pages-artifact@v3` is given
  `path: frontend/dist` explicitly — only the Vite build output is
  uploaded, nothing else in the repo (no `backend/`, no `docs/`, no `.git`)
  can reach the published site. Not a finding.
- **npm supply chain / `npm audit`.** See the dedicated section below.

---

## `npm audit`

```
npm audit --omit=dev   → 0 vulnerabilities
npm audit (full)       → 5 vulnerabilities (3 moderate, 1 high, 1 critical) — all in vite/esbuild/@vitest/mocker
```

All five findings are in the Vite/Vitest dev-server toolchain
(`esbuild <=0.24.2`, `vite <=6.4.2`, `@vitest/mocker <=4.1.10`), which:
- are **devDependencies only** (`--omit=dev` confirms zero production
  vulnerabilities),
- describe the Vite **dev server** accepting arbitrary requests from any
  website while `npm run dev` is running locally, and a path-traversal bug
  in Vitest's module mocker during test runs,
- never ship into `frontend/dist`, which is a static bundle of
  framer-motion/lucide-react/React output with no dev server, no build
  tooling, and no mocker code in it at all.

This is real dev-time noise, not a production finding: the deployed
artifact is a static site on GitHub Pages with none of these packages
present. **Not fixed** — `npm audit fix --force` would force
`vitest@4.1.11`/`vite@8` major-version bumps, which risk breaking the
Vitest config and the 16 passing tests for a vulnerability class
(local dev server request forgery) that only matters on a developer's own
machine while `npm run dev` is open to the local network. Recommend
revisiting next time the Vite/Vitest major version is bumped intentionally,
with the full test suite re-run alongside it.

---

## What stays correct as-is (confirmed, not changed)

- `WorkoutService`/`AntiCheatService`: XP/stat computation is entirely
  server-side, re-derives diminishing-returns indices from the database
  plus request position (so splitting one call into many produces identical
  totals), and the daily cap is a ledger `SUM`, not a mutable counter. No
  path lets a client dictate its own XP, stats, or duel outcome.
  `AuthController`/`DuelController`/`SocialController`/`WorkoutController`
  all resolve the acting user from `@AuthenticationPrincipal`, never from
  the request body.
- `SecurityConfig`: `/api/leaderboard` (GET only) and `/api/auth/**` are the
  only public surface, matching the stated design; everything else is
  `.authenticated()`. Pre-flight `OPTIONS` is explicitly left open so CORS
  works, which is correct and standard.

---

## Summary of changes made in this pass

| File | Change |
|---|---|
| `backend/src/main/resources/application.properties` | Documented default-profile-only status of the secrets; `server.error.include-message`/`include-binding-errors` → `never` |
| `backend/src/main/resources/application-prod.properties` | **New.** No-fallback `JWT_SECRET`/`DB_PASSWORD` for any non-local deploy |
| `backend/src/main/java/com/fitclash/config/StartupSafetyGuard.java` | **New.** Refuses to boot under a non-local profile if either secret still matches the committed default |
| `backend/src/main/java/com/fitclash/security/AuthRateLimitFilter.java` | **New.** Per-IP fixed-window rate limit (10/min) on `/api/auth/login` and `/api/auth/register` |
| `backend/src/main/java/com/fitclash/config/SecurityConfig.java` | Wired `AuthRateLimitFilter` into the chain ahead of `JwtAuthFilter` |
| `backend/src/main/java/com/fitclash/service/AuthService.java` | Login now burns a dummy bcrypt comparison on "account not found" to close the timing side-channel |

Verified after changes: backend 21/21 tests pass (`javac --release 17` +
JUnit Platform Console Standalone), frontend 16/16 tests pass
(`npx vitest run`).
