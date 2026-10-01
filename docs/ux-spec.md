# FitClash UX Spec — Simple, Competitive, Sticky

Owner's brief, restated: make it simple and un-intimidating, make it feel competitive
and alive, make people come back tomorrow. Today the app does the opposite of
"simple" on day one — see the Central Tension section — and does the opposite of
"competitive" everywhere else: five motionless NPCs, a static table, and duels you
settle by pressing a labeled "(demo)" button.

Everything below is written against the real component tree in
`frontend/src/FitClash.jsx` so the implementing agent can go straight to a name:
`AuthScreen`, `Dashboard`, `Logger`, `DuelsHub`, `Leaderboard`, `StatCard`,
`DailyBudget`, `XpBar`, `Toasts`, `LevelUpOverlay`, `TABS`, and the state verbs
`bankSession`, `challenge`, `respond`, `settle`. All data is in-memory mock state
(`RIVALS`, `SEED_DUELS`, `INITIAL_HERO`) — there is no backend wired, so every
"live" or "social" effect below is simulated client-side with `setInterval`/
`setTimeout` and said to be simulated. No push, no email, no real server.

---

## TOP 5 — build these in one sitting, in this order

Ranked by (impact ÷ effort). Each is detailed fully in its deliverable section
below; this table is the index the implementing agent should work top-down from.

| # | What | Where | Effort | Why it's #1-5 |
|---|---|---|---|---|
| 1 | **Fix + surface the streak** (day-boundary simulation, flame counter on every screen, "protect your streak" nudge) | `bankSession`, `Dashboard` header, new `StreakBadge` | S | Streak is the single cheapest return-driver and it is currently broken (see bug below). Fixing a bug that already has UI hooks (`Flame` icon, `hero.streak`) is the highest ratio in the whole spec. |
| 2 | **Collapse the logger to 3 taps** (big exercise tiles → weight/reps steppers already there → one "Add set" → auto-bank on leaving the tab), hide XP math by default | `Logger` | S | Directly fixes the "15 second log" requirement. Mostly deletion/reordering of existing JSX, not new logic. |
| 3 | **"Rival just trained" ticker + overtake alert** on `Dashboard` and `DuelsHub` | New `RivalActivityFeed` component, reuses the existing rival-tick `setInterval` in root `FitClash` | S | Turns the already-running mock interval (currently invisible, just mutates numbers) into the single biggest "competitive and alive" win for almost no new logic — the simulated event stream already exists, it just isn't rendered anywhere. |
| 4 | **Progressive disclosure gate on day-1 dashboard** (hide STR/STA/CON cards, daily cap meter, e1RM, duel-metric picker until unlocked) | `Dashboard`, `StatCard` grid, `DailyBudget` | M | This is the fix for the core complaint ("spreadsheet wearing a game costume"). Mostly conditional rendering keyed off a `daysActive`/`sessionsLogged` counter already derivable from `history.length`. |
| 5 | **First-run flow: guided first set → first bank → "I get it" moment** | New `Onboarding` component inserted between `AuthScreen` and `Dashboard` | M | Nothing today greets a new user; `AuthScreen` drops straight onto a dashboard with seeded numbers that aren't even theirs. This is the fix for the "closes the app in 10 seconds" risk. |

---

## 0. The central tension, resolved

**Keep forever, unconditionally visible:** level number, the big XP bar, "add a
set," "bank session," the avatar. These four things ARE the game. Everything else
is instrumentation and must earn its way onto the screen.

**Hide on Day 1 (first session), unlock by signal, not by day-count where possible:**

| Mechanic | Today's location | Hide until | Why |
|---|---|---|---|
| STR / STA / CON three-stat grid with per-stat ceilings, logs, raise-tips | `Dashboard` → `StatCard` × 3 | 3rd banked session | A first-timer cannot act on "elite reference ceiling 50" before they've lifted anything twice. One number (Level) carries all the motivation Day 1 needs. |
| Daily XP cap meter (`DailyBudget` in header) | Always visible in header | 2nd session, and only after they've actually hit the cap once | On day 1 it's a progress bar nobody asked for, counting toward a ceiling they've never bumped. Showing it before it's ever bound is pure anxiety-generation with no payoff. |
| Diminishing-returns multiplier badges / "×0.5" chips, "set 7 of 5" copy | `Logger`, inline on each drafted set | 6th set logged in one exercise in one sitting (i.e., the moment it would actually fire) | Teach the rule exactly once, at the moment it's true, not as a disclaimer up front. |
| e1RM number, "best e1RM" stat | `Dashboard` hero strip | after 2 strength sessions with the same lift logged twice (so a PR is mathematically possible) | A single-session e1RM is meaningless noise; showing it before a comparison point exists is a stat with nothing to compare to. |
| Level curve formula text ("100 × N^1.5") | `Dashboard` under XP bar | never shown by default; move to a "how leveling works" info icon | Nobody needs the formula to enjoy the bar filling up. This is implementation detail leaking into product. |
| Duel metric picker (XP/Volume/Sets/Active days), stakes, rated-duel quota copy | `DuelsHub` | after first duel is received or after 3rd session | Day 1, offer exactly one duel type: "Who trains more this week" (defaults to TOTAL_XP). Let the metric picker open up once they've played one duel and want more control. |
| Tie-break ladder text ("Ties break on active days, then volume, then CON") | `DuelsHub` footer | only show if a duel actually ties | Resolve ties silently 99% of the time; explain the rule only to the user it just happened to. |
| Archetype label (VANGUARD etc.) | `Dashboard`, `Leaderboard` | keep, cosmetic-only, zero cognitive load — no change needed | Flavor text costs nothing to understand. |

