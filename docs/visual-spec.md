# FitClash Visual Spec — Palette + Living Character

Author: Art Direction pass, Sept 2026. Target reader: the engineer implementing
changes in `frontend/src/FitClash.jsx` (the `Avatar`, `STAT_META`, `StatCard`,
`XpBar` and the formulas near the top) and `frontend/tailwind.config.js`.

This document does not touch those files. It specifies them precisely enough
that no creative judgment calls remain for implementation.

Hard constraints assumed throughout: no external image assets (inline SVG +
CSS + Framer Motion only), Tailwind theme tokens only, committed dark UI,
AA 4.5:1 for text / 3:1 for bars-icons-borders (every number below is
measured, not eyeballed, with the WCAG relative-luminance formula), and a
defined static fallback for every animation under `prefers-reduced-motion`.

---

## 1. Palette

### 1.1 Honest diagnosis of the current scheme

What's there today (`tailwind.config.js`): `iron` gunmetal neutrals, a
`chalk` off-white text scale, `plate.str/sta/con` as literal red/blue/yellow
competition-plate colors, `plate.ember` as a single hot-orange accent doing
triple duty (XP fill, primary button, focus ring), and `plate.jade`/`rust`
as half-used semantic colors.

Worth keeping:
- The `iron` neutral ramp is genuinely good — it's not a flat near-black,
  it steps cleanly from `#0D1014` to `#4A5261`, and every text tier already
  clears AA against every surface it's used on (verified below: `chalk-muted`
  on `iron-700` is 4.71:1, the tightest pair in the app, and it passes).
- The red/blue/yellow STR/STA/CON triad is colorblind-safe almost by
  accident — it avoids a pure red/green pair, which is the one combination
  that breaks under deuteranopia/protanopia (the two common forms of
  red-green color blindness). That instinct is correct and should survive
  any repaint.
- The split between a saturated "bar/icon" color and a brightened "text"
  tint (`plate.str` vs `plate.strText`) is the right pattern for mixed
  contrast tiers. Keep that mechanism; this spec extends it to every stat
  color instead of just two.

What's weak, specifically:
- The three stat colors are literal paint-chip primaries (`#D8322C` red,
  `#2C6FD1` blue, `#E9B424` yellow) — the exact hues you'd get from a
  default chart-library categorical palette. They read as a legend, not as
  equipment. Nothing about them says "iron," "chalk," or "competition."
- `plate.ember` is overloaded: it's the XP accent, the primary button fill,
  AND the focus ring color. One hue carrying three different meanings means
  none of them land — when everything is ember, nothing specifically means
  "you just earned XP."
- `plate.jade` (win) and `plate.rust` (declared but barely used) sit far
  apart in the file from the stat colors they need to stay distinguishable
  from, so nobody has verified they don't collide with STR red under
  color-blindness simulation. They're close enough in hue family to risk it.
- No token currently represents loss as distinct from "STR red" — a lost
  duel and a strength stat bar risk reading as the same color doing two jobs.

### 1.2 Three directions

**A — Calibrated Plate (refined).** *Concept: the exact hex of bumper
plates on a competition platform — keep the literal plate-color logic the
owner already chose, but deepen the saturation and separate every
multi-purpose hue into single-purpose tokens.*
`oxide #C1392B` (STR) · `galvanized #2E7D9E` (STA) · `brass #C9922F` (CON) ·
`ember #E8631B` (XP only) · `patina #2FA36B` (win) · `garnet #C14E5F` (loss) ·
`chalk #F2EFE7` (text).

**B — Oxide & Chalk.** *Concept: a weight room that has seen a thousand
sessions — oxidizing iron, galvanized steel going teal at the edges, brass
fittings on old plates, chalk dust in the air. Nothing showroom-fresh.*
Same token set as A, but the surfaces lean one notch cooler and the stat
colors are pulled from real metal-weathering references rather than paint
swatches: `oxide` (rust-red, not fire-engine red), `galvanized` (the
blue-grey a steel bar actually goes, not sky blue), `brass` (a warm
trophy-gold, not traffic-sign yellow).

