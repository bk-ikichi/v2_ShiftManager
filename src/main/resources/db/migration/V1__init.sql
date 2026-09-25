-- ポジション
CREATE TABLE positions (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(30) NOT NULL,
    display_order INT         NOT NULL DEFAULT 0,
    hidden        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- スタッフ（管理者を含む）
CREATE TABLE users (
    id                   BIGSERIAL PRIMARY KEY,
    login_id             VARCHAR(50)  NOT NULL UNIQUE,
    name                 VARCHAR(50)  NOT NULL,
    password_hash        VARCHAR(100) NOT NULL,
    position_id          BIGINT REFERENCES positions (id),
    admin                BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- アプリ設定（常に1行）
CREATE TABLE app_settings (
    id                   INT PRIMARY KEY CHECK (id = 1),
    deadline_days_before INT NOT NULL CHECK (deadline_days_before BETWEEN 0 AND 30)
);
INSERT INTO app_settings (id, deadline_days_before) VALUES (1, 5);

-- 申請パターン（スタッフ個人用）
CREATE TABLE shift_patterns (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(20) NOT NULL,
    start_time TIME        NOT NULL,
    end_time   TIME        NOT NULL,
    CONSTRAINT shift_patterns_time_check CHECK (
        start_time < end_time AND start_time >= TIME '08:00' AND end_time <= TIME '23:00'
        AND EXTRACT(MINUTE FROM start_time) IN (0, 30) AND EXTRACT(SECOND FROM start_time) = 0
        AND EXTRACT(MINUTE FROM end_time) IN (0, 30) AND EXTRACT(SECOND FROM end_time) = 0)
);

-- シフト希望の申請（1人1日1件）
CREATE TABLE shift_requests (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    work_date  DATE         NOT NULL,
    start_time TIME         NOT NULL,
    end_time   TIME         NOT NULL,
    note       VARCHAR(200),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT shift_requests_user_date_key UNIQUE (user_id, work_date),
    CONSTRAINT shift_requests_time_check CHECK (
        start_time < end_time AND start_time >= TIME '08:00' AND end_time <= TIME '23:00'
        AND EXTRACT(MINUTE FROM start_time) IN (0, 30) AND EXTRACT(SECOND FROM start_time) = 0
        AND EXTRACT(MINUTE FROM end_time) IN (0, 30) AND EXTRACT(SECOND FROM end_time) = 0)
);
CREATE INDEX shift_requests_work_date_idx ON shift_requests (work_date);

-- 「この期間は出勤できない」チェック
CREATE TABLE cycle_unavailable (
    user_id     BIGINT NOT NULL REFERENCES users (id),
    cycle_start DATE   NOT NULL,
    PRIMARY KEY (user_id, cycle_start)
);

-- 確定シフト（1人1日1件）
CREATE TABLE shifts (
    id          BIGSERIAL PRIMARY KEY,
    work_date   DATE        NOT NULL,
    user_id     BIGINT      NOT NULL REFERENCES users (id),
    position_id BIGINT      NOT NULL REFERENCES positions (id),
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT shifts_date_user_key UNIQUE (work_date, user_id),
    CONSTRAINT shifts_time_check CHECK (
        start_time < end_time AND start_time >= TIME '08:00' AND end_time <= TIME '23:00'
        AND EXTRACT(MINUTE FROM start_time) IN (0, 30) AND EXTRACT(SECOND FROM start_time) = 0
        AND EXTRACT(MINUTE FROM end_time) IN (0, 30) AND EXTRACT(SECOND FROM end_time) = 0)
);
CREATE INDEX shifts_user_date_idx ON shifts (user_id, work_date);

-- 公開済みの日付
CREATE TABLE published_dates (
    work_date    DATE PRIMARY KEY,
    published_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 公開後の変更マーク
CREATE TABLE shift_changes (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users (id),
    work_date       DATE        NOT NULL,
    change_type     VARCHAR(10) NOT NULL CHECK (change_type IN ('ADDED', 'UPDATED', 'CANCELLED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    acknowledged_at TIMESTAMPTZ
);
-- 未確認の変更は1人1日1件
CREATE UNIQUE INDEX shift_changes_unacked_key ON shift_changes (user_id, work_date)
    WHERE acknowledged_at IS NULL;