**Week 2 unlocks:** full stat grid with raise-tips, leaderboard sort options,
multiple concurrent duels, custom duel terms (stake slider, day count, metric).

**Month 2 unlocks:** archetype-specific duel modifiers (if ever built), season
resets, the full anti-cheat flag vocabulary surfaced in a "fairness" settings
page instead of inline toasts.

**Concretely, cut/hide from the default view, don't just add:** the three-stat
grid, the daily cap meter, the e1RM figure, the level-curve formula, the duel
metric/stake/day pickers, the diminishing-returns badge, and the tie-break
ladder copy. That is six of the eleven things a first-timer sees today, gone
from day one.

---

## 1. The first 60 seconds

**Today:** `AuthScreen` → straight to `Dashboard`, which renders `INITIAL_HERO`
(level 7, 9840 lifetime XP, a 5-day streak, two completed duels) as if it belongs
to the person who just typed a password. There is no distinction between "seeded
demo state" and "your actual empty account," no tour, and `Logger` assumes you
already know a "set" has a weight, reps, and a cost.

**The one thing they must feel before closing the app:** *I did a real thing and
the game instantly paid me for it — this isn't homework.* Not "I understand the
rules." Feeling > comprehension.

### Screen-by-screen

1. **Sign up** (`AuthScreen`, keep as-is structurally) — copy change only.
   Replace "Log the session. Earn the stats. Settle it in the arena." with a
   single concrete promise: **"Log one set. Watch the bar move."** Removes
   "stats" and "arena" — jargon for mechanics they haven't met yet.

2. **New: `Onboarding` component**, 2 screens, skippable, inserted before
   `Dashboard` only when `history.length === 0`:
   - Screen A: the avatar at level 1, no numbers, one line: *"Everything here
     is driven by what you actually lift. Let's log one set."* One button:
     "Log my first set" → jumps straight into `Logger`.
   - Screen B: skip entirely for returning/seeded users (gate on a
     `hasOnboarded` flag in mock state, not on tab history, so it never
     reappears).

3. **`Logger`, first-run variant** — pre-select the first exercise
   (`EXERCISES[0]`, Bench Press) instead of making them choose from 10 tiles.
   Hide the exercise-tile grid behind a "not this one?" link. Show weight/reps
   steppers pre-filled with the exercise's sane defaults (already in `EXERCISES`
   — `weight: 70, reps: 8`). One big button: **"Add set"**, then immediately
   **"Bank it"** — skip the multi-set drafting UI for the very first set; bank
   on a single set so the reward lands in under 15 seconds.

4. **The payoff** — reuse `LevelUpOverlay`'s visual language (burst rings,
   `Sparkles`) even though they won't level up yet: fire a lighter version — a
   single burst + the `XpBar` filling with the existing spring animation + a
   toast via `Toasts` ("good" tone): **"+9 XP. That's real. Every set counts
   this much."** This is the "I get it" moment: cause (one set) → visible,
   immediate, animated effect (bar moves, number appears) with zero
   intervening screens.

5. Drop them on `Dashboard`, now correctly empty/small (their real level 1
   state, not `INITIAL_HERO`'s seeded level 7 — fix the seed so a brand-new
   signup doesn't inherit a stranger's progress), with a single prompt in the
   `Panel` where `StatCard`s will eventually go: **"Train 2 more days to
   unlock your stats."** This sets up Deliverable 2 (return loop) in the same
   breath, and tells them something is intentionally hidden rather than
   missing/broken.

Effort: **M** (new `Onboarding` component + gating flag + copy edits + fixing
the seed-on-signup bug where `INITIAL_HERO` is reused for new accounts).

