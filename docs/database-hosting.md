# FitClash: Database & API Hosting Recommendation

**Date verified:** 2026-09-30. All pricing/limits below were checked against each provider's own site on this date; aggregator/blog sources are marked as such and used only to corroborate.

**Context:** solo dev, <50 users (gym friends giving feedback), bursty evening usage, long idle stretches between feedback rounds, near-zero budget. Backend has never been deployed. This is not a scale problem — it's a "stay alive and free between feedback rounds, with no data loss" problem.

---

## Question 1: Should it stay PostgreSQL?

**Yes — stay on PostgreSQL.** Verified against `backend/src/main/resources/db/schema.sql`:

- `CREATE EXTENSION pgcrypto` + `gen_random_uuid()` for every PK.
- Partial unique indexes: `uq_friend_unordered_pair`, `uq_duel_one_live_per_pair`, `idx_workouts_flagged` (`WHERE flagged_for_review`), `uq_xp_duel_payout` — all use `WHERE` predicates and the `least()`/`greatest()` functions.
- CHECK constraints with regex (`~`), `BETWEEN`, cross-column boolean logic.
- 7 tables, 19 indexes, already Hibernate-mapped under `backend/src/main/java/com/fitclash/domain/` (`User`, `Character`, `Workout`, `WorkoutLog`, `Friendship`, `Duel`, `XpEvent`).

**SQLite:** would require rewriting every partial/functional index (SQLite supports partial indexes but not `least()`/`greatest()` without a custom extension), dropping `pgcrypto` for `lower(hex(randomblob(16)))`-style UUID generation, and accepting SQLite's single-writer locking model under concurrent duel resolution and XP-ledger writes — a correctness risk even at 50 users if two devices write at once (the schema already defends against this with `characters.version` optimistic locking, which assumes real transaction isolation). **Switching cost: rewrite ~40% of schema.sql, swap Hibernate dialect, re-test every CHECK/unique constraint. Not worth it — the schema is already correct Postgres and the hosting problem (Q2) is solved without touching it.**

**Supabase (BaaS):** it *is* Postgres underneath, so the schema and JPA entities port unchanged. But adopting its auto-generated REST/GraphQL API and auth would make large parts of the already-written Spring Boot backend (JWT auth in `fitclash.jwt.*`, `StartupSafetyGuard`, the anti-cheat/XP service layer enforcing `fitclash.rules.*`) redundant. The anti-cheat logic (diminishing returns, daily caps, duel resolution) is business logic that doesn't fit cleanly into Supabase's auto-CRUD model without still writing a server — so you'd keep Spring Boot anyway and just use Supabase as a plain Postgres host. **Verdict: don't throw away the backend; if you use Supabase, use it only as a Postgres host (see Q2), ignore its API/auth features.**

**Conclusion: keep PostgreSQL, keep the Spring Boot API, solve this purely as a hosting problem.**

---

## Question 2: Where does the database get hosted?

