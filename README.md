# FitClash

A gamified fitness tracker. Friends log gym sessions, their sessions turn into RPG
character stats, and they settle it in 1v1 duels.

Spring Boot 3 + PostgreSQL on the back, React + Tailwind + Framer Motion on the front.

```
fitclash/
├── backend/                       Java 17 · Spring Boot 3.3 · Spring Data JPA · JWT
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/fitclash/
│       │   ├── config/            CORS, security chain, tunable game rules
│       │   ├── security/          JWT mint/verify + the auth filter
│       │   ├── domain/            User, Character, Workout, WorkoutLog, Duel, Friendship, XpEvent
│       │   ├── repo/              Spring Data repositories
│       │   ├── service/           Formulas, AntiCheat, Gamification, Workout, Duel, Auth, Social
│       │   └── web/               REST controllers, DTOs, error handling
│       ├── main/resources/
│       │   ├── application.properties
│       │   └── db/schema.sql      Canonical DDL
│       └── test/java/…/GameMathTest.java   The balance, pinned as tests
└── frontend/
    ├── src/FitClash.jsx           The whole UI, one component, mock state
    ├── src/lib/api.js             The live API client, for when you wire it up
    ├── preview/index.html         No-build playable build of the same source
    └── tools/build-preview.mjs    Regenerates preview/index.html
```

## Running it

### Database

```bash
createdb fitclash
psql -d fitclash -f backend/src/main/resources/db/schema.sql   # optional, see note
```

`schema.sql` is the canonical DDL. The app ships with `ddl-auto=update` so it boots
against an empty database with no manual step. To run script-first instead, set
`spring.sql.init.mode=always` and `spring.jpa.hibernate.ddl-auto=validate`.

### Backend

```bash
cd backend
./mvnw spring-boot:run          # or: mvn spring-boot:run
curl localhost:8080/api/health
```