---

## 2. The return loop

Every mechanic below: trigger → action → reward → investment → cost of ignoring.
Grounded in real app content, not generic badges.

### 2a. Streak (today: broken — fix first)

**The bug:** `hero.streak` only increments in `bankSession` when
`trainedToday` (`hero.dailyXp > 0`) is false. But nothing in the mock state
ever resets `dailyXp` to 0 on a new day — there is no day-boundary simulation
anywhere in `FitClash.jsx`. So in the current prototype, the streak can
increment at most once per browser session, ever, then freezes. It is
decorative, not functional.

**The fix (S):** simulate day boundaries explicitly in mock state. Add a
`lastTrainedDate` field to `hero`; on `bankSession`, compare today's mock date
(a `useState` clock, advanceable by a dev-only "next day" control for
demoing, or simply `Date.now()` day-diffed) against it. `trainedToday` becomes
"already trained on the current mock day," streak increments once per new
calendar day with activity, and `dailyXp` resets at the boundary. This also
unblocks the daily-cap mechanic below, which depends on the same clock.

- **Trigger:** opening the app on a day you haven't trained yet, with a streak > 0.
- **Action:** log one set (not a full session — one set should be enough to
  keep a streak alive; this is the honest version, see Deliverable 4).
- **Reward:** flame counter increments, visible on `Dashboard` header next to
  level, and on `Leaderboard` per-row (already a column — just needs the
  underlying number to actually move).
- **Investment:** every day the streak survives, the number the user doesn't
  want to lose gets bigger — classic sunk-progress, but attached to something
  real (consecutive days you actually showed up).
- **Cost of ignoring:** streak resets to 0. **Not** amplified with guilt copy
  (see Deliverable 4) — just stated plainly: "Streak reset. Back to day 1."

**Where:** `bankSession` logic fix + a persistent `StreakBadge` (flame + number)
promoted from buried-in-the-hero-panel to the sticky header next to
`DailyBudget`, so it's visible on every tab, not just `Dashboard`.

### 2b. Daily cap as a reason to come back tomorrow, not a punishment today

**Today:** the cap is framed entirely as a limiter — `DailyBudget` shows a
countdown-to-zero bar, and `Logger` shows a scary red "Daily cap reached" with
an `AlertTriangle`, i.e. the UI punishes success.

**Reframe (S):** once the cap is hit, change the copy and visual tone from red
rejection to gold completion. `DailyBudget`'s bar at 100% should read as a
*trophy state*, not an error state — swap the red for the same gold used in
the XP bar gradient, and change the `Logger` cap message from "Daily cap
reached. XP past 1,000 is not banked" to **"Day maxed. 1,000/1,000 — nothing
left on the table. Come back tomorrow for a fresh 1,000."** Add a small
"maxed today" chip to the `Dashboard` header once hit, which doubles as social
proof fuel for 2c below (others can see you capped out).

- **Trigger:** hitting 1,000 XP in a session.
- **Action:** none required — this is a passive reward for the session they
  just finished.
- **Reward:** the completion framing + the fact that lifting *more* today
  literally cannot help, which is permission to stop — a fitness app telling
  you to rest is a trust-building, differentiated moment.
- **Investment:** none forced; the user leaves satisfied, which is what
  brings them back tomorrow instead of trying to cram more volume in (the
  anti-grind purpose README already states).
- **Cost of ignoring:** nothing punitive — sets past the cap still log (for
  the exercise history / anti-cheat ledger) but bank 0 XP, which is already
  the backend behavior; just stop presenting it as a failure state.

### 2c. Duels as social obligation

See Deliverable 3 for the mechanics (rival ticker, head-to-head bars already
in `DuelBars`). The return-loop framing specifically:

- **Trigger:** a duel is live with `daysLeft` counting down (`DuelBars`
  already renders this), or an incoming challenge sits in `DuelsHub` unanswered.
- **Action:** log a session to move `youScore` ahead of `rivalScore`.
- **Reward:** the lead bar flips color in `DuelBars` (`leading` boolean already
  drives this) — immediate, visible, zero extra engineering.
- **Investment:** accepting a duel commits a stake (`duel.stake`, already in
  the data model) — money-like commitment devices increase return rate because
  walking away now costs something concrete.
- **Cost of ignoring:** losing the duel (lose the stake-adjacent payout
  asymmetry: winner gets up to 300, loser gets a flat 25 — already in
  `settle`). Make this cost legible before the duel ends, not just after:
  add a one-line "you're behind by X — one session usually swings this" nudge
  in `DuelBars` when `!leading` and `daysLeft <= 2`.

---

