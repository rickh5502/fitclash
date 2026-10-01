-- =====================================================================
--  FitClash - PostgreSQL DDL
--  Agent 5 (Database Architect)
--
--  Design notes
--   * UUID primary keys: friend-shareable ids that leak no user counts.
--   * The anti-cheat thresholds from Agent 2 are re-stated as CHECK
--     constraints. The service layer is the friendly gatekeeper; the
--     database is the one that cannot be bypassed by a rogue client,
--     a bad migration or a future endpoint that forgets to validate.
--   * workout_logs carries a denormalised user_id + workout_date so the
--     "how many sets of this exercise today?" lookup that drives
--     diminishing returns is a single index hit instead of a join.
--   * xp_events is the append-only ledger the daily cap is computed from
--     and the audit trail for any XP dispute.
--
--  Apply with:  psql -d fitclash -f schema.sql
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------
--  users
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    username       varchar(32)  NOT NULL,
    email          varchar(255) NOT NULL,
    password_hash  varchar(100) NOT NULL,
    display_name   varchar(64),
    created_at     timestamptz  NOT NULL DEFAULT now(),
    last_login_at  timestamptz,
    CONSTRAINT uq_users_username     UNIQUE (username),
    CONSTRAINT uq_users_email        UNIQUE (email),
    CONSTRAINT ck_users_username_fmt CHECK (username ~ '^[A-Za-z0-9_]{3,32}$'),
    CONSTRAINT ck_users_email_fmt    CHECK (position('@' in email) > 1)
);

CREATE INDEX IF NOT EXISTS idx_users_username_lower ON users (lower(username));
CREATE INDEX IF NOT EXISTS idx_users_email_lower    ON users (lower(email));

-- ---------------------------------------------------------------------
--  characters  (1:1 with users - the RPG sheet)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS characters (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           uuid        NOT NULL,
    archetype         varchar(32) NOT NULL DEFAULT 'VANGUARD',
    level             integer     NOT NULL DEFAULT 1,
    current_xp        bigint      NOT NULL DEFAULT 0,   -- XP banked inside the current level
    lifetime_xp       bigint      NOT NULL DEFAULT 0,
    str               integer     NOT NULL DEFAULT 5,
    sta               integer     NOT NULL DEFAULT 5,
    con               integer     NOT NULL DEFAULT 5,
    str_points        double precision NOT NULL DEFAULT 0,  -- raw tonnage accumulator
    sta_points        double precision NOT NULL DEFAULT 0,  -- raw endurance accumulator
    best_e1rm_kg      double precision NOT NULL DEFAULT 0,
    current_streak    integer     NOT NULL DEFAULT 0,
    longest_streak    integer     NOT NULL DEFAULT 0,
    last_workout_date date,
    duels_won         integer     NOT NULL DEFAULT 0,
    duels_lost        integer     NOT NULL DEFAULT 0,
    duels_drawn       integer     NOT NULL DEFAULT 0,
    version           bigint      NOT NULL DEFAULT 0,   -- optimistic lock: two devices, one XP grant
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_characters_user  FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_characters_user  UNIQUE (user_id),
    CONSTRAINT ck_characters_level CHECK (level BETWEEN 1 AND 999),
    CONSTRAINT ck_characters_xp    CHECK (current_xp >= 0 AND lifetime_xp >= 0),
    CONSTRAINT ck_characters_stats CHECK (str BETWEEN 1 AND 99
                                      AND sta BETWEEN 1 AND 99
                                      AND con BETWEEN 1 AND 99),
    CONSTRAINT ck_characters_streak CHECK (current_streak >= 0 AND longest_streak >= current_streak)
);

-- The leaderboard's only query, served straight from the index.
CREATE INDEX IF NOT EXISTS idx_characters_rank ON characters (level DESC, lifetime_xp DESC);

-- ---------------------------------------------------------------------
--  workouts  (one gym session)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workouts (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            uuid        NOT NULL,
    workout_date       date        NOT NULL,
    logged_at          timestamptz NOT NULL DEFAULT now(),
    title              varchar(80),
    notes              varchar(500),
    total_sets         integer     NOT NULL DEFAULT 0,
    total_volume_kg    double precision NOT NULL DEFAULT 0,
    duration_minutes   integer     NOT NULL DEFAULT 0,
    xp_raw             bigint      NOT NULL DEFAULT 0,   -- before diminishing returns + cap
    xp_awarded         bigint      NOT NULL DEFAULT 0,   -- what the character actually banked
    daily_cap_reached  boolean     NOT NULL DEFAULT false,
    flagged_for_review boolean     NOT NULL DEFAULT false,
    -- Backdated sessions bank XP but earn no consistency credit.
    counts_for_consistency boolean NOT NULL DEFAULT true,
    CONSTRAINT fk_workouts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_workouts_xp   CHECK (xp_awarded >= 0 AND xp_awarded <= xp_raw),
    CONSTRAINT ck_workouts_sets CHECK (total_sets BETWEEN 0 AND 120),
    CONSTRAINT ck_workouts_date CHECK (workout_date <= (now() AT TIME ZONE 'UTC')::date + 1)
);