| Provider | Free tier exists? | Storage/compute | Connections | **Sleeps / deletes on idle?** | Cold start | Card required? |
|---|---|---|---|---|---|---|
| **Neon** | Yes, permanent | 0.5 GB storage/project, 100 CU-hours/month, autoscale to 2 CU | 0.25 CU ≈ 104 max_connections direct; pooled (PgBouncer) endpoint accepts up to 10,000 client conns, multiplexed | **Scales to zero after 5 min idle (suspend, not delete). No evidence of project deletion for inactivity** — Neon's own FAQ states limits "block" usage but never delete data. | 300ms–1s to resume from suspend | **No** |
| **Supabase** | Yes | 500 MB DB, 1 GB file storage, 5 GB egress | 200 concurrent (Realtime); Postgres direct connection limit is small on free tier (shared compute) | **Pauses after 7 days of inactivity.** Data is retained but the project is completely unreachable until a human logs into the dashboard and manually un-pauses it — no API-triggered wake. For a prototype that sits idle for weeks between feedback rounds, this is a recurring manual chore (or needs a scheduled keep-alive ping, which defeats "idle"). | N/A until manually resumed (not auto) | Not confirmed either way |
| **Railway** | **No permanent free tier.** One-time $5 trial credit (30-day window), then $5/mo Hobby minimum. | N/A on free | N/A | Trial data is deleted ~30 days after credit exhaustion ("after sufficient warning") | N/A | Not required for trial, required for Hobby |
| **Render (Postgres)** | Free Postgres exists but is a trap. | Small free instance | Low | **Expires 30 days after creation, 14-day grace period, then Render permanently deletes the database and all its data.** This is the single most dangerous option in this comparison for a prototype that will sit untouched for weeks. | N/A | Not required to start |
| **Fly.io Postgres** | **No free tier at all** — removed free allowances in 2024; new accounts get only a 2-VM-hour / 7-day trial. Managed Postgres plans start at $38/mo (Basic). | N/A | N/A | N/A — pay-as-you-go only after trial | N/A | **Yes, required** |
| **Aiven** | Yes, "permanent" free plan (1 CPU, 1 GB RAM, 1 GB storage) | 1 GB storage | Not published for free tier | Aiven explicitly reserves the right to "shut down services... unused for an extended period of time" — vaguer and less reassuring than Neon's documented behavior, no fixed day-count found | Not published | Not confirmed |

**Loud callouts:**
- **Render's free Postgres deletes your data after 44 days total (30 + 14 grace) of being untouched.** Disqualifying for this project's usage pattern.
- **Supabase pauses (not deletes) after just 7 days**, but the un-pause is a manual dashboard click — not acceptable for a link you hand to friends who might try it again three weeks later and find a dead API.
- **Fly.io has no real free tier for a database anymore** — it's a paid product now.
- **Railway has no permanent free tier either** — it's a 30-day runway, not an idle-friendly home.
- **Neon is the only one that is simultaneously permanent, free, no-card, and does not delete or require manual un-pausing on idle.** Its "suspend after 5 min" is an automatic, transparent compute pause — the next query just takes under a second longer, no human action needed.

**HikariCP pool vs connection caps:** the app's default `spring.datasource.hikari.maximum-pool-size=10` (set in `backend/src/main/resources/application.properties`) is well under Neon's ~104 direct connections at the smallest compute size, so **no pool size change is required** on Neon's free tier. Recommendation: still drop `minimum-idle` to `1` (from the default `2`) in a prod override so the pool doesn't hold connections open and trigger unnecessary compute-active time against the 100 CU-hour/month budget during idle stretches — negligible at this traffic level but free to do.

**Verdict: Neon, unambiguously, for the database.**

---

## Question 3: Where does the Spring Boot API get hosted?

| Provider | Free tier reality | Sleeps? | Cold start (JVM-specific) | Memory vs JVM heap | Build method | Card required? |
|---|---|---|---|---|---|---|
| **Render** | Yes, free Web Service, 750 shared hours/month per workspace | **Spins down after 15 min of no inbound traffic**; ~1 minute to come back up on the next request | Render's own "~1 minute" spin-up is mostly container/VM boot; add JVM startup on top (Spring Boot 3.3 cold JVM start is typically 3–8s for a small app) — expect the first hit after idle to take roughly 60–90s total | No published hard number for the free instance; community reports treat it as 512MB-class | Dockerfile (full control) or native buildpack | **No** |
| **Railway** | $5 one-time trial credit, then $5/mo minimum — not a standing free tier | Trial-based, not idle-based | Normal container cold start plus JVM boot (same JVM caveat as above) | Configurable, billed by usage | Dockerfile or Nixpacks buildpack | No for trial, yes for paid |
| **Fly.io** | No free tier (2-hour/7-day trial only) | Machines can be configured to auto-stop/start, but this is a paid, metered product post-trial | Fly Machines restart is fast (sub-second to a few seconds) for the VM itself, then add JVM boot | You size the VM yourself | Dockerfile only | **Yes, required** |
| **Koyeb** | **Effectively gone for a card-free prototype**: since February 2026, signup requires a credit card with a $29 pre-authorization hold, and the default path is the $29/mo Pro plan. The nominal free 512MB/0.1vCPU instance still exists underneath but is gated behind that card requirement. | Scales to zero after 1 hour idle on the free instance tier | 1–5s container-level, plus JVM boot | 512MB, 0.1 vCPU — very tight for a JVM | Dockerfile | **Yes (new, Feb 2026 change)** |
| **Google Cloud Run** | Real "Always Free" perpetual quota: 2M requests/month, 360,000 GB-seconds, 180,000 vCPU-seconds — generous enough that this app would likely never be billed | Scales to zero with no traffic; Google keeps a warm instance up to ~15 min after last request | **This is the one to be careful about.** Unoptimized Java/Spring Boot cold starts on Cloud Run run **3–10 seconds** (JVM class loading + Spring context init), not the sub-second cold start you'd get from a Node or Go function. Mitigated by: trimming dependencies, enabling CDS (`-Xshare:on`/AppCDS), or (bigger lift) GraalVM native-image to get under 500ms — not worth the complexity for a 50-user prototype. | Default Cloud Run container is commonly 512Mi; **must** pass explicit heap flags or the JVM's default ergonomics (up to 25% of container RAM for Metaspace alone, plus a default max heap guess) can OOM-kill the container under load. | Dockerfile required (you control the JRE/JVM flags) | **Yes** — billing account/card must be linked (Feb 2026 policy), though usage stays $0 within Always Free |