## 3. Competition and interactivity

Current state to replace: `RIVALS` are 5 hardcoded, frozen NPCs; `Leaderboard`
is a static sorted table; duels only resolve via a button literally labeled
"Settle now (demo)" in `DuelsHub`.

All of the below is simulated client-side — no real backend, no push. Each
item says explicitly what's being faked.

### 3a. Rival activity feed (reason to open the app that comes from another person)

**What:** a running feed — "Dax just banked 340 XP · Leg day" — on `Dashboard`
and `DuelsHub`. **Simulation note:** the root `FitClash` component already runs
a 5-second `setInterval` that silently bumps `rivalScore` for active duels
(lines in the root effect) — this is invisible plumbing today. Surface it:
every time that interval fires, also push a line into a new `RivalActivityFeed`
list ("Maeve logged a set. +18 to her score."), extend it to fire for rivals
you're not even dueling (so the whole roster feels alive, not just opponents),
and vary the message by rival archetype for flavor.

**Why it works:** this is literally "a reason to open the app that comes from
another person" — simulated, but the user has no way to distinguish it from
real friend activity, and it is the cheapest possible fix since the interval
already exists and only needs a render target.

**Where:** new `RivalActivityFeed` component, mounted in `Dashboard` below
`Live duels` panel, and as a condensed strip at the top of `DuelsHub`.
**Effort: S.**

### 3b. Visible head-to-head moment

**What exists already and is good:** `DuelBars` — two horizontal bars, you vs.
rival, color-coded by who's leading. Keep it, but promote it: currently it only
shows up buried in `Dashboard`'s "Live duels" panel and inside `DuelsHub`.
Add a **"lead flip" animation**: when `youScore` crosses `rivalScore` (either
direction) during the live-tick interval, fire a quick flash + a toast
("You just took the lead over Dax!" / "Dax just passed you.") via the existing
`Toasts` system. This is the single most "interactive" feeling available
without any backend — a bar visibly overtaking another bar, with the running
rival-tick interval, already produces this moment every ~5 seconds in demo
pacing; it just currently updates silently with no acknowledgment.

**Where:** root `FitClash` effect that ticks `rivalScore`, extended to detect
sign-change in `(youScore - rivalScore)` and call `notify`. **Effort: S.**

### 3c. Mild, healthy social pressure