Requires **JDK 17 or newer** and Maven. Every setting has a local default; override
with `DB_HOST`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ORIGINS`.
The JWT secret must be at least 32 bytes — the app refuses to start otherwise,
which is the cheapest place to catch that mistake.

### Frontend

```bash
cd frontend
npm install
npm run dev                     # http://localhost:3000 - the origin CORS allows
```

Or, with no toolchain at all, open `frontend/preview/index.html` in a browser.
It is the same component source with a different module preamble; regenerate it
with `node tools/build-preview.mjs`.

The UI runs on in-memory mock data out of the box. To go live, swap the mutations
in `bankSession`, `challenge`, `respond` and `settle` for the calls in `src/lib/api.js`.

## The rules

### XP

| | |
|---|---|
| Strength / bodyweight set | `volume ÷ 60` XP, where `volume = weight × reps` (bodyweight reps carry a 35 kg nominal load) |
| Cardio block | `minutes × 3.0 × (intensity ÷ 5)` XP |
| Diminishing returns | Sets 1–5 of a movement per day pay full. Set *n* after that pays `0.5^(n-5)`, floored at 0.03125 |
| Daily cap | 1,000 XP across everything, applied as one proportional scale over the session |
| Levelling | XP to go from level *N* to *N+1* = `⌊100 × N^1.5⌋` |

Ten sets of one movement in a day are worth 5.97 full sets. That is the anti-grind
mechanism: junk volume costs time and pays nothing.

### Stats

| Stat | Formula | Reads |
|---|---|---|
| **STR** | `5 + ⌊8 × log₁₀(1 + strPoints)⌋` | `strPoints` += tonnes lifted, + ½ point per kg of new e1RM best |
| **STA** | `5 + ⌊8 × log₁₀(1 + staPoints)⌋` | `staPoints` += `(minutes × intensity) ÷ 10`, + a bonus for sets over 11 reps |
| **CON** | `5 + ⌊25 × (activeDays₃₀ ÷ 30)^1.2⌋ + min(10, streak ÷ 3)` | A rolling 30-day window |

STR and STA are logarithmic on purpose: twenty times the tonnage buys ten points,
not two hundred. CON is the only stat that falls — it is the one that measures
whether the app is working.

Stat points are scaled by the same diminishing-returns multipliers and the same
daily cap as XP, so volume that earned no XP cannot quietly inflate STR either.

### Integrity (anti-cheat)

Two tiers. **Reject** means nothing is persisted and no XP moves (HTTP 422, one
reason per offending set). **Flag** means the set is logged and paid, but marked
unverified and excluded from duel scoring and competitive tie-breaks.

| Rejected | |
|---|---|
| Single-set volume | > 5,000 kg |
| Absolute load | > 500 kg |
| Reps in one set | > 150 |
| Per-lift ceilings | bench 350 · squat 500 · deadlift 550 · OHP 250 · row 300 · curl 120 · leg press 800 kg |
| Sets of one movement per day | > 25 |
| Sets per session | > 120 |
| Sessions per day | > 6 |
| Dates | future, or backdated more than 14 days |

| Flagged | |
|---|---|
| Load ≥ 75% of that lift's ceiling | Near-elite, unverified |
| Single-set volume ≥ 3,750 kg | Unusual |
| e1RM more than 25% over the athlete's own best | Sudden jump |

A hard wall at "elite" punishes the strongest honest users; a soft flag costs a
cheater the only thing they were after. Every threshold is also a `CHECK`
constraint in `schema.sql`, so no future endpoint can write around the service.

### Duels

One metric (`TOTAL_XP`, `TOTAL_VOLUME`, `TOTAL_SETS`, `ACTIVE_DAYS`), one window of
whole calendar days, accepted friends only, one live duel per pair. Flagged
sessions score nothing.

Victory goes to the higher score. Ties break on, in order: **active days** in the
window → **total volume** → **CON**. Still level after all three: a draw, and both
sides take the consolation payout.

Winner takes `min(300, 150 + stake + 10% of the margin)`. Loser takes 25.
A draw pays `25 + stake ÷ 2` each. Duel XP is exempt from the daily cap — it was
earned in the arena, not the gym — but only **3 rated duels settle per week**, and
duels past that quota run and resolve while paying nothing. Payouts are keyed on
`(user, duel, source)` in the ledger, so the resolver can run a thousand times and
pay once.

## API

| Method | Path | Auth |
|---|---|---|
| `POST` | `/api/auth/register` | open |
| `POST` | `/api/auth/login` | open |
| `GET` | `/api/health` | open |
| `GET` | `/api/leaderboard?limit=25` | open |
| `GET` | `/api/characters/me` | bearer |
| `POST` | `/api/workouts` | bearer |
| `GET` | `/api/workouts` | bearer |
| `GET` | `/api/workouts/daily-budget` | bearer |
| `POST` | `/api/duels` | bearer |
| `GET` | `/api/duels`, `/api/duels/{id}` | bearer |
| `POST` | `/api/duels/{id}/accept` · `/decline` · `/withdraw` | bearer |
| `GET` | `/api/friends`, `/api/friends/search?q=` | bearer |
| `POST` | `/api/friends/requests` · `/requests/{id}/accept` · `/decline` | bearer |

`POST /api/workouts` returns the whole result of the session in one round trip —
per-set XP with its multiplier, the cap state, the stat deltas, any level-ups and
the new character sheet — so the client never has to refetch to animate.

```jsonc
// POST /api/workouts
{
  "workoutDate": "2026-09-21",
  "title": "Push day",
  "sets": [
    { "exerciseName": "Bench Press", "kind": "STRENGTH", "reps": 8, "weightKg": 80, "durationSec": 0, "intensity": 5 },
    { "exerciseName": "Run",         "kind": "CARDIO",   "reps": 0, "weightKg": 0,  "durationSec": 1800, "intensity": 6 }
  ]
}
```

## Notes for whoever picks this up

- **`Character` shadows `java.lang.Character`** inside `com.fitclash.domain`. That is
  deliberate — the domain language says "character" — and every other package imports
  it explicitly. Do not use boxed `char` in that package.
- **XP is a ledger, not a counter.** The daily cap is a `SUM` over `xp_events` for the
  day. A counter on the character drifts; a ledger replays and audits.
- **Submitting 10 sets in one call and 10 calls of one set must produce identical XP.**
  That is why the diminishing-returns index is read from the database plus the
  position within the request, and why the cap is applied once at the end as a
  proportional scale. If the client can choose its own reward, the game is over.
- **`frontend/src/FitClash.jsx` and `backend/…/service/Formulas.java` are the same
  formulas twice.** They are cross-checked by `GameMathTest` on the Java side; if you
  change one, change both or the optimistic preview starts lying.
- Optimistic locking (`@Version` on `Character`) turns a two-device race into a 409
  the client can retry, rather than a silently lost XP grant.