**JVM heap flags needed in a 512MB container (Render or Cloud Run):** since the container has no room for the JVM's default heap guessing (which can target up to 25% of RAM for Metaspace/overhead on top of a sizeable heap), set explicitly:
```
JAVA_TOOL_OPTIONS=-Xms128m -Xmx256m -XX:MaxMetaspaceSize=96m -XX:+UseSerialGC -XX:MaxRAMPercentage=50.0
```
`UseSerialGC` matters here too — G1 (Spring Boot's/JDK's default on multi-core-looking containers) spends more memory and CPU on housekeeping than this workload needs; Serial GC is the right choice below ~1-2GB heaps.

**Verdict: Render, for a card-free prototype.** Cloud Run is the better engineering choice (true always-free quota, no risk of deletion) if a linked card is acceptable to the developer; Render avoids the card entirely at the cost of a ~60–90s wait on the first request after 15 minutes idle, which is a completely acceptable tradeoff for a handful of friends testing in the evening.

---

## Question 4: The Recommendation

### Pick: **Neon (database) + Render (API)**

**Why this pair:** both have genuinely permanent, no-credit-card free tiers; neither deletes data on idle (Neon auto-suspends compute, Render auto-sleeps the web dyno — both auto-resume on the next request, no manual dashboard click required like Supabase); both are well inside this app's real usage (under 50 users, bursty evenings, long idle stretches). This is the only pair in the comparison that is simultaneously card-free, deletion-free, and resume-without-human-intervention.

### Cost