**What:** a single line on `Dashboard`, "This week: 3 of 5 friends have
trained today," computed from `RIVALS` (mock: treat `RIVALS` as "friends" for
this purpose, flag clearly as "friends," not "the whole user base," since
comparison-to-everyone is a worse, more anxious framing than comparison-to-
your-circle). Also add it as a single row at the top of `Leaderboard`:
"ironmaeve and sunny_reps trained today — you haven't yet," generated from
whichever rivals have a non-zero mock "trained today" flag (new boolean driven
by the same mock clock as 2a's streak fix).

**Why mild, not heavy:** this is information, not a guilt message — no red
text, no "you're falling behind," just a factual social proof line. Compare
to Deliverable 4's rejection of guilt-based copy.

**Where:** new `TrainedTodayStrip` component on `Dashboard` and `Leaderboard`.
**Effort: S.**

### 3d. Make the leaderboard feel alive, not static

**What:** `Leaderboard` already uses `motion.tr` with `layout` — row reordering
already animates when sort values change. It's unused because `RIVALS` values
never change outside the duel-tick interval. Fix: have the same rival-tick
interval also nudge `lifetimeXp` for all rivals (not just the ones in active
duels), so the leaderboard visibly reshuffles every so often while the tab is
open — the `layout` animation is sitting there ready to fire. **Effort: S**
(extend the existing interval's scope; the render-side animation needs zero
changes).

### 3e. Make duel resolution feel earned, not administrative

**What:** replace the "Settle now (demo)" button's framing. It should stay
functionally available for demoing (`onSettle` already works), but relabel it
and add a **duel-end countdown visual** reusing `Timer`/`daysLeft` already in
`DuelBars`, with a distinct "final day" state (last 24 hours: pulse the bars,
change copy to "Final stretch"). When `settle` fires naturally (`daysLeft`
reaching 0, simulated by the mock clock from 2a), trigger a result screen
reusing `LevelUpOverlay`'s celebratory visual language for wins, and a
quieter, respectful one for losses — not punitive, just lower-key. **Effort: M**
(needs the mock clock from 2a wired to auto-expire duels, plus a new result
overlay variant).

---

## 4. The line you will not cross

Fitness apps have a real failure mode: pushing injury, shaming rest, rewarding
overtraining. The game's own math already encodes good values (diminishing
returns punish junk volume, the cap prevents cramming, CON is explicitly "the
one stat that measures whether the app is working" per the README) — the UX
must not undermine that with dark-pattern engagement tactics layered on top.

**Core argument used throughout:** this is a fitness app — the user's actual
results (strength up, a streak of real training days, a duel actually won
through real effort) are the retention mechanism. Anything that drives opens
without driving real training is working against the product's own economy
(stat points, XP, duel scores are all literally computed from real lifted
weight) and will show up as hollow numbers the user eventually distrusts.
Honest mechanics compound; manipulative ones burn out the moment the user
notices the trick, and fitness users — more than most audiences — notice fast
when a game is lying about their body.

### Recommended (the honest version)

- **Streak with rest-day protection: YES, build it.** A fitness streak that
  breaks the first time someone takes a scheduled rest day is bad fitness
  advice wearing a dark pattern. Recommend: **one free "streak shield" per
  week**, automatically applied (no begging, no ad-watching, no micropayment)
  — if a day passes with no session, the shield consumes itself silently and
  the streak survives. Surface it quietly in the `StreakBadge` tooltip ("1
  rest day protected this week") not as a scarcity mechanic. This directly
  answers the brief's own callout: a streak that punishes rest is bad advice.
- **Honest cap framing (Deliverable 2b):** reward-framed, not threat-framed.
  Already specified above.
- **Social proof, not social shame (Deliverable 3c):** "3 of 5 friends trained
  today" — factual, opt-in to read, never pushed as a notification, never
  phrased as "you're behind."
- **Earned celebration, respectful loss framing (3e):** wins get fanfare,
  losses get information, never guilt.
- **One real notification worth having, if/when a backend exists:** "it's
  your rival's final day in your duel" — functionally useful, not manufactured
  urgency. Not building the actual push infrastructure now (no backend), but
  recording the position: notifications should be *information the user
  would want*, never a re-engagement hook disguised as information.

### Rejected, and why

- **Streak-loss guilt copy** ("Don't break your streak!", flame going out with
  sad animation, "Maeve is going to pass you if you skip today!"). Rejected:
  this is the single most common dark pattern in habit apps and the brief
  explicitly flags it as bad fitness advice. A missed day is sometimes the
  correct training decision. The honest version (streak shield + neutral
  "reset to day 1" copy) retains better here specifically because a user who
  gets injured from guilt-driven overtraining churns permanently and tells
  people why.
- **Artificial countdown timers / urgency on things that aren't actually
  time-limited** (e.g., "Only 2 hours left to claim bonus XP!" on anything
  that isn't a real duel deadline). Rejected outright. The only countdown kept
  is `duel.daysLeft`, which is a real, already-committed deadline the user
  set themselves by accepting a duel — not manufactured scarcity.
- **Variable-ratio reward schedules** (randomized loot/mystery-box XP bonuses,
  slot-machine-style set rewards). Rejected. FitClash's XP is already a
  deterministic function of real work (`volume ÷ 60`, etc.) — this is a
  feature, not a gap to patch with randomness. Injecting variable-ratio
  rewards on top would literally make the numbers lie about effort, which
  breaks the trust the whole economy depends on (same reasoning as the
  README's anti-cheat design: "if the client can choose its own reward, the
  game is over" — a loot box is the client choosing its own reward by chance
  instead of by lifting).
- **Guilt-based copy generally** ("You're letting your team down," "Dax is
  laughing at you right now"). Rejected across the board — replaced with
  neutral/factual framing everywhere in this spec (2b, 3c, 4's loss framing).
- **Notification nagging** (daily "come back!" pushes with no new information,
  streak-reminder spam). Rejected as a default behavior. If/when push exists,
  gate every notification on "does this carry information the user doesn't
  already have and would want" (a rival just took the duel lead, a duel is in
  its final day, a streak shield was just used) — never a bare "we miss you."

### Why the honest version wins here specifically

In most consumer apps, engagement and the product's real value can diverge —
you can be notification-addicted to an app that gives you nothing. In
FitClash, XP, stats, and duel scores are mathematically derived from real
training (`Formulas.java` / the mirrored formulas in `FitClash.jsx`). That
means every dark pattern above either (a) tries to manufacture engagement
disconnected from training, which the economy will expose as hollow the
moment the user checks the math, or (b) tries to pressure more training than
is healthy, which produces injury or burnout — the single fastest way to lose
a fitness-app user permanently. The honest mechanics in this spec (real
streaks with real rest protection, real duels with real stakes, real social
proof from real friends) are retentive precisely because they're reflections
of a result the user actually wants — getting stronger with people they
know — which is a longer-lived motivation than any notification trick.