CREATE INDEX IF NOT EXISTS idx_workouts_user_date ON workouts (user_id, workout_date DESC);
CREATE INDEX IF NOT EXISTS idx_workouts_logged_at ON workouts (logged_at DESC);
CREATE INDEX IF NOT EXISTS idx_workouts_flagged   ON workouts (flagged_for_review) WHERE flagged_for_review;

-- ---------------------------------------------------------------------
--  workout_logs  (one set / one cardio block)
--  CHECK constraints below are Agent 2's sanity thresholds, enforced by
--  the database so no code path can write a 500 kg x 1000 rep set.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workout_logs (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workout_id     uuid        NOT NULL,
    user_id        uuid        NOT NULL,   -- denormalised for the per-day set count
    workout_date   date        NOT NULL,   -- denormalised for the per-day set count
    exercise_name  varchar(80) NOT NULL,
    exercise_slug  varchar(80) NOT NULL,
    kind           varchar(16) NOT NULL,
    set_index      integer     NOT NULL,   -- 1-based ordinal of this set for this exercise, this day
    reps           integer     NOT NULL DEFAULT 0,
    weight_kg      double precision NOT NULL DEFAULT 0,
    duration_sec   integer     NOT NULL DEFAULT 0,
    intensity      integer     NOT NULL DEFAULT 5,
    volume_kg      double precision NOT NULL DEFAULT 0,
    e1rm_kg        double precision NOT NULL DEFAULT 0,
    xp_raw         double precision NOT NULL DEFAULT 0,
    xp_multiplier  double precision NOT NULL DEFAULT 1,
    xp_awarded     double precision NOT NULL DEFAULT 0,
    flagged        boolean     NOT NULL DEFAULT false,
    flag_reason    varchar(160),
    CONSTRAINT fk_logs_workout    FOREIGN KEY (workout_id) REFERENCES workouts (id) ON DELETE CASCADE,
    CONSTRAINT fk_logs_user       FOREIGN KEY (user_id)    REFERENCES users (id)    ON DELETE CASCADE,
    CONSTRAINT ck_logs_kind       CHECK (kind IN ('STRENGTH', 'BODYWEIGHT', 'CARDIO')),
    CONSTRAINT ck_logs_set_index  CHECK (set_index BETWEEN 1 AND 25),
    CONSTRAINT ck_logs_reps       CHECK (reps BETWEEN 0 AND 150),
    CONSTRAINT ck_logs_weight     CHECK (weight_kg BETWEEN 0 AND 500),
    CONSTRAINT ck_logs_volume     CHECK (volume_kg BETWEEN 0 AND 5000),
    CONSTRAINT ck_logs_duration   CHECK (duration_sec BETWEEN 0 AND 14400),
    CONSTRAINT ck_logs_intensity  CHECK (intensity BETWEEN 1 AND 10),
    CONSTRAINT ck_logs_multiplier CHECK (xp_multiplier > 0 AND xp_multiplier <= 1)
);

CREATE INDEX IF NOT EXISTS idx_logs_workout       ON workout_logs (workout_id);
CREATE INDEX IF NOT EXISTS idx_logs_daily_sets    ON workout_logs (user_id, workout_date, exercise_slug);
CREATE INDEX IF NOT EXISTS idx_logs_user_exercise ON workout_logs (user_id, exercise_slug, workout_date DESC);

-- ---------------------------------------------------------------------
--  friendships  (a duel needs a consenting opponent)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS friendships (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id uuid        NOT NULL,
    addressee_id uuid        NOT NULL,
    status       varchar(16) NOT NULL DEFAULT 'PENDING',
    created_at   timestamptz NOT NULL DEFAULT now(),
    responded_at timestamptz,
    CONSTRAINT fk_friend_requester FOREIGN KEY (requester_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_friend_addressee FOREIGN KEY (addressee_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_friend_pair      UNIQUE (requester_id, addressee_id),
    CONSTRAINT ck_friend_not_self  CHECK (requester_id <> addressee_id),
    CONSTRAINT ck_friend_status    CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'BLOCKED'))
);