**$0/month at this usage.** First threshold where it stops being free:
- **Neon:** exceeding 0.5 GB storage (blocks writes) or 100 CU-hours/month of active compute — at <50 bursty users this is very unlikely to be hit; if it is, Neon's Launch plan is metered at ~$0.106/CU-hour plus $0.35/GB-month storage, i.e. a few dollars, not a cliff.
- **Render:** exceeding the shared 750 hours/month across free services on the workspace (one always-idle-capable service for a prototype won't come close), or wanting to eliminate the 15-minute sleep/60-90s cold start, which requires the $7/mo Starter instance.

### Setup runbook

1. **Neon:** sign up at neon.com (no card). Create a project named `fitclash`. Note the connection details from the dashboard: host, port (5432), database name, username, password — or just copy the full pooled connection string (`postgresql://user:password@ep-xxxx-pooler.region.aws.neon.tech/fitclash?sslmode=require`).
2. **Render:** sign up at render.com (no card). New → Web Service → connect the `rickh5502/fitclash` GitHub repo, root directory `backend/`, environment = Docker (write a simple multi-stage Dockerfile: `eclipse-temurin:17-jdk-alpine` build stage running `./mvnw clean package -DskipTests`, then `eclipse-temurin:17-jre-alpine` runtime stage running `java $JAVA_TOOL_OPTIONS -jar app.jar`) — **there is no Dockerfile in the repo today; this must be added**, which is new work, not a config change.
3. **Environment variables to set on Render** (confirmed by reading `backend/src/main/resources/application.properties` and `application-prod.properties` — these are the actual property placeholders, not guesses):
   - `SPRING_PROFILES_ACTIVE=prod` — required to activate `application-prod.properties`, which removes the committed dev-default secrets, and to satisfy `StartupSafetyGuard` (`backend/src/main/java/com/fitclash/config/StartupSafetyGuard.java`), which throws `IllegalStateException` at boot under any non-local profile if `JWT_SECRET` or `DB_PASSWORD` still equal the known committed defaults (`dev-only-fitclash-secret-please-rotate-me-0123456789` / `postgres`).
   - `JWT_SECRET` — any random string ≥32 bytes, must NOT equal the committed default above.
   - `DB_PASSWORD` — the Neon database password, must NOT equal `postgres`.
   - `DB_HOST` — the Neon pooled hostname, e.g. `ep-xxxx-pooler.region.aws.neon.tech`.
   - `DB_PORT` — `5432`.
   - `DB_NAME` — `fitclash` (or whatever the Neon project's default DB is named).
   - `DB_USER` — the Neon username.
   - `JAVA_TOOL_OPTIONS` — the heap flags above.
   - `CORS_ORIGINS` — must be updated from the dev default (`http://localhost:3000,...`) to `https://rickh5502.github.io` so the live GitHub Pages frontend is allowed to call the API.
   - `SERVER_PORT` has a working default (`8080`); Render auto-detects the port, no change needed.
   - **Neon's connection string requires SSL** (`sslmode=require`); `spring.datasource.url` is built in `application.properties` as `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}` with no `sslmode` parameter — **this needs a one-line addition** (`?sslmode=require`) to the datasource URL template in `application.properties`, or pass it via a new `DB_SSL_PARAMS` env var appended to the URL. This is the one concrete code change required beyond env vars.
4. Point the GitHub Pages frontend's API base URL at the Render service's `https://<service>.onrender.com` URL — a frontend config change, not covered by this file.

### What breaks / needs changing in existing code
- **No Dockerfile exists yet** in `backend/` — must be written.
- **`application.properties`'s datasource URL has no `sslmode` parameter** — Neon requires SSL; this is a one-line change.
- Nothing else in the Spring Boot config needs to change — `application-prod.properties` and `StartupSafetyGuard` were clearly designed with exactly this kind of deployment in mind (fail fast on missing/default secrets), and `ddl-auto=update` means the first boot against the empty Neon database will create the schema automatically from the JPA entities (acceptable for a prototype; `schema.sql` remains available as the source of truth if `ddl-auto` is later turned off).

### Second choice

If Render has a signup problem (account flagged, region unavailable, etc.), fall back to **Google Cloud Run** for the API, keeping Neon for the database. Cloud Run's Always Free quota (2M requests, 360K GB-seconds, 180K vCPU-seconds/month) comfortably covers this traffic and never auto-sleeps-with-deletion risk, but it requires linking a credit card to the Google Cloud billing account (no charge expected, but the card is mandatory as of the Feb 2026 policy change) and demands more deployment ceremony (gcloud CLI, Artifact Registry, Dockerfile with explicit JVM heap flags to survive the 512Mi-class default container) than Render's git-push-to-deploy flow. Expect a materially worse cold start on the first request after idle (3-10s of JVM/Spring context boot inside the container, on top of Cloud Run's own cold-start overhead) unless AppCDS or GraalVM native-image work is done, which is not justified at this scale.

---

---

## Addendum: MongoDB Atlas (owner has a cluster already)

The owner has an Atlas cluster available and asked for an honest evaluation, explicitly citing scalability as the reason it might be worth a switch. Evaluated on the merits, not availability.

**1. Migration cost, counted, not estimated.** All 7 entities (`User`, `Character`, `Workout`, `WorkoutLog`, `Friendship`, `Duel`, `XpEvent` — 1,214 lines) would become `@Document`s and all 7 repositories would become `MongoRepository`s. More importantly: `backend/src/main/java/com/fitclash/repo/` contains **18 `@Query` methods using JPQL** across 6 of the 7 repositories (`DuelRepository` 4, `WorkoutRepository` 4, `FriendshipRepository` 3, `CharacterRepository` 3, `WorkoutLogRepository` 2, `XpEventRepository` 2) — joins (`join fetch c.user`), aggregate sums for the daily XP cap, and the leaderboard/duel-metric queries. None of this JPQL survives; each becomes a MongoDB aggregation pipeline, hand-written. This is a full backend rewrite, not a swap — realistically a bigger effort than the original backend build.

**2. What is actually lost — this is the real issue.** `schema.sql` doesn't just store data, it *enforces the game's integrity*: CHECK constraints block a 500kg×1000-rep set at the database layer regardless of what the application code does; the partial unique index `uq_duel_one_live_per_pair` guarantees one live duel per pair; `uq_xp_duel_payout` guarantees a duel pays out exactly once no matter how many times the resolver runs; foreign keys guarantee no `Character` or `WorkoutLog` can exist without a `User`. MongoDB's schema validation can approximate the CHECK constraints (with more verbose JSON Schema rules), but it has **no equivalent of a partial unique index enforced atomically at the storage layer**, and **no foreign keys** — referential integrity and the "exactly once" and "one live duel" guarantees would all move into application code, enforced only by whatever the current code path remembers to check. For an app whose entire premise is that users cannot cheat the game, moving those guarantees from "impossible regardless of code bugs" to "correct as long as nobody writes a buggy endpoint" is a real regression, not a neutral tradeoff.

**3. The scalability claim, answered directly.** This data shape does not benefit from documents: it's 1:1 User–Character (a join, not a nested document relationship worth denormalizing), a self-referential many-to-many friendship graph (relational by nature), duels referencing two users with a resolver sweep query, and an append-only ledger that gets summed per-user-per-day — i.e., relational joins and aggregations, exactly what Postgres is built for and exactly what fights MongoDB's single-document-locality model. A single small Postgres instance comfortably handles tens of thousands of active users doing this kind of workload before becoming the bottleneck; at 50 users it is not within three orders of magnitude of mattering. There is no scalability case here — the data shape is the "join-heavy, invariant-heavy" shape that is Postgres's reason to exist, not Mongo's.

**4. The honest upside.** Atlas M0 is a genuine, permanent, forever-free tier the owner already has provisioned, which removes the one signup step from the Neon recommendation above. Verified: **M0 auto-pauses after 30 days of zero connections, but does not delete data** — it stays paused indefinitely until resumed, which is better than Render's Postgres (hard delete) but worse than Neon's (auto-resume on next query, no human needed) since an Atlas M0 resume typically needs a dashboard click. That convenience (one less signup) does not come close to offsetting a full backend rewrite plus a real loss of DB-enforced anti-cheat guarantees.

**Verdict: keep PostgreSQL.** The schema, the 18 hand-written JPQL queries, and the CHECK/FK/partial-index invariants are already correct and already paid for. Switch this call only if the owner later needs: (a) genuinely unstructured or per-user-variable document shapes (not the case here — the schema is stable and relational), or (b) a write-heavy workload at a scale where Postgres has been *measured* to be the bottleneck (not plausible below thousands of concurrent users for this workload). Neither applies today.

---

## What could not be fully verified
- Supabase's exact free-tier Postgres direct connection limit (only the 200-connection Realtime figure was confirmed from the pricing page).
- Whether Supabase, Aiven, or Fly.io require a credit card at signup — official pages did not state this explicitly in the fetched content; third-party sources were mixed or silent.
- The exact memory ceiling (MB) for Render's free Web Service tier — Render's own docs did not state a hard number in the fetched page; community/blog sources consistently describe it as suitable for small JVM apps with explicit heap flags, which is the operating assumption used above.
- Aiven's "unused for an extended period" shutdown policy has no published fixed day-count, unlike Neon's and Supabase's documented numbers — treated as a soft disqualifier rather than a hard one.
