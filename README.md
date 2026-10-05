# シフト管理アプリ v2

1店舗専用のシフト管理アプリ。

## 技術構成
Java 17 / Spring Boot 4.1.1 / MyBatis / Spring Security / Thymeleaf / Tailwind CSS 4 / PostgreSQL 17（Neon）/ Docker

## 実装済みの内容
- **Plan 1 基盤**：ログイン・ログイン保持、初期管理者の作成、パスワード変更（初回ログイン時は変更を強制）、ポジション管理、スタッフ管理（登録・編集・パスワードリセット・無効化）、ログイン中のセッションへのユーザー状態の反映、申請締切日数の設定、PWAマニフェスト、Dockerイメージ
- **Plan 2 申請**：サイクル計算、申請パターンの管理（マイページ）、シフト希望の月ごとの一括申請と締切、「この期間は出勤できない」の登録、管理者の申請一覧（未提出の表示）、管理者による申請の代理編集
- **Plan 3 転記・公開**：確定シフトの転記画面（名前候補・申請との差分警告）、転記の登録、シフトの公開（日単位・期間指定）、公開済みの日を編集した際の確認と「変更あり」の記録
- **Plan 4 閲覧**：公開済みシフトの日別一覧、トップ画面（変更あり・次回の出勤・月カレンダー・締切案内・勤務予定時間）、閲覧範囲の制限（スタッフは前月1日以降、カレンダーは2か月後まで）

## ドキュメント
- 仕様書：`documents/2026-09-25-shift-manager-v2-spec.md`
- DB設計：`documents/2026-09-25-db-design.md`
- 実装計画：`docs/superpowers/plans/`

## ローカルでの起動
テストには Docker Desktop が必要（Testcontainers）。

起動には環境変数 `STAFF_INITIAL_PASSWORD`（スタッフ登録で初期パスワードを省略したときの共通の値。半角8〜72文字）が必要。未設定だと起動しない。

```
npm run build
$env:STAFF_INITIAL_PASSWORD = "（共通の初期パスワード）"
./mvnw spring-boot:run
```

## 使い方（取扱説明書）の画像とPDF
アプリ内の使い方（`/help`・`/admin/help`）の画像と、そこからダウンロードできるPDFは、次のコマンドで作り直す。画面を変えたら実行してコミットする。

```
docker compose up -d db
npm run manual
```

- 説明書専用のDB `shiftmanager_manual` を作り直し、アプリを8081で起動してデモデータ（`scripts/manual/demo-data.sql`）を入れ、PCのEdgeで撮影とPDF出力を行う。普段の開発DBには触れない
- デモデータの日付は実行した日からの相対で作るため、実行するたびに画像の日付は変わる
- 失敗したときはアプリのログ `target/manual-app.log` を確認する

## 本番環境（Render + Neon）
- アプリ：Render の Web Service（Language：Docker、Branch：`main`、Region：Singapore）。ルートの `Dockerfile` でビルドする
- DB：Neon（PostgreSQL 17、Region：AWS Asia Pacific (Singapore)）。Connection pooling はオフ（`-pooler` なしのホスト）で接続する
- `main` へのpush（PRのマージ）で自動デプロイされる。DBの変更は `src/main/resources/db/migration/` にマイグレーションを追加すれば起動時に反映される
- 初回起動時、ユーザーが1人もいなければ初期管理者 `admin / admin` が作られる。デプロイ直後にログインしてパスワードを変更すること

### 環境変数（Render で設定する）

| キー | 値 |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://<Neonのホスト>/<DB名>?sslmode=require`（ユーザー名・パスワードは含めない） |
| `DATABASE_USERNAME` | Neon のロール名 |
| `DATABASE_PASSWORD` | Neon のロールのパスワード |
| `REMEMBER_ME_KEY` | ログイン保持Cookieの署名鍵（ランダム文字列）。変更すると全員のログイン保持が無効になる |
| `STAFF_INITIAL_PASSWORD` | スタッフの共通初期パスワード（半角8〜72文字） |
| `PORT` | `8080` |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75` |

Neon の接続情報はコンソールの「Connect」で確認できる（コンソールへのログイン方法とは無関係）。