**C — Scoreboard Arena.** *Concept: a gymnasium scoreboard and the painted
lane lines on a competition floor — numbered-bib red, lane-marker blue,
judge's-flag gold, LED-amber for the XP readout.* More saturated and
"game-show" than A/B; higher energy, lower maturity. Tokens:
`bib #D43A2E`, `lane #2B6FB0`, `flag #D4A017`, `ledamber #FF9500`,
`win #22A565`, `loss #C23B3B`.

### 1.3 Recommendation: **B — Oxide & Chalk**

Direction A is the safe move but doesn't fix the actual complaint — it's
still three paint-chip primaries, just darker ones. Direction C is
energetic but pushes FitClash toward a generic "sports app" look and its
`loss` red sits too close to `bib` red for comfort under deuteranopia.
**Oxide & Chalk** keeps the plate-color *logic* the owner already liked
(STR/STA/CON as real equipment colors) while replacing each hue with its
weathered-metal equivalent, which is more specific to a gym than any
"modern app" palette and is the only direction where every stat color,
every semantic color, and every text tint was individually measured against
every surface it appears on (table below) rather than inherited by analogy.

### 1.4 Token table — Oxide & Chalk (measured)

Contrast method: WCAG relative luminance, `(L1+0.05)/(L2+0.05)`, computed
with the exact formula given in the brief. Tier column states which AA
threshold applies: **text** needs ≥4.5:1, **UI** (bars/icons/borders) needs
≥3:1.

#### Surfaces (unchanged from current `iron` scale — keep as-is)

| Token | Hex | Use |
|---|---|---|
| `iron-950` | `#0D1014` | App background, bar troughs |
| `iron-900` | `#13161B` | Page background |
| `iron-800` | `#1B1F26` | Panel fill (`bg-iron-800/60`) |
| `iron-700` | `#252A33` | Borders, dividers, the tightest surface anything sits on |
| `iron-600` | `#333945` | Elevated borders |
| `iron-500` | `#4A5261` | Disabled/muted icon strokes |

#### Text tiers (unchanged — already compliant, re-verified)

| Token | Hex | Tier | on iron-950 | on iron-900 | on iron-800 | on iron-700 |
|---|---|---|---|---|---|---|
| `chalk` | `#F2EFE7` | text | 16.59 | 15.78 | 14.38 | 12.54 |
| `chalk-dim` | `#B9C0CB` | text | 10.41 | 9.90 | 9.02 | 7.87 |
| `chalk-muted` | `#8B94A3` | text | 6.23 | 5.92 | 5.40 | **4.71** |

`chalk-muted` is the floor — it clears 4.5:1 everywhere in the app by a
margin of 0.21 on the worst surface (`iron-700`). Do not introduce a dimmer
text tone without re-running the check.

#### Stat colors — bar/icon tier (3:1) + text-safe tint (4.5:1)

