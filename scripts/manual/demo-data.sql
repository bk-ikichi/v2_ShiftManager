-- 説明書（ヘルプ画面）の撮影用デモデータ。scripts/manual/build.mjs が説明書専用DBに入れる
-- 前提：アプリが一度起動して、テーブルと初期管理者（admin, id=1）が作られていること
-- パスワードは psql の変数 pw で受け取る。日付は実行した日（日本時間）からの相対で作る
-- ユーザーIDは作成順に 1:admin 2:tanaka 3:hanako 4:ken 5:mika 6:yuto 7:taro（build.mjs が画面のURLに使う）
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 指定日より後で最初のサイクル開始日（1日・11日・21日）
CREATE FUNCTION pg_temp.next_cycle(d DATE) RETURNS DATE LANGUAGE sql AS $$
    SELECT CASE
        WHEN EXTRACT(DAY FROM d) < 11 THEN date_trunc('month', d)::date + 10
        WHEN EXTRACT(DAY FROM d) < 21 THEN date_trunc('month', d)::date + 20
        ELSE (date_trunc('month', d) + INTERVAL '1 month')::date
    END
$$;

-- 今日と、次・その次のサイクルの開始日
CREATE TEMP TABLE demo AS SELECT (now() AT TIME ZONE 'Asia/Tokyo')::date AS today;
ALTER TABLE demo ADD COLUMN c1 DATE, ADD COLUMN c2 DATE;
UPDATE demo SET c1 = pg_temp.next_cycle(today);
UPDATE demo SET c2 = pg_temp.next_cycle(c1);

-- ポジション（id 1:キッチン 2:カウンター 3:ドライブスルー）
INSERT INTO positions (name, display_order, color) VALUES
    ('キッチン', 1, 'sky'),
    ('カウンター', 2, 'pink'),
    ('ドライブスルー', 3, 'orange');

-- 初期管理者を店長にし、パスワード変更の強制を外す
UPDATE users SET name = '佐藤 店長', password_hash = crypt(:'pw', gen_salt('bf', 10)),
    must_change_password = FALSE, position_id = 1, display_order = 1
WHERE login_id = 'admin';

-- taro はパスワード変更画面の撮影用に、パスワード変更を必須にしておく
INSERT INTO users (login_id, name, password_hash, position_id, admin, must_change_password, display_order) VALUES
    ('tanaka', '田中 副店長', crypt(:'pw', gen_salt('bf', 10)), 2, TRUE, FALSE, 2),
    ('hanako', '山田 花子', crypt(:'pw', gen_salt('bf', 10)), 2, FALSE, FALSE, 3),
    ('ken', '鈴木 健', crypt(:'pw', gen_salt('bf', 10)), 1, FALSE, FALSE, 4),
    ('mika', '高橋 美香', crypt(:'pw', gen_salt('bf', 10)), 3, FALSE, FALSE, 5),
    ('yuto', '伊藤 悠斗', crypt(:'pw', gen_salt('bf', 10)), 1, FALSE, FALSE, 6),
    ('taro', '中村 太郎', crypt(:'pw', gen_salt('bf', 10)), 2, FALSE, TRUE, 7);

INSERT INTO shift_patterns (user_id, name, start_time, end_time) VALUES
    (3, '朝', '08:00', '13:00'),
    (3, '昼', '11:00', '17:00'),
    (3, '夜', '17:00', '22:00');

-- 各スタッフの勤務時間。偶数日は s1〜e1、奇数日は s2〜e2
CREATE TEMP TABLE work (user_id BIGINT, position_id BIGINT, s1 TIME, e1 TIME, s2 TIME, e2 TIME);
INSERT INTO work VALUES
    (1, 1, '09:00', '18:00', '09:00', '18:00'),
    (2, 2, '13:00', '22:00', '08:00', '17:00'),
    (3, 2, '10:00', '15:00', '17:00', '22:00'),
    (4, 1, '08:00', '14:00', '14:00', '22:00'),
    (5, 3, '11:00', '17:00', '16:00', '23:00'),
    (6, 1, '15:00', '22:00', '09:00', '13:00'),
    (7, 2, '08:00', '12:00', '18:00', '23:00');

-- 確定シフト：今日の3日前〜8日後。花子の5日後は取り消し済みのため入れない
INSERT INTO shifts (work_date, user_id, position_id, start_time, end_time)
SELECT demo.today + o, w.user_id, w.position_id,
       CASE WHEN o % 2 = 0 THEN w.s1 ELSE w.s2 END,
       CASE WHEN o % 2 = 0 THEN w.e1 ELSE w.e2 END
FROM demo, work w, generate_series(-3, 8) AS o
WHERE NOT (w.user_id = 3 AND o = 5);

-- 公開済み：今日の3日前〜6日後（7日後・8日後は下書き、9日後は未登録）
INSERT INTO published_dates (work_date)
SELECT demo.today + o FROM demo, generate_series(-3, 6) AS o;

-- 花子の「変更あり」：2日後は時間の変更、5日後は取り消し
INSERT INTO shift_changes (user_id, work_date, change_type)
SELECT 3, today + 2, 'UPDATED' FROM demo
UNION ALL
SELECT 3, today + 5, 'CANCELLED' FROM demo;

-- 申請：明日〜45日後。4日に1日は申請なし。健は退勤を1時間早く申請し、転記画面に「申請の時間外です」を出す
-- 悠斗は次のサイクルから未提出、美香は次のサイクルを「出勤できない」にする（申請一覧の画像に両方写す）
INSERT INTO shift_requests (user_id, work_date, start_time, end_time, note)
SELECT w.user_id, demo.today + o,
       CASE WHEN o % 2 = 0 THEN w.s1 ELSE w.s2 END,
       CASE WHEN o % 2 = 0 THEN w.e1 ELSE w.e2 END
           - CASE WHEN w.user_id = 4 THEN INTERVAL '1 hour' ELSE INTERVAL '0' END,
       CASE WHEN w.user_id = 3 AND o % 7 = 3 THEN '学校の行事のため、できれば早めに上がりたいです' END
FROM demo, work w, generate_series(1, 45) AS o
WHERE (o + w.user_id) % 4 <> 0
  AND NOT (w.user_id = 6 AND demo.today + o >= demo.c1)
  AND NOT (w.user_id = 5 AND demo.today + o >= demo.c1 AND demo.today + o < demo.c2);

INSERT INTO cycle_unavailable (user_id, cycle_start) SELECT 5, c1 FROM demo;
