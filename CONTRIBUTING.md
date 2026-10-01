# Contributing to FitClash

FitClash is a solo-developer project (one human + AI coding agents, with a
handful of testers using the live site). This document is deliberately
lightweight: it exists to keep `main` deployable, not to simulate a large
team's process.

## The one fact that drives everything

**Every push to `main` deploys to <https://rickh5502.github.io/fitclash/>,
and real testers are using that URL.** There is no staging environment and
no manual approval gate between a commit landing on `main` and it being
live. That single fact is the justification for every rule below — each one
exists to keep something broken from reaching `main`, nothing more.

## Branching model

**Trunk-based, with short-lived feature branches for anything risky, and
direct commits to `main` for anything trivial.** There is no `develop`,
`release`, or `hotfix` branch, and there will not be one. GitFlow's
multi-branch model exists to coordinate many developers who need isolation
from each other and a staged rollout; with one developer (plus agents) and
an app that already deploys continuously, that coordination problem does
not exist, and the extra branches would only add merge ceremony between
`main` and a deploy that was always going to be the next thing that
happened anyway. The thing that actually matters here is CI: `main` is kept
deployable by requiring the `CI` workflow (frontend tests + preview
freshness check + backend tests) to pass, not by interposing branches
between a change and production.

**Open a branch + PR when:**
- The change touches `backend/.../service/Formulas.java` or the formula
  block in `frontend/src/FitClash.jsx` (game math — see Invariant 1 below).
- The change touches auth, security config, or anything in
  `application-prod.properties` / `StartupSafetyGuard`.
- The change is a schema change, a new dependency, or anything you can't
  fully verify locally before pushing.
- The change is large enough that you'd want a second pass (your own, or an
  agent's) before it's live.

**Commit straight to `main` when:**
- It's a typo, copy fix, README/docs update, or a one-line config tweak.
- You've already run the relevant test suite locally and it's green.
- Reverting it would be trivial if it somehow broke something.

A typo fix and a schema change are not the same risk and should not go
through the same ceremony. Use judgment, not a rule that fires on file
count.

### Branch naming

`<type>/<short-description>`, where `<type>` is one of `feat`, `fix`,
`chore`, `refactor`, `docs`, `security` — mirroring the commit prefixes
below. Examples: `feat/duel-rematch`, `fix/streak-off-by-one`,
`security/rate-limit-signup`.

## Commit messages

Follow the style already established in this repo's history (see
`git log`): a short imperative summary line, optionally a type-ish lead-in
when it clarifies scope (e.g. `FitClash: gamified fitness tracker
prototype`, `Harden auth and secrets handling`), then a blank line and a
bulleted body explaining **why**, not just what. Specifically:

- Summary line: imperative mood, no trailing period, roughly ≤70 chars.
- Body: bullet points (`- `), each one a complete thought about why the
  change was made or what it protects against — not a restatement of the
  diff. Mention test results when relevant (e.g. "Backend 21/21 and
  frontend 16/16 still pass").
- If an AI agent authored or materially contributed to the commit, end the
  message with a trailing `Co-Authored-By:` line for that agent, exactly as
  the existing commits do.

## Pull request expectations

- **Size:** small enough to review in one sitting. If a PR is touching both
  the game math and an unrelated UI tweak, split it.
- **Must be green before merge:** the `CI` workflow (frontend tests, preview
  freshness, backend tests). There are no required human reviewers (see
  Branch Protection below) — the checks are the gate.
- **What a reviewer (you, later, or an agent) checks:**
  1. If `Formulas.java` changed, did `FitClash.jsx`'s formula block change
     to match, in the same PR? (Invariant 1.)
  2. If `FitClash.jsx` changed, did `frontend/preview/index.html` get
     regenerated and committed? (CI enforces this, but check it wasn't
     worked around.)
  3. Does the PR description say what was tested and how?
  4. Is the diff doing one thing?

## Running everything locally

### Backend

There is no system-wide `mvn` on this machine. A JDK (21) is available via
the VS Code Java extension bundle; point `JAVA_HOME` at it if you need to
run Java tooling directly:

```sh
export JAVA_HOME=/home/rharri24/.vscode-server/extensions/redhat.java-1.56.0-linux-x64/jre/21.0.12.1-linux-x86_64/bin/..
```

Without a local Maven, the backend's test suite is effectively **verified
in CI** (`actions/setup-java@v4` + `mvn -B test`, Temurin 17), not on this
machine. Treat a green backend CI job as the source of truth for backend
changes; don't assume a change is safe just because it compiles in an
editor.

### Frontend

```sh
cd frontend
npm ci
npm test            # vitest run — 16 DOM tests
npm run dev          # local dev server against mock data
```

### Regenerating the preview artifact

`frontend/preview/index.html` is a committed build output, not hand-edited.
Any time `frontend/src/FitClash.jsx` changes, regenerate and commit it:

```sh
cd frontend
node tools/build-preview.mjs
git add preview/index.html
```

CI fails the build if this file is stale (i.e. regenerating it produces a
diff), so forgetting this step blocks the PR rather than silently shipping
a stale preview — but don't rely on CI to catch it; run the command before
you push.

## The two invariants a newcomer WILL break

**1. Game math is implemented twice and must change in lockstep.**
`backend/src/main/java/com/fitclash/service/Formulas.java` and the formula
block in `frontend/src/FitClash.jsx` (search for `xpForNextLevel` and
neighboring functions) encode the *same* game rules independently — the
frontend has its own copy so it can show an optimistic preview without a
round trip to the server. If you change one and not the other, the client
will show the player a number that the server will not honor. **Any PR that
touches one must touch the other, in the same PR, with both test suites
run.** There is no automated check that these two stay in sync today (they
live in different languages and runtimes); this is a manual discipline
until someone writes a cross-language golden-value test for it.

**2. `frontend/preview/index.html` is generated, not hand-written.**
It is produced from `frontend/src/FitClash.jsx` by
`node frontend/tools/build-preview.mjs` and committed so the UI can be
opened with zero tooling (see `DEPLOYING.md`). Editing it directly, or
forgetting to regenerate it after touching `FitClash.jsx`, makes it lie.
CI's frontend job regenerates it and diffs the result against what's
committed, and fails the build on drift — but the fix is still yours to
make and commit, not CI's.

## Working with AI agents on this repo

This project is built and maintained largely by AI coding agents working
under human direction. What has worked in practice:

- **Partition file ownership before starting parallel agents.** Give each
  agent a disjoint set of files/directories it owns and tell it explicitly
  what it must not touch. Two agents editing the same file concurrently is
  the single most reliable way to lose work or create a silent merge
  conflict in intent (not just text).
- **Give the agent the exact verification command, not a description of
  success.** "Make sure the tests pass" is weaker than "run `npx vitest
  run` in `frontend/` and show the output." Agents (and humans skimming
  their output) are much better at following a concrete command than
  inferring one.
- **Make agents prove claims, not assert them.** An agent saying "CI now
  passes" is worth nothing; a workflow run URL with a green conclusion is
  worth everything. Where possible, have the agent push a branch, open a
  PR, and point at the actual check run rather than describing what it
  expects to happen.
- **Tell agents which invariants exist before they touch the relevant
  files** — specifically the two above. An agent with no memory of past
  sessions will not know `Formulas.java` has a frontend twin unless it's
  told, and will not know the preview file is generated unless it's told.