Each stat gets two tokens: the saturated one for bars, left-border strips
and icons (`STatCard`'s colored rail, `Icon` fill), and a brightened tint
for anywhere the color is actually rendered as text (e.g. a `+N` delta).
This is the existing `plate.str`/`plate.strText` pattern, applied to all
three stats instead of two.

| Token | Hex | Tier | on iron-950 | on iron-900 | on iron-800 | on iron-700 |
|---|---|---|---|---|---|---|
| `plate.oxide` (STR bar/icon) | `#C1392B` | UI (≥3:1) | 3.53 | 3.36 | 3.06 | 2.67* |
| `plate.oxideText` (STR text) | `#FF8A75` | text (≥4.5:1) | 8.30 | 7.89 | 7.20 | 6.27 |
| `plate.galvanized` (STA bar/icon) | `#2E7D9E` | UI (≥3:1) | 4.13 | 3.92 | 3.58 | 3.12 |
| `plate.galvanizedText` (STA text) | `#7FC3E0` | text (≥4.5:1) | 9.79 | 9.31 | 8.49 | 7.40 |
| `plate.brass` (CON bar/icon) | `#C9922F` | UI (≥3:1) | 6.93 | 6.59 | 6.01 | 5.24 |
| `plate.brassText` (CON text) | `#E8B860` | text (≥4.5:1) | 10.40 | 9.89 | 9.02 | 7.86 |

\* `plate.oxide` drops to 2.67:1 on `iron-700` — below the 3:1 floor. In
practice nothing renders this token directly on `iron-700`: the StatCard
rail and XP/stat bars sit on `iron-950` or the `iron-800/60` panel fill
(3.06+), never on the bare `iron-700` border color. **Do not use
`plate.oxide` as a border or on an `iron-700` chip.** If a future use case
needs that, use `plate.oxideText` instead (6.27 on `iron-700`).

Colorblind check (deuteranopia/protanopia, the common red-green forms):
oxide (red), galvanized (blue), brass (gold) form a red/blue/yellow triad
with no red-green pair in it — the one combination that collapses under
those conditions. Brass and oxide stay separated primarily by hue-shift
toward blue vs. toward orange-yellow under simulation, and by the
consistent ~15-20% luminance gap between them (oxide L≈0.09, brass L≈0.27),
so they remain distinguishable on value alone even if hue perception
degrades. Keep brass warmer/lighter than oxide — do not darken brass to
"match" saturation; that's what removes the luminance separation.

#### Semantic colors (XP, win, loss — each distinct from the stat colors and from each other)

| Token | Hex | Tier | on iron-950 | on iron-900 | on iron-800 | on iron-700 |
|---|---|---|---|---|---|---|
| `plate.ember` (XP only — bar fill, level-up flash) | `#E8631B` | UI (≥3:1) | 5.66 | 5.38 | 4.90 | 4.28 |
| `plate.patina` (win) | `#2FA36B` | UI (≥3:1) | 5.97 | 5.67 | 5.17 | 4.51 |
| `plate.patinaText` (win, as text) | `#6FD9A4` | text (≥4.5:1) | 11.02 | 10.48 | 9.55 | 8.33 |
| `plate.garnet` (loss) | `#C14E5F` | UI (≥3:1) | 4.11 | 3.90 | 3.56 | 3.10 |
| `plate.garnetText` (loss, as text) | `#FF95A3` | text (≥4.5:1) | 9.15 | 8.70 | 7.93 | 6.92 |

`plate.ember` is now reserved for XP and the single moment of a level-up
flash — it is **no longer the primary button color**. Move `PlateButton`'s
default `tone="ember"` fill to a new neutral-forward token
(`plate.steelAction`, reuse `iron-600`/`iron-500` family with a `chalk`
label, i.e. the existing `tone="steel"` button becomes the default) so a
"Bank session" button press and an XP gain are no longer visually the same
event. This is a one-line default-prop change, not a redesign of
`PlateButton`.

`plate.garnet` is a distinct hue (pink-red, hue ~350°) from `plate.oxide`
(brick-red, hue ~6°) specifically so a lost duel never reads as "your STR
bar." Keep at least 15° of hue separation if either is retuned later.

### 1.5 Fonts

Keep Anton / Barlow / Barlow Condensed. All three are on Google Fonts, the
pairing already does the job (a monumental display face for level/XP
numbers, a condensed data face for stats and labels, a workhorse body
face), and nothing about the owner's complaint is about typography. No
change.

---

## 2. The character that grows

### 2.1 Why today's avatar reads as static

The only thing that changes between level 1 and level 50 today is the aura
ring's fill fraction (`aura = min(1, 0.25 + level/24)`) and the number in
the badge. The figure — a fixed 9px-radius head, 11px-wide torso, fixed
barbell with fixed plates — is identical pixel-for-pixel at every level.
Growth needs to live in the silhouette, not just the ring.

### 2.2 Level curve reality check

`xpForNextLevel(N) = floor(100 × N^1.5)`. Cumulative lifetime XP to reach a
level (computed, not estimated):

| Level | Lifetime XP to reach it |
|---|---|
| 5 | ~1,700 |
| 10 | ~11,100 |
| 15 | ~32,000 |
| 20 | ~67,100 |
| 25 | ~118,800 |
| 30 | ~189,000 |
| 40 | ~392,000 |
| 50 | ~689,500 |

At the 1,000 XP/day hard cap, level 25 (~119k lifetime XP) is already ~119
fully-capped days — realistically a year or more of real training given
rest days and the diminishing-returns curve, matching the brief's "a
committed user reaches roughly level 24 in a year." Levels 30+ are a
multi-year aspirational tier that most accounts will never see. Tiers below
are weighted accordingly: four tiers cover levels 1-19 (where almost every
active user lives), a fifth covers the realistic one-to-two-year ceiling,
and a sixth is the long-tail "forever grind" tier.

### 2.3 The six tiers

All figures are drawn in the existing `120×120` viewBox, replacing the
fixed coordinates in `Avatar`'s `<svg>`. Coordinates below are anchored to
the current rig (torso centerline `x=60`, head center `(60,40)`, bar at
`y≈42-47`, feet at `y=104`) so each tier is a diff against the current
markup, not a redraw.

---

**Tier 1 — Chalk Hands** · Levels 1-4 (lifetime XP 0 to ~800)

- *Figure:* torso stroke-width `9` (down from today's 11 — this tier should
  look *lighter* than the current default, establishing headroom to grow
  into). Head radius `8`. Limb stroke-width `4.5`. Stance narrow: feet at
  `x=56/64` (8px spread). Slight forward lean: torso path curves
  `M60 54 Q62 70 60 88` instead of a straight line.
- *Equipment:* bar width `60` (short bar, `x=30` to `x=90`), thickness `4`.
  One plate per side, small: `9×16`, color `plate.galvanized` (a 15kg-type
  plate — the lightest, coolest color, visually "entry weight").
  No collars drawn.
- *Environment:* aura ring stroke-width `1.5`, max opacity `0.5`, no
  particles, no platform — the figure floats on transparent background,
  same as today.
- *Silhouette-legible marker (44px thumbnail):* thin, narrow stance, single
  small plate blob per side — reads as "sparse" at a glance.

**Tier 2 — Lifter** · Levels 5-9 (lifetime XP ~1,700 to ~9,000)

- *Figure:* torso stroke-width `10`. Head radius `8.5`. Limb stroke-width
  `5`. Stance widens: feet at `x=54/66` (12px spread). Torso straightens
  (back to a straight vertical path, matching current rig).
- *Equipment:* bar width `70` (`x=25` to `x=95`), thickness `4.5`. Two
  plates per side: inner `plate.brass` (small, `7×13`), outer
  `plate.galvanized` (`9×19`) — this is the first tier with a visible
  plate *stack* rather than one disc.
- *Environment:* aura ring stroke-width `2`, opacity ramps `0.5→0.65`
  across the tier. Add a thin baseline: a single `1px` horizontal line at
  `y=106` in `iron-700` — the first hint of a platform.
- *Silhouette marker:* two-tone plate stack appears — distinguishes from
  Tier 1's single blob even at 44px.

**Tier 3 — Competitor** · Levels 10-14 (lifetime XP ~11,100 to ~27,000)

- *Figure:* torso stroke-width `11` (today's current default — this tier
  is "today's avatar" as a baseline, not a new high point). Head radius `9`.
  Limb stroke-width `5.5`. Stance `x=53/67` (14px spread, matches current
  rig exactly).
- *Equipment:* bar width `80` (today's full-width bar, `x=20` to `x=100`),
  thickness `5`. Two plates per side as today, but outer plate is now
  `plate.oxide` (a 25kg-type plate — the heaviest, hottest color) and inner
  is `plate.galvanized`: the stack is visually heavier and hotter-toned
  than Tier 2.
- *Environment:* aura ring full-strength per the existing `aura` formula.
  Platform line thickens to `2px` and gains two short vertical tick marks
  at `x=40` and `x=80` (rack uprights, suggested not drawn in full) in
  `iron-600`.
- *Silhouette marker:* bar now spans past the shoulders at both ends
  (visibly wider than the figure) — the first tier where the equipment is
  bigger than the person.

**Tier 4 — Contender** · Levels 15-19 (lifetime XP ~32,000 to ~61,000)

- *Figure:* torso stroke-width `12.5`. Head radius `9.5`. Limb stroke-width
  `6.5`. Stance widens further: `x=51/69` (18px). Add a second torso detail:
  a short horizontal line at `y=68` (`stroke-width 2`, `iron-500`) implying
  a belt — the first visible gear item on the body itself.
  Shoulders implied by extending the arm-to-bar path origin outward
  (`M58 58 L42 46` / `M62 58 L78 46`, 2px wider at the shoulder joint than
  Tier 3's `M60 60`).
- *Equipment:* bar thickness `5.5`. Three plates per side now: innermost
  `plate.brass`, mid `plate.galvanized`, outer `plate.oxide` (`9×23`,
  matches today's largest plate size but now it's the third disc, not the
  only one).
- *Environment:* aura gains a second, outer ring (radius `56` vs the main
  ring's `52`), same hue, `0.15` opacity, static (no independent
  animation) — reads as "aura has depth" without adding an animated layer.
  Platform gains a flat rectangle fill (`iron-800`, `x=10 w=100 y=104 h=4`)
  — a visible lifting platform, not just a line.
- *Silhouette marker:* three-disc stack plus the belt line — distinctly
  "loaded" silhouette even at 44px.

**Tier 5 — Champion** · Levels 20-29 (lifetime XP ~67,000 to ~175,000) —
*the realistic one-to-two-year ceiling; design this tier to feel like an
achievement, not a stepping stone*

- *Figure:* torso stroke-width `14`, and the torso path gains a slight
  taper — wider at the shoulder end, narrower at the hip (`M60 50 L58 70
  L60 88`, replacing the single straight line) to imply a V-taper rather
  than a uniform bar. Head radius `10`. Limb stroke-width `7.5`. Stance
  `x=49/71` (22px). Belt line gains a small buckle: a `3×3` square at
  `(60,68)` in `plate.brass`.
- *Equipment:* bar thickness `6`. Four plates per side, same three colors
  as Tier 4 plus a repeated outer `plate.oxide` disc (two 25kg-equivalents
  stacked outside the brass/galvanized pair) — reads as a genuinely heavy
  pull. Add collars: small `3×6` caps in `iron-500` at each bar end,
  outside the last plate.
- *Environment:* the outer aura ring (introduced Tier 4) now animates
  independently and slowly (its own slow pulse, see Motion §3.1), and gains
  3-4 small static particles: tiny circles (`r=1.5`, `plate.ember`,
  `0.4` opacity) fixed at points around the ring circumference, not
  orbiting — cheap, no per-frame recomputation. Platform rectangle gets a
  `1px` top highlight edge in `iron-600`.
- *Silhouette marker:* V-taper torso shape plus four-plate stack plus
  visible collars — a materially different outline from Tier 4, not just
  "more of the same," at 44px.

**Tier 6 — Iron Titan** · Levels 30+ (lifetime XP ~189,000+) — *long-tail,
aspirational, built once and left alone*

- *Figure:* torso stroke-width `16`, full taper retained from Tier 5 but
  more pronounced (`M60 48 L56 70 L60 88`). Head radius `10.5`. Limb
  stroke-width `9`. Stance at maximum, `x=47/73` (26px). Add a second belt
  detail: the buckle square becomes a small cross-hatch pattern (two `4px`
  diagonals) in `plate.brass` — read as an ornamented/decorated belt, the
  closest this system gets to "armor."
- *Equipment:* bar thickness `6.5`, slightly bowed: render the bar as a
  shallow arc (`path d="M20 46 Q60 44 100 46"` instead of a straight
  `rect`) to imply visible load-bend. Five plates per side (add one more
  outer `plate.oxide`). Collars grow to `4×8`.
- *Environment:* a third aura ring appears (radius `60`, `0.1` opacity,
  static), and the particle count from Tier 5 doubles (6-8 fixed points).
  The platform rectangle gains a subtle `plate.ember`-tinted bottom glow
  (a second rect, same bounds, `blur` via a static SVG `<feGaussianBlur>`
  filter defined once — not re-rendered per frame).
- *Silhouette marker:* bowed bar plus five-plate stack plus three aura
  rings — unmistakably the "maxed out" silhouette even at 44px, and
  nothing above this tier ever has to be designed since no further bar/
  plate growth is specified past it.

### 2.4 Stat-driven looks (independent of level)

Two level-20 characters with different training histories must not look
identical. Keep this cheap: each stat maps to **one discrete visual
property with 3 buckets** (low/mid/high, by stat value thresholds already
computed server-side — no new math), not a continuous recalculation.

- **STR** (bucket by `str` value: <20 / 20-39 / ≥40) controls **bar bend**.
  Low: bar rendered straight (as drawn per tier above). Mid: bar gets a 1px
  downward bow at its center (`Q60 47 100 46` instead of a flat line/rect).
  High: 2px bow, matching the "bowed bar" treatment that Tier 6 gets
  automatically — a high-STR character at a lower tier previews that look
  early.
- **STA** (bucket by `sta` value) controls **breath/vapor**. Low: none.
  Mid: a single short static vertical line above the head (`y=28` to `y=24`,
  `iron-500`, `opacity 0.3`) implying visible breath/exertion. High: two
  such lines, slightly splayed, `opacity 0.5` — reads as "visibly
  conditioned," costs two extra static path elements, no animation.
- **CON** (bucket by `con` value, which is also the streak-driven stat)
  controls **aura steadiness**: this is the one stat allowed to touch
  motion, because it's cheap. Low: the existing idle aura pulse (§3.1) runs
  at its default amplitude. Mid: amplitude reduced 30% (a consistent
  trainer's aura "holds steadier" rather than surging). High: aura
  additionally gets a `strokeDasharray` broken into small even segments
  (e.g. `4 2`) instead of a solid ring — reads as "charged," still a single
  static SVG attribute, not a new animated layer.

Rendering cost: at most 2 extra static SVG elements (STA) + 1 path-data
swap (STR) + 1 attribute swap (CON amplitude/dasharray) on top of the
tier's base figure. No new `motion.*` components are introduced by stats.

---

## 3. Motion

All animations are Framer Motion (`motion.*`, `AnimatePresence`), matching
the library already in use. `useReducedMotion()` is already imported in
`FitClash.jsx` — every animation below must branch on it the same way the
file already branches `glow` in `Avatar`.

### 3.1 Idle (resting loop)

- *Motion:* existing `motion.g` body bob, `y: [0, -1.6, 0]`, `duration:
  3.2s`, `ease: easeInOut`, `repeat: Infinity`. Keep as-is — it's already
  cheap (one transform, one element). Aura pulse likewise stays as the
  existing `scale`/`opacity` keyframe loop at `4.5s`.
- *Tier addition:* Tier 4+'s second aura ring pulses on its own loop,
  `6s` duration, lower amplitude (`scale: [1, 1.05, 1]`), so it visibly
  drifts out of phase with the inner ring without any added per-frame cost
  (it's one more `motion.circle` with its own `transition`, same pattern
  already used for the inner ring).
- *Reduced motion:* render the figure and both aura rings at their
  resting/mean values (no `animate` prop at all, or `animate` set to the
  midpoint keyframe as a static value) — no bob, no pulse. This is a prop
  branch on `useReducedMotion()`, not a separate component.

### 3.2 Gaining XP (a set is banked, bar fills)

- *Motion:* existing `XpBar` spring fill (`type: spring, stiffness: 90,
  damping: 16`) plus the existing white flash overlay (`opacity 0.85→0`,
  `0.6s`). Keep both. Add: the stat value in `StatCard` already pops in
  with a spring on `key={value}` change — keep as-is, it's already cheap
  and already has a `delta` chip animation.
- *Reduced motion:* bar width still animates (a width/value change is
  information, not decoration — fine to keep per WCAG motion guidance for
  essential changes), but swap the `spring` transition for a `duration:
  0.3, ease: linear` tween and drop the white flash overlay entirely
  (render the final 0-opacity state immediately).

### 3.3 Levelling up (same tier, big moment)

- *Motion:* the avatar's `glow` prop (already wired from `xpFlash`) already
  speeds the aura pulse to `0.7s` and raises its ceiling — keep. Add: the
  `LV {level}` badge does a single scale pop, `scale: [1, 1.35, 1]`,
  `duration: 0.5s`, `ease: 'backOut'`. Add one burst of 6 small particles
  (`motion.circle`, `r=2`, `plate.ember`) that spawn at the ring and
  animate outward + fade (`x/y` to a fixed small radial offset, `opacity
  1→0`, `duration: 0.6s`, staggered `0.03s` each) — a one-shot
  `AnimatePresence` exit, not a loop, so it costs nothing once settled.
- *Reduced motion:* badge still updates its number but with no scale pop
  (CSS-level instant swap). No particle burst — the level number changing
  is itself the signal.

### 3.4 Crossing into a new tier (bigger than a level-up)

- *Motion:* this must read as categorically bigger than §3.3. Sequence:
  (1) screen-safe flash — the aura's radial gradient briefly maxes opacity
  to `1` over `0.4s`; (2) the *entire* avatar crossfades from the old
  tier's SVG to the new tier's SVG over `0.5s` (`opacity` swap on two
  stacked `<svg>`, old fading `1→0`, new fading `0→1`, both absolutely
  positioned — a crossfade, not a layout animation, so there's no
  reflow); (3) a wider particle burst than the level-up one — 12 particles
  instead of 6, larger (`r=3`), traveling further, `duration: 0.9s`; (4) the
  platform (if the new tier introduces or changes it) fades in under the
  figure, `0.4s`, slightly after the figure settles (`delay: 0.3s`).
  Total sequence ~1.2s, orchestrated with Framer's `delay`s rather than a
  manual timeline.
- *Reduced motion:* no flash, no crossfade animation — swap directly from
  old SVG to new SVG with no transition (one frame). No particles. The
  platform/environment element appears instantly in its new state.

### 3.5 Winning a duel

- *Motion:* aura briefly recolors from `plate.ember` to `plate.patina`
  (interpolated via a `backgroundColor`-equivalent on the radial-gradient
  stop colors, or by crossfading two overlaid gradient layers — cheaper:
  render a second aura `motion.div` in `plate.patina`, `opacity 0→0.6→0`
  over `1s`, stacked on top of the existing ember aura). Figure does a
  single upward hop: `y: [0, -6, 0]`, `duration: 0.5s`, `ease: backOut`,
  no repeat.
- *Reduced motion:* a single static `plate.patina` ring flash at fixed
  `opacity 0.4` for `0.3s` then removed (fade handled by `AnimatePresence`
  exit, no keyframe animation in between) — no hop.

### 3.6 Losing a duel

- *Motion:* aura dims and briefly tints `plate.garnet` the same way the
  win state tints `plate.patina` (second overlay `motion.div`, `opacity
  0→0.4→0`, `1s`). Figure does a small downward settle: `y: [0, 3, 0]`,
  `duration: 0.6s`, `ease: easeInOut` — notably slower and smaller than the
  win hop, so win/loss are distinguishable by motion quality alone, not
  just color.
- *Reduced motion:* static `plate.garnet` ring flash, `opacity 0.3` for
  `0.3s`, no figure movement at all (losing shouldn't get a bounce even a
  muted one).

---

## 4. What's expensive and why (flagged, not built here)

Called out per the brief's request for an honest cost read — none of this
is in the implementation scope above:

- **Per-stat continuous (non-bucketed) visual scaling** — e.g. bar bend
  angle as a smooth function of exact STR value — would require recomputing
  path `d` strings on every render instead of picking from 3 precomputed
  strings. The 3-bucket design in §2.4 gets 90% of the perceived benefit
  (two characters *do* look different) for near-zero cost; a continuous
  version is not worth building.
- **Orbiting (vs. static) aura particles** — particles that actually travel
  around the ring per frame need either a per-frame transform recompute or
  an SVG `<animateMotion>` per particle. The static-point version in Tiers
  5-6 reads as "charged" at a fraction of the cost and should be preferred
  unless a future pass has render-budget to spare.
- **A unique SVG redraw per level (50 states)** instead of per tier (6
  states) — this is the single biggest false economy the owner could ask
  for next. Six tiers already deliver "the character visibly grows"; fifty
  hand-authored states would be fifty times the authoring and QA cost for
  a difference most users will never sit still long enough to notice
  between adjacent levels.