CREATE INDEX IF NOT EXISTS idx_friend_addressee ON friendships (addressee_id, status);
CREATE INDEX IF NOT EXISTS idx_friend_requester ON friendships (requester_id, status);

-- One row per pair regardless of who asked first.
CREATE UNIQUE INDEX IF NOT EXISTS uq_friend_unordered_pair
    ON friendships (least(requester_id, addressee_id), greatest(requester_id, addressee_id));

-- ---------------------------------------------------------------------
--  duels  (1v1, fixed window, one metric)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS duels (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    challenger_id    uuid        NOT NULL,
    opponent_id      uuid        NOT NULL,
    metric           varchar(24) NOT NULL DEFAULT 'TOTAL_XP',
    status           varchar(16) NOT NULL DEFAULT 'PENDING',
    stake_xp         integer     NOT NULL DEFAULT 100,
    duration_days    integer     NOT NULL DEFAULT 7,
    rated            boolean     NOT NULL DEFAULT true,
    created_at       timestamptz NOT NULL DEFAULT now(),
    accepted_at      timestamptz,
    starts_at        timestamptz,
    ends_at          timestamptz,
    resolved_at      timestamptz,
    challenger_score double precision NOT NULL DEFAULT 0,
    opponent_score   double precision NOT NULL DEFAULT 0,
    winner_id        uuid,
    resolution_note  varchar(160),
    CONSTRAINT fk_duel_challenger FOREIGN KEY (challenger_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_duel_opponent   FOREIGN KEY (opponent_id)   REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_duel_winner     FOREIGN KEY (winner_id)     REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_duel_not_self   CHECK (challenger_id <> opponent_id),
    CONSTRAINT ck_duel_status     CHECK (status IN ('PENDING','ACTIVE','COMPLETED','DECLINED','EXPIRED','CANCELLED')),
    CONSTRAINT ck_duel_metric     CHECK (metric IN ('TOTAL_XP','TOTAL_VOLUME','TOTAL_SETS','ACTIVE_DAYS')),
    CONSTRAINT ck_duel_stake      CHECK (stake_xp BETWEEN 0 AND 250),
    CONSTRAINT ck_duel_duration   CHECK (duration_days BETWEEN 1 AND 30),
    CONSTRAINT ck_duel_window     CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at),
    CONSTRAINT ck_duel_winner     CHECK (winner_id IS NULL
                                     OR winner_id = challenger_id
                                     OR winner_id = opponent_id)
);

CREATE INDEX IF NOT EXISTS idx_duels_challenger ON duels (challenger_id, status);
CREATE INDEX IF NOT EXISTS idx_duels_opponent   ON duels (opponent_id, status);
-- The resolver sweep: "which active duels have expired?"
CREATE INDEX IF NOT EXISTS idx_duels_sweep      ON duels (status, ends_at) WHERE status = 'ACTIVE';

-- One live duel per pair at a time.
CREATE UNIQUE INDEX IF NOT EXISTS uq_duel_one_live_per_pair
    ON duels (least(challenger_id, opponent_id), greatest(challenger_id, opponent_id))
    WHERE status IN ('PENDING', 'ACTIVE');

-- ---------------------------------------------------------------------
--  xp_events  (append-only ledger: the daily cap reads from here)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS xp_events (
    id                       uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  uuid        NOT NULL,
    event_date               date        NOT NULL,
    source                   varchar(24) NOT NULL,
    amount                   bigint      NOT NULL,
    counts_toward_daily_cap  boolean     NOT NULL DEFAULT true,
    reference_id             uuid,
    created_at               timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_xp_user     FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_xp_source   CHECK (source IN ('WORKOUT','DUEL_WIN','DUEL_DRAW','DUEL_LOSS','ACHIEVEMENT','ADJUSTMENT')),
    CONSTRAINT ck_xp_amount   CHECK (amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_xp_user_date ON xp_events (user_id, event_date);
CREATE INDEX IF NOT EXISTS idx_xp_reference ON xp_events (reference_id);

-- A duel pays out exactly once, however many times the resolver runs.
CREATE UNIQUE INDEX IF NOT EXISTS uq_xp_duel_payout
    ON xp_events (user_id, reference_id, source)
    WHERE source IN ('DUEL_WIN', 'DUEL_DRAW', 'DUEL_LOSS');
