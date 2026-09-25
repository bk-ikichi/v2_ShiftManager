# Plan 1 基盤構築 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **セッション運用：** 1セッション1 Task。Taskの最後のステップ（コミットとチェックボックス更新）が終わったら停止してユーザーに報告する。次のTaskは `/clear` 後の新しいセッションで行う。新しいセッションではこの冒頭（File Structureまで）と、未完了の最初のTaskの範囲だけを読む（`CLAUDE.md` 参照）。

**Goal:** ログイン・パスワード変更・スタッフ管理・ポジション管理・締切設定ができ、Dockerでデプロイ可能な状態のSpringアプリを作る。

**Architecture:** Spring Boot（Spring MVC + Thymeleafのサーバーサイドレンダリング）の単体アプリ。層ごとにパッケージを分け、呼び出しは controller → service → repository → mapper の一方向。MapperはMyBatisのアノテーションSQL、Repositoryは単件取得を `Optional` で返す窓口。認証はSpring Securityのフォームログイン＋セッション＋remember-me（ハッシュベースCookie、10日）。

**Tech Stack:** Java 17、Spring Boot 4.1.1、MyBatis Spring Boot Starter 4.1.0、Spring Security 7、Thymeleaf + thymeleaf-extras-springsecurity6、Flyway、PostgreSQL 17、Tailwind CSS 4（`@tailwindcss/cli`）、Lombok、JUnit 5 + MockMvc + Testcontainers 2

**Spec:** `documents/2026-09-25-shift-manager-v2-spec.md`、DB設計：`documents/2026-09-25-db-design.md`

## Global Constraints

- 画面の文言・コードのコメントはすべて日本語
- Java 17 / Spring Boot 4.1.1 / mybatis-spring-boot-starter 4.1.0 / PostgreSQL 17
- パッケージは層ごと（`controller` / `service` / `repository` / `mapper` / `entity` / `dto` / `form` / `exception` / `auth` / `config` / `util`）。controllerはserviceのみ、serviceはrepositoryのみを呼ぶ
- タイムゾーンは Asia/Tokyo
- ロールは管理者とスタッフの2種。`users.admin = true` のユーザーが `ROLE_ADMIN`、全員が `ROLE_USER`
- 初期管理者：ログインID `admin`、パスワード `admin`、名前「管理者」。初回ログイン時にパスワード変更必須
- ログイン状態の保持：10日間（864000秒）
- スタッフ登録時・管理者によるリセット後は、次回ログイン時にパスワード変更必須
- パスワード：半角英数字記号（ASCII 0x21〜0x7E）8〜72文字（BCryptの72バイト上限による）
- ログインID：前後空白を除去し小文字化して扱う。半角英数字と `.` `_` `-` の3〜50文字
- スタッフは削除せず無効化のみ。自分自身の無効化・自分の管理者権限の解除は不可
- 使用中のポジションは削除不可（非表示のみ）。同名のポジションは登録不可
- 締切日数の初期値5、範囲0〜30
- スタッフ画面はスマートフォン優先のレイアウト
- コミットメッセージの末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` を付ける

## Review Focus

1. ログインIDの大文字・前後空白（スマホの自動大文字化や末尾スペース）：`Taro ` でも `taro` としてログイン・登録・重複判定される → Task 2（ログイン）、Task 5（登録）でテスト
2. 無効化されたスタッフが持っているremember-me Cookie：自動ログインされずログイン画面へ戻る → Task 2でテスト
3. パスワード変更後の古いremember-me Cookie（別端末に残っているもの）：自動ログインされない → Task 3でテスト
4. 全角文字や73文字以上のパスワード：500エラーにならず入力エラーとして表示される → Task 3、Task 5でテスト
5. 同名ポジションの登録：「キッチン」が2つできず、エラーになる → Task 4でテスト
6. 管理者が無効化・パスワードリセット・管理者権限の変更をしたスタッフのログイン中セッション：次のリクエストから反映される（無効化・リセットはログイン画面へ戻る、権限は再ログインなしで変わる） → Task 6でテスト
7. 強制ログアウトされたスタッフ：ログイン画面に理由が表示される。静的ファイルの取得ではDBに問い合わせない → Task 7でテスト

## File Structure

```
pom.xml / mvnw / .mvn/                     Initializrで生成し、MyBatisを追加
compose.yaml                               ローカル用PostgreSQL
package.json                               Tailwind CLI
Dockerfile / .dockerignore
src/main/frontend/app.css                  Tailwind入力
src/main/resources/
  application.yml
  db/migration/V1__init.sql                全テーブル（DB設計書の正本）
  static/manifest.webmanifest, static/icons/icon.svg
  templates/layout.html                    head・header・flashの共通フラグメント
  templates/login.html, password.html, home.html
  templates/admin/positions.html, admin/settings.html
  templates/admin/staff/list.html, new.html, edit.html
src/main/java/jp/bk/shiftmanager/
  ShiftmanagerApplication.java             （生成）
  auth/        SecurityConfig, LoginUser, LoginUserDetailsService, InitialAdminRunner,
               PasswordRules, ForcePasswordChangeInterceptor, UserStateCheckFilter
  config/      WebConfig
  controller/  LoginController, HomeController, PasswordChangeController,
               PositionAdminController, StaffAdminController, SettingsController
  service/     PasswordService, PositionService, StaffService, SettingService
  repository/  UserRepository, PositionRepository, AppSettingRepository
  mapper/      UserMapper, PositionMapper, AppSettingMapper
  entity/      User, Position
  dto/         StaffRow
  form/        PasswordChangeForm, PositionForm, StaffEditForm, StaffCreateForm
  exception/   BusinessException
  util/        LoginIds
src/test/java/jp/bk/shiftmanager/
  TestcontainersConfiguration.java         （生成、イメージをpostgres:17に変更）
  IntegrationTestBase.java, TestData.java, SchemaTest.java, PwaTest.java
  auth/InitialAdminRunnerTest.java, auth/UserStateCheckFilterTest.java
  controller/LoginTest.java, PasswordChangeTest.java, PositionAdminTest.java,
             StaffAdminTest.java, SettingsTest.java
```

---

### Task 1: プロジェクト雛形とDBスキーマ

**Files:**
- Create: Initializr生成一式、`compose.yaml`、`src/main/resources/application.yml`、`src/main/resources/db/migration/V1__init.sql`
- Modify: `pom.xml`（MyBatis追加）、`src/test/java/jp/bk/shiftmanager/TestcontainersConfiguration.java`（イメージ指定）、`.gitignore`
- Delete: `src/main/resources/application.properties`
- Test: `src/test/java/jp/bk/shiftmanager/SchemaTest.java`

**Interfaces:**
- Produces: 全テーブル（DB設計書どおり）、`TestcontainersConfiguration`（package-private、`jp.bk.shiftmanager`）

- [x] **Step 1: gitリポジトリを作成し、Initializrから雛形を取得して展開する**

作業ディレクトリ：`C:\Users\KamataRyunosuke\Desktop\BugerKingProject\v2_ShiftManager`（既存の `CLAUDE.md`・`documents/`・`docs/` は残す）

```bash
git init
curl -s -o "$TEMP/starter.zip" "https://start.spring.io/starter.zip?type=maven-project&language=java&bootVersion=4.1.1&groupId=jp.bk&artifactId=shiftmanager&name=shiftmanager&packageName=jp.bk.shiftmanager&javaVersion=17&dependencies=web,thymeleaf,security,validation,flyway,postgresql,testcontainers,lombok,devtools"
unzip -o "$TEMP/starter.zip" -d .
rm src/main/resources/application.properties
```

展開後に `mvnw`、`pom.xml`、`src/main/java/jp/bk/shiftmanager/ShiftmanagerApplication.java`、`src/test/java/jp/bk/shiftmanager/TestcontainersConfiguration.java` が存在することを確認する。生成された `HELP.md` は削除してよい。

- [x] **Step 2: pom.xmlにMyBatisを追加する**

`<dependencies>` 内、`spring-boot-starter-webmvc` の直後に追加する（InitializrはMyBatisのBoot 4.1対応が未反映のため手動追加。4.1.0はBoot 4.1.0向けにビルドされている）：

```xml
		<dependency>
			<groupId>org.mybatis.spring.boot</groupId>
			<artifactId>mybatis-spring-boot-starter</artifactId>
			<version>4.1.0</version>
		</dependency>
```

- [x] **Step 3: application.yml と compose.yaml を作成し、テスト用コンテナのイメージを指定する**

`src/main/resources/application.yml`：

```yaml
spring:
  application:
    name: shiftmanager
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/shiftmanager}
    username: ${DATABASE_USERNAME:shiftmanager}
    password: ${DATABASE_PASSWORD:shiftmanager}

mybatis:
  configuration:
    map-underscore-to-camel-case: true

app:
  # remember-me Cookieの署名鍵。本番では必ず環境変数で上書きする
  remember-me-key: ${REMEMBER_ME_KEY:dev-only-remember-me-key}
```

`compose.yaml`：

```yaml
services:
  db:
    image: postgres:17
    environment:
      POSTGRES_DB: shiftmanager
      POSTGRES_USER: shiftmanager
      POSTGRES_PASSWORD: shiftmanager
    ports:
      - "5432:5432"
    volumes:
      - dbdata:/var/lib/postgresql/data
volumes:
  dbdata:
```

`TestcontainersConfiguration.java` のコンテナイメージ指定を `postgres:17` に変更する（生成時は `postgres:latest`）：

```java
return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
```

- [x] **Step 4: 失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/SchemaTest.java`：

```java
package jp.bk.shiftmanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SchemaTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void マイグレーションで全テーブルが作成される() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);
        assertThat(tables).contains(
                "positions", "users", "app_settings", "shift_patterns", "shift_requests",
                "cycle_unavailable", "shifts", "published_dates", "shift_changes");
    }

    @Test
    void 締切日数の初期値は5() {
        Integer days = jdbc.queryForObject(
                "SELECT deadline_days_before FROM app_settings WHERE id = 1", Integer.class);
        assertThat(days).isEqualTo(5);
    }
}
```

- [x] **Step 5: テストが失敗することを確認する**

Docker Desktopを起動しておくこと（Testcontainersが使用する）。

Run: `./mvnw test -Dtest=SchemaTest`
Expected: FAIL（テーブルが存在しないためのアサーション失敗、またはSQLエラー）

- [x] **Step 6: マイグレーションを書く**

`src/main/resources/db/migration/V1__init.sql`：

```sql
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
```

- [x] **Step 7: テストが通ることを確認する**

Run: `./mvnw test -Dtest=SchemaTest`
Expected: PASS（2件）

- [x] **Step 8: .gitignoreを追記してコミットする**

`.gitignore`（生成済み）の末尾に追記：

```
# Tailwindのビルド成果物
src/main/resources/static/css/app.css
node_modules/
```

```bash
git add -A
git commit -m "feat: プロジェクト雛形とDBスキーマを作成

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [x] **Step 9: この計画ファイルのTask 1のチェックボックスをすべて `[x]` にしてコミットし、停止してユーザーに報告する**

```bash
git add docs/superpowers/plans/2026-09-25-plan1-foundation.md
git commit -m "docs: Plan 1 Task 1 完了

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: ログイン・ログアウト・ログイン保持・初期管理者

**Files:**
- Create: `package.json`、`src/main/frontend/app.css`
- Create: `entity/User.java`、`mapper/UserMapper.java`、`repository/UserRepository.java`、`util/LoginIds.java`
- Create: `auth/LoginUser.java`、`auth/LoginUserDetailsService.java`、`auth/SecurityConfig.java`、`auth/InitialAdminRunner.java`
- Create: `controller/LoginController.java`、`controller/HomeController.java`
- Create: `src/main/resources/templates/layout.html`、`login.html`、`home.html`
- Test: `src/test/java/jp/bk/shiftmanager/IntegrationTestBase.java`、`TestData.java`、`controller/LoginTest.java`、`auth/InitialAdminRunnerTest.java`

（Javaのパスはすべて `src/main/java/jp/bk/shiftmanager/` からの相対）

**Interfaces:**
- Consumes: Task 1のスキーマ
- Produces:
  - `entity.User`（`Long id, String loginId, String name, String passwordHash, Long positionId, boolean admin, boolean enabled, boolean mustChangePassword`、Lombok `@Data`）
  - `mapper.UserMapper#findById(long): User`、`#findByLoginId(String): User`、`#count(): long`、`#insert(User): void`（id採番）
  - `repository.UserRepository#findById(long): Optional<User>`、`#findByLoginId(String): Optional<User>`、`#count(): long`、`#insert(User): void`
  - `util.LoginIds.normalize(String): String`（strip＋小文字化、null→null）
  - `auth.LoginUser`（`UserDetails`実装。`getId(): long`、`getName(): String`、`isAdmin()`、`isMustChangePassword()`、`static from(User)`、`withPasswordChanged(String hash): LoginUser`）
  - `auth.SecurityConfig.REMEMBER_ME_SECONDS = 864000`、`PasswordEncoder` Bean（BCrypt）
  - テスト：`IntegrationTestBase`（`mvc`、`data`、各テスト前にDB初期化）、`TestData.PASSWORD = "password1"`、`TestData#reset()`、`#user(String loginId, String name, boolean admin): User`、`#login(User): LoginUser`、`#disable(User)`、`#requirePasswordChange(User)`
  - layout.htmlのフラグメント：`head(title)`、`header`、`flash`（`message` / `error` を表示）
  - CSSクラス：`input`、`btn-primary`、`btn-secondary`、`btn-danger`、`card`

- [x] **Step 1: Tailwindを導入する**

`package.json`：

```json
{
  "name": "shiftmanager",
  "private": true,
  "scripts": {
    "build": "tailwindcss -i ./src/main/frontend/app.css -o ./src/main/resources/static/css/app.css --minify",
    "watch": "tailwindcss -i ./src/main/frontend/app.css -o ./src/main/resources/static/css/app.css --watch"
  },
  "devDependencies": {
    "@tailwindcss/cli": "^4.3.3",
    "tailwindcss": "^4.3.3"
  }
}
```

`src/main/frontend/app.css`：

```css
@import "tailwindcss";
@source "../resources/templates";

/* 共通部品 */
@layer components {
  .input {
    @apply mt-1 w-full rounded border border-stone-300 bg-white px-3 py-2 text-base;
  }
  .btn-primary {
    @apply rounded bg-amber-700 px-4 py-2 font-bold text-white hover:bg-amber-800;
  }
  .btn-secondary {
    @apply rounded border border-stone-300 bg-white px-4 py-2 hover:bg-stone-100;
  }
  .btn-danger {
    @apply rounded border border-red-300 bg-white px-4 py-2 text-red-700 hover:bg-red-50;
  }
  .card {
    @apply rounded-lg border border-stone-200 bg-white p-4;
  }
}
```

```bash
npm install
npm run build
```

Expected: `src/main/resources/static/css/app.css` が生成される

- [x] **Step 2: テスト基盤を書く**

`src/test/java/jp/bk/shiftmanager/TestData.java`：

```java
package jp.bk.shiftmanager;

import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** テストデータの作成・初期化 */
@TestComponent
@RequiredArgsConstructor
public class TestData {

    public static final String PASSWORD = "password1";

    private final JdbcTemplate jdbc;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /** 全データを削除し、設定を初期値に戻す */
    public void reset() {
        jdbc.execute("TRUNCATE shift_changes, published_dates, shifts, cycle_unavailable, "
                + "shift_requests, shift_patterns, users, positions RESTART IDENTITY CASCADE");
        jdbc.update("UPDATE app_settings SET deadline_days_before = 5");
    }

    /** パスワード変更済み・有効なスタッフを作成する */
    public User user(String loginId, String name, boolean admin) {
        User user = new User();
        user.setLoginId(loginId);
        user.setName(name);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setAdmin(admin);
        user.setEnabled(true);
        user.setMustChangePassword(false);
        userMapper.insert(user);
        return user;
    }

    /** DBの最新状態からログインユーザーを作る */
    public LoginUser login(User user) {
        return LoginUser.from(userMapper.findById(user.getId()));
    }

    public void disable(User user) {
        jdbc.update("UPDATE users SET enabled = FALSE WHERE id = ?", user.getId());
    }

    public void requirePasswordChange(User user) {
        jdbc.update("UPDATE users SET must_change_password = TRUE WHERE id = ?", user.getId());
    }
}
```

`src/test/java/jp/bk/shiftmanager/IntegrationTestBase.java`：

```java
package jp.bk.shiftmanager;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** DBとMockMvcを使う結合テストの基底クラス。全テストで同じコンテキスト（同じコンテナ）を共有する */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestData.class})
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected TestData data;

    @BeforeEach
    void resetDatabase() {
        data.reset();
    }
}
```

注：Spring Boot 4では `AutoConfigureMockMvc` のパッケージが `org.springframework.boot.webmvc.test.autoconfigure` に移動している。コンパイルエラーになる場合は `spring-boot-webmvc-test` のjar内で正しいパッケージを確認する。

- [x] **Step 3: 失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/controller/LoginTest.java`：

```java
package jp.bk.shiftmanager.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.TestData;
import jp.bk.shiftmanager.entity.User;
import org.junit.jupiter.api.Test;

class LoginTest extends IntegrationTestBase {

    @Test
    void 未ログインでトップにアクセスするとログイン画面へリダイレクトされる() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void ログイン画面は未ログインでも表示できる() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    void 正しいIDとパスワードでログインできる() throws Exception {
        data.user("taro", "山田太郎", false);

        mvc.perform(post("/login").param("loginId", "taro").param("password", TestData.PASSWORD).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("taro"));
    }

    @Test
    void ログインIDは大文字や前後の空白があってもログインできる() throws Exception {
        data.user("taro", "山田太郎", false);

        mvc.perform(post("/login").param("loginId", " Taro ").param("password", TestData.PASSWORD).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("taro"));
    }

    @Test
    void パスワードが違うとログイン画面にエラー付きで戻る() throws Exception {
        data.user("taro", "山田太郎", false);

        mvc.perform(post("/login").param("loginId", "taro").param("password", "wrong-pass").with(csrf()))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void 無効化されたスタッフはログインできない() throws Exception {
        User taro = data.user("taro", "山田太郎", false);
        data.disable(taro);

        mvc.perform(post("/login").param("loginId", "taro").param("password", TestData.PASSWORD).with(csrf()))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void ログイン状態の保持を選ぶと10日間有効なCookieが発行される() throws Exception {
        data.user("taro", "山田太郎", false);

        mvc.perform(post("/login").param("loginId", "taro").param("password", TestData.PASSWORD)
                        .param("remember-me", "on").with(csrf()))
                .andExpect(cookie().maxAge("remember-me", 864000));
    }

    @Test
    void 保持Cookieがあればセッションなしでもアクセスできるが無効化後は使えない() throws Exception {
        User taro = data.user("taro", "山田太郎", false);
        Cookie rememberMe = mvc.perform(post("/login").param("loginId", "taro")
                        .param("password", TestData.PASSWORD).param("remember-me", "on").with(csrf()))
                .andReturn().getResponse().getCookie("remember-me");

        mvc.perform(get("/").cookie(rememberMe)).andExpect(status().isOk());

        data.disable(taro);
        mvc.perform(get("/").cookie(rememberMe))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void 一般スタッフは管理画面にアクセスできない() throws Exception {
        User taro = data.user("taro", "山田太郎", false);

        mvc.perform(get("/admin/staff").with(user(data.login(taro))))
                .andExpect(status().isForbidden());
    }

    @Test
    void ログアウトするとログイン画面へ戻る() throws Exception {
        User taro = data.user("taro", "山田太郎", false);

        mvc.perform(post("/logout").with(user(data.login(taro))).with(csrf()))
                .andExpect(redirectedUrl("/login?logout"));
    }
}
```

`src/test/java/jp/bk/shiftmanager/auth/InitialAdminRunnerTest.java`：

```java
package jp.bk.shiftmanager.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

class InitialAdminRunnerTest extends IntegrationTestBase {

    @Autowired
    InitialAdminRunner runner;

    @Autowired
    UserMapper userMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void ユーザーが1人もいなければ初期管理者を作成する() {
        runner.run(new DefaultApplicationArguments());

        User admin = userMapper.findByLoginId("admin");
        assertThat(admin).isNotNull();
        assertThat(admin.getName()).isEqualTo("管理者");
        assertThat(admin.isAdmin()).isTrue();
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.isMustChangePassword()).isTrue();
        assertThat(passwordEncoder.matches("admin", admin.getPasswordHash())).isTrue();
    }

    @Test
    void ユーザーが既にいれば何もしない() {
        data.user("taro", "山田太郎", false);

        runner.run(new DefaultApplicationArguments());

        assertThat(userMapper.findByLoginId("admin")).isNull();
        assertThat(userMapper.count()).isEqualTo(1);
    }
}
```

- [x] **Step 4: テストが失敗することを確認する**

Run: `./mvnw test -Dtest="LoginTest,InitialAdminRunnerTest"`
Expected: FAIL（コンパイルエラー：`User`、`UserMapper`、`LoginUser`、`InitialAdminRunner` が存在しない）

- [x] **Step 5: エンティティ・Mapper・Repositoryを実装する**

`src/main/java/jp/bk/shiftmanager/entity/User.java`：

```java
package jp.bk.shiftmanager.entity;

import lombok.Data;

/** スタッフ（管理者を含む） */
@Data
public class User {
    private Long id;
    private String loginId;
    private String name;
    private String passwordHash;
    /** 初期ポジション（未設定ならnull） */
    private Long positionId;
    private boolean admin;
    private boolean enabled;
    private boolean mustChangePassword;
}
```

`src/main/java/jp/bk/shiftmanager/util/LoginIds.java`：

```java
package jp.bk.shiftmanager.util;

import java.util.Locale;

/** ログインIDの正規化 */
public final class LoginIds {

    private LoginIds() {
    }

    /** 前後の空白を除去し小文字にする。スマホの自動大文字化・末尾スペース対策 */
    public static String normalize(String loginId) {
        return loginId == null ? null : loginId.strip().toLowerCase(Locale.ROOT);
    }
}
```

`src/main/java/jp/bk/shiftmanager/mapper/UserMapper.java`：

```java
package jp.bk.shiftmanager.mapper;

import jp.bk.shiftmanager.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM users WHERE id = #{id}")
    User findById(@Param("id") long id);

    @Select("SELECT * FROM users WHERE login_id = #{loginId}")
    User findByLoginId(@Param("loginId") String loginId);

    @Select("SELECT COUNT(*) FROM users")
    long count();

    @Insert("""
            INSERT INTO users (login_id, name, password_hash, position_id, admin, enabled, must_change_password)
            VALUES (#{loginId}, #{name}, #{passwordHash}, #{positionId}, #{admin}, #{enabled}, #{mustChangePassword})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(User user);
}
```

`src/main/java/jp/bk/shiftmanager/repository/UserRepository.java`：

```java
package jp.bk.shiftmanager.repository;

import java.util.Optional;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final UserMapper userMapper;

    public Optional<User> findById(long id) {
        return Optional.ofNullable(userMapper.findById(id));
    }

    public Optional<User> findByLoginId(String loginId) {
        return Optional.ofNullable(userMapper.findByLoginId(loginId));
    }

    public long count() {
        return userMapper.count();
    }

    public void insert(User user) {
        userMapper.insert(user);
    }
}
```

- [x] **Step 6: 認証まわりを実装する**

`src/main/java/jp/bk/shiftmanager/auth/LoginUser.java`：

```java
package jp.bk.shiftmanager.auth;

import java.util.Collection;
import java.util.List;
import jp.bk.shiftmanager.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** ログイン中のユーザー（セッションに保存される） */
@Getter
public class LoginUser implements UserDetails {

    private final long id;
    private final String loginId;
    private final String name;
    private final String passwordHash;
    private final boolean admin;
    private final boolean enabled;
    private final boolean mustChangePassword;

    private LoginUser(long id, String loginId, String name, String passwordHash,
            boolean admin, boolean enabled, boolean mustChangePassword) {
        this.id = id;
        this.loginId = loginId;
        this.name = name;
        this.passwordHash = passwordHash;
        this.admin = admin;
        this.enabled = enabled;
        this.mustChangePassword = mustChangePassword;
    }

    public static LoginUser from(User user) {
        return new LoginUser(user.getId(), user.getLoginId(), user.getName(), user.getPasswordHash(),
                user.isAdmin(), user.isEnabled(), user.isMustChangePassword());
    }

    /** パスワード変更後のセッション更新用 */
    public LoginUser withPasswordChanged(String newPasswordHash) {
        return new LoginUser(id, loginId, name, newPasswordHash, admin, enabled, false);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (admin) {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return loginId;
    }
}
```

`src/main/java/jp/bk/shiftmanager/auth/LoginUserDetailsService.java`：

```java
package jp.bk.shiftmanager.auth;

import jp.bk.shiftmanager.repository.UserRepository;
import jp.bk.shiftmanager.util.LoginIds;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String loginId) {
        return userRepository.findByLoginId(LoginIds.normalize(loginId))
                .map(LoginUser::from)
                .orElseThrow(() -> new UsernameNotFoundException("ユーザーが存在しません"));
    }
}
```

`src/main/java/jp/bk/shiftmanager/auth/SecurityConfig.java`：

```java
package jp.bk.shiftmanager.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /** ログイン状態の保持期間（10日） */
    public static final int REMEMBER_ME_SECONDS = 10 * 24 * 60 * 60;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService userDetailsService,
            @Value("${app.remember-me-key}") String rememberMeKey) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/css/**", "/js/**", "/icons/**",
                                "/manifest.webmanifest").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("loginId")
                        .defaultSuccessUrl("/", true)
                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                // パスワードハッシュを含む署名のため、パスワード変更で古いCookieは無効になる
                .rememberMe(remember -> remember
                        .key(rememberMeKey)
                        .tokenValiditySeconds(REMEMBER_ME_SECONDS)
                        .userDetailsService(userDetailsService));
        return http.build();
    }
}
```

`src/main/java/jp/bk/shiftmanager/auth/InitialAdminRunner.java`：

```java
package jp.bk.shiftmanager.auth;

import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** ユーザーが1人もいない場合に初期管理者（admin / admin）を作成する */
@Component
@RequiredArgsConstructor
public class InitialAdminRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        User admin = new User();
        admin.setLoginId("admin");
        admin.setName("管理者");
        admin.setPasswordHash(passwordEncoder.encode("admin"));
        admin.setAdmin(true);
        admin.setEnabled(true);
        // 推測されやすい初期パスワードのため、初回ログインで必ず変更させる
        admin.setMustChangePassword(true);
        userRepository.insert(admin);
    }
}
```

`src/main/java/jp/bk/shiftmanager/controller/LoginController.java`：

```java
package jp.bk.shiftmanager.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
```

`src/main/java/jp/bk/shiftmanager/controller/HomeController.java`：

```java
package jp.bk.shiftmanager.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** トップ画面（Plan 4でスタッフのトップ画面に置き換える） */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "home";
    }
}
```

- [x] **Step 7: テンプレートを実装する**

`src/main/resources/templates/layout.html`：

```html
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security">
<head th:fragment="head(title)">
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title th:text="|${title} - シフト管理|">シフト管理</title>
  <link rel="stylesheet" th:href="@{/css/app.css}">
</head>
<body>
<header th:fragment="header" class="border-b border-stone-200 bg-white">
  <nav class="mx-auto flex max-w-5xl flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3 text-sm">
    <a th:href="@{/}" class="font-bold">シフト管理</a>
    <th:block sec:authorize="hasRole('ADMIN')">
      <a th:href="@{/admin/staff}" class="hover:underline">スタッフ</a>
      <a th:href="@{/admin/positions}" class="hover:underline">ポジション</a>
      <a th:href="@{/admin/settings}" class="hover:underline">設定</a>
    </th:block>
    <span class="ml-auto text-stone-500" sec:authentication="principal.name">名前</span>
    <a th:href="@{/password}" class="hover:underline">パスワード変更</a>
    <form th:action="@{/logout}" method="post">
      <button class="hover:underline">ログアウト</button>
    </form>
  </nav>
</header>
<div th:fragment="flash">
  <p th:if="${message}" th:text="${message}" class="mb-4 rounded bg-green-50 p-3 text-sm text-green-800"></p>
  <p th:if="${error}" th:text="${error}" class="mb-4 rounded bg-red-50 p-3 text-sm text-red-700"></p>
</div>
</body>
</html>
```

`src/main/resources/templates/login.html`：

```html
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout :: head('ログイン')}"></head>
<body class="min-h-screen bg-stone-50 text-stone-900">
<main class="mx-auto max-w-sm px-4 py-12">
  <h1 class="mb-6 text-center text-xl font-bold">シフト管理</h1>
  <p th:if="${param.error}" class="mb-4 rounded bg-red-50 p-3 text-sm text-red-700">ログインIDまたはパスワードが違います</p>
  <p th:if="${param.logout}" class="mb-4 rounded bg-stone-100 p-3 text-sm">ログアウトしました</p>
  <form th:action="@{/login}" method="post" class="card space-y-4">
    <label class="block">
      <span class="text-sm">ログインID</span>
      <input name="loginId" autocomplete="username" autocapitalize="none" required class="input">
    </label>
    <label class="block">
      <span class="text-sm">パスワード</span>
      <input type="password" name="password" autocomplete="current-password" required class="input">
    </label>
    <label class="flex items-center gap-2 text-sm">
      <input type="checkbox" name="remember-me" checked>
      ログイン状態を保持する（10日間）
    </label>
    <button class="btn-primary w-full">ログイン</button>
  </form>
</main>
</body>
</html>
```

`src/main/resources/templates/home.html`：

```html
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout :: head('トップ')}"></head>
<body class="min-h-screen bg-stone-50 text-stone-900">
<header th:replace="~{layout :: header}"></header>
<main class="mx-auto max-w-5xl px-4 py-6">
  <div th:replace="~{layout :: flash}"></div>
  <p class="card">シフト機能は準備中です。</p>
</main>
</body>
</html>
```

- [x] **Step 8: テストが通ることを確認する**

Run: `./mvnw test -Dtest="LoginTest,InitialAdminRunnerTest"`
Expected: PASS（LoginTest 10件、InitialAdminRunnerTest 2件）

- [x] **Step 9: 全テストを実行してコミットする**

Run: `./mvnw test`
Expected: PASS

```bash
git add -A
git commit -m "feat: ログイン・ログアウト・ログイン保持と初期管理者の作成

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [x] **Step 10: この計画ファイルのTask 2のチェックボックスをすべて `[x]` にしてコミットし、停止してユーザーに報告する**

```bash
git add docs/superpowers/plans/2026-09-25-plan1-foundation.md
git commit -m "docs: Plan 1 Task 2 完了

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: パスワード変更と初回変更の強制

**Files:**
- Create: `exception/BusinessException.java`、`config/WebConfig.java`
- Create: `auth/PasswordRules.java`、`auth/ForcePasswordChangeInterceptor.java`
- Create: `form/PasswordChangeForm.java`、`service/PasswordService.java`、`controller/PasswordChangeController.java`
- Create: `src/main/resources/templates/password.html`
- Modify: `mapper/UserMapper.java`、`repository/UserRepository.java`（`updatePassword` 追加）
- Test: `src/test/java/jp/bk/shiftmanager/controller/PasswordChangeTest.java`

（Javaのパスはすべて `src/main/java/jp/bk/shiftmanager/` からの相対）

**Interfaces:**
- Consumes: `UserMapper`、`UserRepository`、`LoginUser`、`TestData`、`IntegrationTestBase`（Task 2）
- Produces:
  - `exception.BusinessException(String message)`（RuntimeException。メッセージはそのまま画面表示する）
  - `auth.PasswordRules.REGEXP = "[\\x21-\\x7E]{8,72}"`、`PasswordRules.MESSAGE`
  - `UserMapper#updatePassword(long id, String hash, boolean mustChange): int`、`UserRepository#updatePassword(long id, String hash, boolean mustChange): void`
  - `service.PasswordService#change(long userId, String currentPassword, String newPassword): String`（新しいハッシュを返す）

- [x] **Step 1: 失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/controller/PasswordChangeTest.java`：

```java
package jp.bk.shiftmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import jakarta.servlet.http.Cookie;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.TestData;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.UserMapper;
import jp.bk.shiftmanager.service.PasswordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordChangeTest extends IntegrationTestBase {

    @Autowired
    UserMapper userMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    PasswordService passwordService;

    @Test
    void パスワード変更が必要なユーザーは他の画面に行くと変更画面へ飛ばされる() throws Exception {
        User taro = data.user("taro", "山田太郎", false);
        data.requirePasswordChange(taro);

        mvc.perform(get("/").with(user(data.login(taro))))
                .andExpect(redirectedUrl("/password"));
        mvc.perform(get("/password").with(user(data.login(taro))))
                .andExpect(status().isOk());
    }

    @Test
    void パスワードを変更すると変更必須が解除され新しいパスワードが有効になる() throws Exception {
        User taro = data.user("taro", "山田太郎", false);
        data.requirePasswordChange(taro);

        mvc.perform(post("/password").with(user(data.login(taro))).with(csrf())
                        .param("currentPassword", TestData.PASSWORD)
                        .param("newPassword", "newpass123")
                        .param("confirmPassword", "newpass123"))
                .andExpect(redirectedUrl("/"));

        User updated = userMapper.findById(taro.getId());
        assertThat(passwordEncoder.matches("newpass123", updated.getPasswordHash())).isTrue();
        assertThat(updated.isMustChangePassword()).isFalse();
    }

    @Test
    void 現在のパスワードが違うと変更できない() throws Exception {
        User taro = data.user("taro", "山田太郎", false);

        mvc.perform(post("/password").with(user(data.login(taro))).with(csrf())
                        .param("currentPassword", "wrong-pass")
                        .param("newPassword", "newpass123")
                        .param("confirmPassword", "newpass123"))
                .andExpect(view().name("password"))
                .andExpect(model().attributeHasErrors("form"));

        assertThat(passwordEncoder.matches(TestData.PASSWORD,
                userMapper.findById(taro.getId()).getPasswordHash())).isTrue();
    }

    @Test
    void 確認用パスワードが一致しないと変更できない() throws Exception {
        User taro = data.user("taro", "山田太郎", false);

        mvc.perform(post("/password").with(user(data.login(taro))).with(csrf())
                        .param("currentPassword", TestData.PASSWORD)
                        .param("newPassword", "newpass123")
                        .param("confirmPassword", "newpass999"))
                .andExpect(view().name("password"))
                .andExpect(model().attributeHasFieldErrors("form", "confirmPassword"));
    }

    @Test
    void 現在と同じパスワードには変更できない() throws Exception {
        User taro = data.user("taro", "山田太郎", false);

        mvc.perform(post("/password").with(user(data.login(taro))).with(csrf())
                        .param("currentPassword", TestData.PASSWORD)
                        .param("newPassword", TestData.PASSWORD)
                        .param("confirmPassword", TestData.PASSWORD))
                .andExpect(view().name("password"))
                .andExpect(model().attributeHasErrors("form"));
    }

    @Test
    void 短すぎる_全角を含む_73文字以上のパスワードは入力エラーになる() throws Exception {
        User taro = data.user("taro", "山田太郎", false);

        for (String invalid : new String[] {"short1", "パスワード１２３４５", "a".repeat(73)}) {
            mvc.perform(post("/password").with(user(data.login(taro))).with(csrf())
                            .param("currentPassword", TestData.PASSWORD)
                            .param("newPassword", invalid)
                            .param("confirmPassword", invalid))
                    .andExpect(status().isOk())
                    .andExpect(view().name("password"))
                    .andExpect(model().attributeHasFieldErrors("form", "newPassword"));
        }
    }

    @Test
    void パスワード変更後は別端末の古い保持Cookieが使えなくなる() throws Exception {
        User taro = data.user("taro", "山田太郎", false);
        Cookie rememberMe = mvc.perform(post("/login").param("loginId", "taro")
                        .param("password", TestData.PASSWORD).param("remember-me", "on").with(csrf()))
                .andReturn().getResponse().getCookie("remember-me");

        passwordService.change(taro.getId(), TestData.PASSWORD, "newpass123");

        mvc.perform(get("/").cookie(rememberMe))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
```

- [x] **Step 2: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=PasswordChangeTest`
Expected: FAIL（コンパイルエラー：`PasswordService` が存在しない）

- [x] **Step 3: 例外・パスワードルール・Mapper/Repositoryを実装する**

`src/main/java/jp/bk/shiftmanager/exception/BusinessException.java`：

```java
package jp.bk.shiftmanager.exception;

/** 業務ルール違反。メッセージはそのまま画面に表示する */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
```

`src/main/java/jp/bk/shiftmanager/auth/PasswordRules.java`：

```java
package jp.bk.shiftmanager.auth;

/** パスワードの入力ルール（BCryptの72バイト上限に収めるため半角のみ） */
public final class PasswordRules {

    public static final String REGEXP = "[\\x21-\\x7E]{8,72}";
    public static final String MESSAGE = "パスワードは半角英数字記号で8〜72文字にしてください";

    private PasswordRules() {
    }
}
```

`mapper/UserMapper.java` に追加（import `org.apache.ibatis.annotations.Update`）：

```java
    @Update("""
            UPDATE users SET password_hash = #{hash}, must_change_password = #{mustChange}, updated_at = now()
            WHERE id = #{id}
            """)
    int updatePassword(@Param("id") long id, @Param("hash") String hash, @Param("mustChange") boolean mustChange);
```

`repository/UserRepository.java` に追加：

```java
    public void updatePassword(long id, String hash, boolean mustChange) {
        userMapper.updatePassword(id, hash, mustChange);
    }
```

- [x] **Step 4: パスワード変更を実装する**

`src/main/java/jp/bk/shiftmanager/form/PasswordChangeForm.java`：

```java
package jp.bk.shiftmanager.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jp.bk.shiftmanager.auth.PasswordRules;
import lombok.Data;

@Data
public class PasswordChangeForm {

    @NotBlank(message = "現在のパスワードを入力してください")
    private String currentPassword;

    @NotBlank(message = "新しいパスワードを入力してください")
    @Pattern(regexp = PasswordRules.REGEXP, message = PasswordRules.MESSAGE)
    private String newPassword;

    @NotBlank(message = "確認用パスワードを入力してください")
    private String confirmPassword;
}
```

`src/main/java/jp/bk/shiftmanager/service/PasswordService.java`：

```java
package jp.bk.shiftmanager.service;

import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** 本人によるパスワード変更。新しいハッシュを返す */
    @Transactional
    public String change(long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("ユーザーが見つかりません"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException("現在のパスワードが違います");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException("現在と同じパスワードは使えません");
        }
        String hash = passwordEncoder.encode(newPassword);
        userRepository.updatePassword(userId, hash, false);
        return hash;
    }
}
```

`src/main/java/jp/bk/shiftmanager/controller/PasswordChangeController.java`：

```java
package jp.bk.shiftmanager.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Objects;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.PasswordChangeForm;
import jp.bk.shiftmanager.service.PasswordService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class PasswordChangeController {

    private final PasswordService passwordService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @GetMapping("/password")
    public String form(Model model) {
        model.addAttribute("form", new PasswordChangeForm());
        return "password";
    }

    @PostMapping("/password")
    public String change(@AuthenticationPrincipal LoginUser me,
            @Valid @ModelAttribute("form") PasswordChangeForm form, BindingResult bindingResult,
            HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        if (!Objects.equals(form.getNewPassword(), form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "mismatch", "確認用パスワードが一致しません");
        }
        if (bindingResult.hasErrors()) {
            return "password";
        }
        String newHash;
        try {
            newHash = passwordService.change(me.getId(), form.getCurrentPassword(), form.getNewPassword());
        } catch (BusinessException e) {
            bindingResult.reject("business", e.getMessage());
            return "password";
        }
        // セッション上のログイン情報を更新し、変更必須を解除する
        LoginUser updated = me.withPasswordChanged(newHash);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(updated, null, updated.getAuthorities()));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        redirectAttributes.addFlashAttribute("message", "パスワードを変更しました");
        return "redirect:/";
    }
}
```

`src/main/java/jp/bk/shiftmanager/auth/ForcePasswordChangeInterceptor.java`：

```java
package jp.bk.shiftmanager.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/** パスワード変更が必要なユーザーを変更画面へ誘導する */
public class ForcePasswordChangeInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser user
                && user.isMustChangePassword()) {
            response.sendRedirect(request.getContextPath() + "/password");
            return false;
        }
        return true;
    }
}
```

`src/main/java/jp/bk/shiftmanager/config/WebConfig.java`：

```java
package jp.bk.shiftmanager.config;

import jp.bk.shiftmanager.auth.ForcePasswordChangeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ForcePasswordChangeInterceptor())
                .excludePathPatterns("/password", "/login", "/error", "/css/**", "/js/**", "/icons/**",
                        "/manifest.webmanifest");
    }
}
```

`src/main/resources/templates/password.html`：

```html
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security">
<head th:replace="~{layout :: head('パスワード変更')}"></head>
<body class="min-h-screen bg-stone-50 text-stone-900">
<header th:replace="~{layout :: header}"></header>
<main class="mx-auto max-w-sm px-4 py-6">
  <h1 class="mb-4 text-lg font-bold">パスワード変更</h1>
  <p sec:authorize="principal.mustChangePassword" class="mb-4 rounded bg-amber-50 p-3 text-sm text-amber-900">
    初回ログインのため、パスワードを変更してください。
  </p>
  <form th:action="@{/password}" th:object="${form}" method="post" class="card space-y-4">
    <p th:each="err : ${#fields.globalErrors()}" th:text="${err}" class="rounded bg-red-50 p-3 text-sm text-red-700"></p>
    <label class="block">
      <span class="text-sm">現在のパスワード</span>
      <input type="password" th:field="*{currentPassword}" autocomplete="current-password" class="input">
      <span th:errors="*{currentPassword}" class="text-sm text-red-700"></span>
    </label>
    <label class="block">
      <span class="text-sm">新しいパスワード（半角英数字記号 8〜72文字）</span>
      <input type="password" th:field="*{newPassword}" autocomplete="new-password" class="input">
      <span th:errors="*{newPassword}" class="text-sm text-red-700"></span>
    </label>
    <label class="block">
      <span class="text-sm">新しいパスワード（確認）</span>
      <input type="password" th:field="*{confirmPassword}" autocomplete="new-password" class="input">
      <span th:errors="*{confirmPassword}" class="text-sm text-red-700"></span>
    </label>
    <button class="btn-primary w-full">変更する</button>
  </form>
</main>
</body>
</html>
```

- [x] **Step 5: テストが通ることを確認する**

Run: `./mvnw test -Dtest=PasswordChangeTest`
Expected: PASS（7件）

- [x] **Step 6: 全テストを実行してコミットする**

Run: `./mvnw test`
Expected: PASS

```bash
git add -A
git commit -m "feat: パスワード変更と初回ログイン時の変更強制

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [x] **Step 7: この計画ファイルのTask 3のチェックボックスをすべて `[x]` にしてコミットし、停止してユーザーに報告する**

```bash
git add docs/superpowers/plans/2026-09-25-plan1-foundation.md
git commit -m "docs: Plan 1 Task 3 完了

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: ポジション管理

**Files:**
- Create: `entity/Position.java`、`mapper/PositionMapper.java`、`repository/PositionRepository.java`
- Create: `form/PositionForm.java`、`service/PositionService.java`、`controller/PositionAdminController.java`
- Create: `src/main/resources/templates/admin/positions.html`
- Modify: `src/test/java/jp/bk/shiftmanager/TestData.java`（`position`、`assignPosition` 追加）
- Test: `src/test/java/jp/bk/shiftmanager/controller/PositionAdminTest.java`

（Javaのパスはすべて `src/main/java/jp/bk/shiftmanager/` からの相対）

**Interfaces:**
- Consumes: `BusinessException`（Task 3）、`TestData`、`IntegrationTestBase`（Task 2）
- Produces:
  - `entity.Position`（`Long id, String name, int displayOrder, boolean hidden`、`@Data`）
  - `mapper.PositionMapper#findAll(): List<Position>`（display_order, id 昇順）、`#findVisible(): List<Position>`、`#findById(long): Position`、`#insert(Position)`、`#update(Position): int`、`#delete(long): int`、`#countUsage(long): long`、`#countByName(String name, long excludeId): long`
  - `repository.PositionRepository#findAll(): List<Position>`、`#findVisible(): List<Position>`、`#findById(long): Optional<Position>`、`#insert(Position)`、`#update(Position)`、`#delete(long)`、`#isUsed(long): boolean`、`#existsName(String name, long excludeId): boolean`
  - `service.PositionService#findAll(): List<Position>`、`#create(PositionForm)`、`#update(long, PositionForm)`、`#delete(long)`
  - `TestData#position(String name, int displayOrder): Position`、`#assignPosition(User, Position)`

- [x] **Step 1: TestDataに追加する**

`TestData.java` にフィールドとメソッドを追加（import `jp.bk.shiftmanager.entity.Position`、`jp.bk.shiftmanager.mapper.PositionMapper`）：

```java
    private final PositionMapper positionMapper;

    public Position position(String name, int displayOrder) {
        Position position = new Position();
        position.setName(name);
        position.setDisplayOrder(displayOrder);
        positionMapper.insert(position);
        return position;
    }

    public void assignPosition(User user, Position position) {
        jdbc.update("UPDATE users SET position_id = ? WHERE id = ?", position.getId(), user.getId());
        user.setPositionId(position.getId());
    }
```

- [x] **Step 2: 失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/controller/PositionAdminTest.java`：

```java
package jp.bk.shiftmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.PositionMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PositionAdminTest extends IntegrationTestBase {

    @Autowired
    PositionMapper positionMapper;

    LoginUser admin;

    @BeforeEach
    void setUp() {
        admin = data.login(data.user("boss", "店長", true));
    }

    @Test
    void 一覧画面を表示できる() throws Exception {
        data.position("キッチン", 2);

        mvc.perform(get("/admin/positions").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("キッチン")));
    }

    @Test
    void 追加すると表示順で並ぶ() throws Exception {
        mvc.perform(post("/admin/positions").with(user(admin)).with(csrf())
                        .param("name", "カウンター").param("displayOrder", "3"))
                .andExpect(redirectedUrl("/admin/positions"));
        mvc.perform(post("/admin/positions").with(user(admin)).with(csrf())
                        .param("name", "社員").param("displayOrder", "1"))
                .andExpect(redirectedUrl("/admin/positions"));

        List<Position> all = positionMapper.findAll();
        assertThat(all).extracting(Position::getName).containsExactly("社員", "カウンター");
    }

    @Test
    void 名前が空だと追加されない() throws Exception {
        mvc.perform(post("/admin/positions").with(user(admin)).with(csrf())
                        .param("name", " ").param("displayOrder", "1"))
                .andExpect(redirectedUrl("/admin/positions"))
                .andExpect(flash().attributeExists("error"));

        assertThat(positionMapper.findAll()).isEmpty();
    }

    @Test
    void 同じ名前のポジションは追加できない() throws Exception {
        data.position("キッチン", 1);

        mvc.perform(post("/admin/positions").with(user(admin)).with(csrf())
                        .param("name", "キッチン").param("displayOrder", "2"))
                .andExpect(redirectedUrl("/admin/positions"))
                .andExpect(flash().attribute("error", "同じ名前のポジションが既にあります"));

        assertThat(positionMapper.findAll()).hasSize(1);
    }

    @Test
    void 名前_表示順_非表示を更新できる() throws Exception {
        Position kitchen = data.position("キッチン", 1);

        mvc.perform(post("/admin/positions/{id}", kitchen.getId()).with(user(admin)).with(csrf())
                        .param("name", "キッチン（夜）").param("displayOrder", "5").param("hidden", "true"))
                .andExpect(redirectedUrl("/admin/positions"));

        Position updated = positionMapper.findById(kitchen.getId());
        assertThat(updated.getName()).isEqualTo("キッチン（夜）");
        assertThat(updated.getDisplayOrder()).isEqualTo(5);
        assertThat(updated.isHidden()).isTrue();
    }

    @Test
    void 非表示のチェックを外すと表示に戻る() throws Exception {
        Position kitchen = data.position("キッチン", 1);
        kitchen.setHidden(true);
        positionMapper.update(kitchen);

        // チェックボックス未選択時はSpringのマーカー（_hidden）だけが送られる
        mvc.perform(post("/admin/positions/{id}", kitchen.getId()).with(user(admin)).with(csrf())
                        .param("name", "キッチン").param("displayOrder", "1").param("_hidden", "on"))
                .andExpect(redirectedUrl("/admin/positions"));

        assertThat(positionMapper.findById(kitchen.getId()).isHidden()).isFalse();
    }

    @Test
    void 未使用のポジションは削除できる() throws Exception {
        Position kitchen = data.position("キッチン", 1);

        mvc.perform(post("/admin/positions/{id}/delete", kitchen.getId()).with(user(admin)).with(csrf()))
                .andExpect(redirectedUrl("/admin/positions"));

        assertThat(positionMapper.findById(kitchen.getId())).isNull();
    }

    @Test
    void スタッフが使用中のポジションは削除できない() throws Exception {
        Position kitchen = data.position("キッチン", 1);
        User taro = data.user("taro", "山田太郎", false);
        data.assignPosition(taro, kitchen);

        mvc.perform(post("/admin/positions/{id}/delete", kitchen.getId()).with(user(admin)).with(csrf()))
                .andExpect(redirectedUrl("/admin/positions"))
                .andExpect(flash().attribute("error", "使用中のため削除できません。非表示にしてください"));

        assertThat(positionMapper.findById(kitchen.getId())).isNotNull();
    }

    @Test
    void 一般スタッフは操作できない() throws Exception {
        LoginUser staff = data.login(data.user("taro", "山田太郎", false));

        mvc.perform(post("/admin/positions").with(user(staff)).with(csrf())
                        .param("name", "キッチン").param("displayOrder", "1"))
                .andExpect(status().isForbidden());
    }
}
```

- [x] **Step 3: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=PositionAdminTest`
Expected: FAIL（コンパイルエラー：`Position`、`PositionMapper` が存在しない）

- [x] **Step 4: エンティティ・Mapper・Repositoryを実装する**

`src/main/java/jp/bk/shiftmanager/entity/Position.java`：

```java
package jp.bk.shiftmanager.entity;

import lombok.Data;

/** ポジション（転記画面・日別一覧のグループ分けに使う） */
@Data
public class Position {
    private Long id;
    private String name;
    /** 上からの並び順（昇順） */
    private int displayOrder;
    /** 使用中で削除できないポジションを選択肢から外す */
    private boolean hidden;
}
```

`src/main/java/jp/bk/shiftmanager/mapper/PositionMapper.java`：

```java
package jp.bk.shiftmanager.mapper;

import java.util.List;
import jp.bk.shiftmanager.entity.Position;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PositionMapper {

    @Select("SELECT * FROM positions ORDER BY display_order, id")
    List<Position> findAll();

    @Select("SELECT * FROM positions WHERE hidden = FALSE ORDER BY display_order, id")
    List<Position> findVisible();

    @Select("SELECT * FROM positions WHERE id = #{id}")
    Position findById(@Param("id") long id);

    @Insert("INSERT INTO positions (name, display_order, hidden) VALUES (#{name}, #{displayOrder}, #{hidden})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(Position position);

    @Update("UPDATE positions SET name = #{name}, display_order = #{displayOrder}, hidden = #{hidden} WHERE id = #{id}")
    int update(Position position);

    @Delete("DELETE FROM positions WHERE id = #{id}")
    int delete(@Param("id") long id);

    /** スタッフの初期ポジションまたはシフトの枠として使われている件数 */
    @Select("""
            SELECT (SELECT COUNT(*) FROM users WHERE position_id = #{id})
                 + (SELECT COUNT(*) FROM shifts WHERE position_id = #{id})
            """)
    long countUsage(@Param("id") long id);

    /** 同名のポジション数（excludeIdは更新時の自分自身を除外するため。新規時は0） */
    @Select("SELECT COUNT(*) FROM positions WHERE name = #{name} AND id <> #{excludeId}")
    long countByName(@Param("name") String name, @Param("excludeId") long excludeId);
}
```

`src/main/java/jp/bk/shiftmanager/repository/PositionRepository.java`：

```java
package jp.bk.shiftmanager.repository;

import java.util.List;
import java.util.Optional;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.mapper.PositionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PositionRepository {

    private final PositionMapper positionMapper;

    public List<Position> findAll() {
        return positionMapper.findAll();
    }

    public List<Position> findVisible() {
        return positionMapper.findVisible();
    }

    public Optional<Position> findById(long id) {
        return Optional.ofNullable(positionMapper.findById(id));
    }

    public void insert(Position position) {
        positionMapper.insert(position);
    }

    public void update(Position position) {
        positionMapper.update(position);
    }

    public void delete(long id) {
        positionMapper.delete(id);
    }

    /** スタッフの初期ポジションまたはシフトの枠として使われているか */
    public boolean isUsed(long id) {
        return positionMapper.countUsage(id) > 0;
    }

    public boolean existsName(String name, long excludeId) {
        return positionMapper.countByName(name, excludeId) > 0;
    }
}
```

- [x] **Step 5: フォーム・サービス・コントローラー・テンプレートを実装する**

`src/main/java/jp/bk/shiftmanager/form/PositionForm.java`：

```java
package jp.bk.shiftmanager.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PositionForm {

    @NotBlank(message = "名前を入力してください")
    @Size(max = 30, message = "名前は30文字以内で入力してください")
    private String name;

    @NotNull(message = "表示順を入力してください")
    @Min(value = 0, message = "表示順は0〜999で入力してください")
    @Max(value = 999, message = "表示順は0〜999で入力してください")
    private Integer displayOrder;

    private boolean hidden;

    public void setName(String name) {
        this.name = name == null ? null : name.strip();
    }
}
```

`src/main/java/jp/bk/shiftmanager/service/PositionService.java`：

```java
package jp.bk.shiftmanager.service;

import java.util.List;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.PositionForm;
import jp.bk.shiftmanager.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionRepository positionRepository;

    public List<Position> findAll() {
        return positionRepository.findAll();
    }

    @Transactional
    public void create(PositionForm form) {
        checkNameUnique(form.getName(), 0);
        Position position = new Position();
        position.setName(form.getName());
        position.setDisplayOrder(form.getDisplayOrder());
        position.setHidden(form.isHidden());
        positionRepository.insert(position);
    }

    @Transactional
    public void update(long id, PositionForm form) {
        Position position = find(id);
        checkNameUnique(form.getName(), id);
        position.setName(form.getName());
        position.setDisplayOrder(form.getDisplayOrder());
        position.setHidden(form.isHidden());
        positionRepository.update(position);
    }

    @Transactional
    public void delete(long id) {
        find(id);
        if (positionRepository.isUsed(id)) {
            throw new BusinessException("使用中のため削除できません。非表示にしてください");
        }
        positionRepository.delete(id);
    }

    private Position find(long id) {
        return positionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("ポジションが見つかりません"));
    }

    private void checkNameUnique(String name, long excludeId) {
        if (positionRepository.existsName(name, excludeId)) {
            throw new BusinessException("同じ名前のポジションが既にあります");
        }
    }
}
```

`src/main/java/jp/bk/shiftmanager/controller/PositionAdminController.java`：

```java
package jp.bk.shiftmanager.controller;

import jakarta.validation.Valid;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.PositionForm;
import jp.bk.shiftmanager.service.PositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/positions")
@RequiredArgsConstructor
public class PositionAdminController {

    private static final String REDIRECT = "redirect:/admin/positions";

    private final PositionService positionService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("positions", positionService.findAll());
        return "admin/positions";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute PositionForm form, BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return REDIRECT;
        }
        try {
            positionService.create(form);
            redirectAttributes.addFlashAttribute("message", "追加しました");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return REDIRECT;
    }

    @PostMapping("/{id}")
    public String update(@PathVariable long id, @Valid @ModelAttribute PositionForm form,
            BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return REDIRECT;
        }
        try {
            positionService.update(id, form);
            redirectAttributes.addFlashAttribute("message", "保存しました");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return REDIRECT;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes redirectAttributes) {
        try {
            positionService.delete(id);
            redirectAttributes.addFlashAttribute("message", "削除しました");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return REDIRECT;
    }
}
```

`src/main/resources/templates/admin/positions.html`：

```html
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout :: head('ポジション管理')}"></head>
<body class="min-h-screen bg-stone-50 text-stone-900">
<header th:replace="~{layout :: header}"></header>
<main class="mx-auto max-w-3xl px-4 py-6">
  <h1 class="mb-4 text-lg font-bold">ポジション管理</h1>
  <div th:replace="~{layout :: flash}"></div>
  <p class="mb-4 text-sm text-stone-600">表示順の小さいものから、転記画面・日別一覧の上に並びます。使用中のポジションは削除できないため、不要になったら非表示にしてください。</p>

  <div class="card mb-6 space-y-3">
    <div th:each="p : ${positions}" class="flex flex-wrap items-end gap-2 border-b border-stone-100 pb-3">
      <form th:action="@{/admin/positions/{id}(id=${p.id})}" method="post" class="flex flex-wrap items-end gap-2">
        <label class="block">
          <span class="text-xs">名前</span>
          <input name="name" th:value="${p.name}" maxlength="30" required class="input w-40">
        </label>
        <label class="block">
          <span class="text-xs">表示順</span>
          <input type="number" name="displayOrder" th:value="${p.displayOrder}" min="0" max="999" required class="input w-20">
        </label>
        <label class="flex items-center gap-1 pb-2 text-sm">
          <input type="checkbox" name="hidden" value="true" th:checked="${p.hidden}">
          <input type="hidden" name="_hidden" value="on">
          非表示
        </label>
        <button class="btn-secondary">保存</button>
      </form>
      <form th:action="@{/admin/positions/{id}/delete(id=${p.id})}" method="post">
        <button class="btn-danger">削除</button>
      </form>
    </div>
    <p th:if="${#lists.isEmpty(positions)}" class="text-sm text-stone-500">ポジションがまだありません。</p>
  </div>

  <h2 class="mb-2 font-bold">追加</h2>
  <form th:action="@{/admin/positions}" method="post" class="card flex flex-wrap items-end gap-2">
    <label class="block">
      <span class="text-xs">名前</span>
      <input name="name" maxlength="30" required class="input w-40" placeholder="例：キッチン">
    </label>
    <label class="block">
      <span class="text-xs">表示順</span>
      <input type="number" name="displayOrder" min="0" max="999" value="0" required class="input w-20">
    </label>
    <button class="btn-primary">追加する</button>
  </form>
</main>
</body>
</html>
```

- [x] **Step 6: テストが通ることを確認する**

Run: `./mvnw test -Dtest=PositionAdminTest`
Expected: PASS（9件）

- [x] **Step 7: 全テストを実行してコミットする**

Run: `./mvnw test`
Expected: PASS

```bash
git add -A
git commit -m "feat: ポジション管理

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [x] **Step 8: この計画ファイルのTask 4のチェックボックスをすべて `[x]` にしてコミットし、停止してユーザーに報告する**

```bash
git add docs/superpowers/plans/2026-09-25-plan1-foundation.md
git commit -m "docs: Plan 1 Task 4 完了

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: スタッフ管理

**Files:**
- Create: `dto/StaffRow.java`
- Create: `form/StaffEditForm.java`、`form/StaffCreateForm.java`、`service/StaffService.java`、`controller/StaffAdminController.java`
- Create: `src/main/resources/templates/admin/staff/list.html`、`admin/staff/new.html`、`admin/staff/edit.html`
- Modify: `mapper/UserMapper.java`、`repository/UserRepository.java`（スタッフ一覧・更新系を追加）
- Test: `src/test/java/jp/bk/shiftmanager/controller/StaffAdminTest.java`

（Javaのパスはすべて `src/main/java/jp/bk/shiftmanager/` からの相対）

**Interfaces:**
- Consumes: `UserMapper`・`UserRepository`（Task 2・3）、`PositionRepository`（Task 4）、`PositionService#findAll()`（Task 4）、`PasswordRules`・`BusinessException`（Task 3）、`LoginIds`（Task 2）、`TestData#position`・`#assignPosition`（Task 4）
- Produces:
  - `dto.StaffRow`（`Long id, String loginId, String name, String positionName, boolean admin, boolean enabled`）
  - `UserMapper#findStaffRows(): List<StaffRow>`、`#updateProfile(User): int`、`#updateEnabled(long id, boolean enabled): int`、`#countByLoginId(String loginId, long excludeId): long`
  - `UserRepository#findStaffRows(): List<StaffRow>`、`#updateProfile(User)`、`#updateEnabled(long, boolean)`、`#existsLoginId(String loginId, long excludeId): boolean`
  - `service.StaffService#findAll(): List<StaffRow>`、`#create(StaffCreateForm): long`、`#editForm(long): StaffEditForm`、`#update(long id, StaffEditForm, long actorId)`、`#resetPassword(long id, String tempPassword)`、`#setEnabled(long id, boolean enabled, long actorId)`

- [x] **Step 1: 失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/controller/StaffAdminTest.java`：

```java
package jp.bk.shiftmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.UserMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

class StaffAdminTest extends IntegrationTestBase {

    @Autowired
    UserMapper userMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    User bossUser;
    LoginUser boss;

    @BeforeEach
    void setUp() {
        bossUser = data.user("boss", "店長", true);
        boss = data.login(bossUser);
    }

    @Test
    void 一覧に名前とポジションが表示される() throws Exception {
        Position kitchen = data.position("キッチン", 1);
        User taro = data.user("taro", "山田太郎", false);
        data.assignPosition(taro, kitchen);

        mvc.perform(get("/admin/staff").with(user(boss)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("山田太郎")))
                .andExpect(content().string(Matchers.containsString("キッチン")));
    }

    @Test
    void 登録したスタッフは初期パスワードでログインでき初回に変更が必要になる() throws Exception {
        Position kitchen = data.position("キッチン", 1);

        mvc.perform(post("/admin/staff").with(user(boss)).with(csrf())
                        .param("loginId", "hanako").param("name", "佐藤花子")
                        .param("positionId", kitchen.getId().toString())
                        .param("password", "initpass1"))
                .andExpect(redirectedUrl("/admin/staff"));

        User hanako = userMapper.findByLoginId("hanako");
        assertThat(hanako.getName()).isEqualTo("佐藤花子");
        assertThat(hanako.getPositionId()).isEqualTo(kitchen.getId());
        assertThat(hanako.isAdmin()).isFalse();
        assertThat(hanako.isEnabled()).isTrue();
        assertThat(hanako.isMustChangePassword()).isTrue();
        assertThat(passwordEncoder.matches("initpass1", hanako.getPasswordHash())).isTrue();
    }

    @Test
    void ログインIDは小文字化され前後の空白が除かれる() throws Exception {
        mvc.perform(post("/admin/staff").with(user(boss)).with(csrf())
                        .param("loginId", " Hanako ").param("name", "佐藤花子").param("password", "initpass1"))
                .andExpect(redirectedUrl("/admin/staff"));

        assertThat(userMapper.findByLoginId("hanako")).isNotNull();
    }

    @Test
    void 大文字小文字違いを含め既存と同じログインIDは登録できない() throws Exception {
        data.user("taro", "山田太郎", false);

        mvc.perform(post("/admin/staff").with(user(boss)).with(csrf())
                        .param("loginId", "TARO").param("name", "別の太郎").param("password", "initpass1"))
                .andExpect(view().name("admin/staff/new"))
                .andExpect(model().attributeHasErrors("form"));

        assertThat(userMapper.count()).isEqualTo(2);
    }

    @Test
    void ログインIDの形式が不正だと登録できない() throws Exception {
        mvc.perform(post("/admin/staff").with(user(boss)).with(csrf())
                        .param("loginId", "た ろう").param("name", "山田太郎").param("password", "initpass1"))
                .andExpect(view().name("admin/staff/new"))
                .andExpect(model().attributeHasFieldErrors("form", "loginId"));
    }

    @Test
    void 全角を含む初期パスワードは登録できない() throws Exception {
        mvc.perform(post("/admin/staff").with(user(boss)).with(csrf())
                        .param("loginId", "hanako").param("name", "佐藤花子").param("password", "パスワード１２３"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/staff/new"))
                .andExpect(model().attributeHasFieldErrors("form", "password"));
    }

    @Test
    void 名前_ログインID_ポジション_管理者権限を編集できる() throws Exception {
        Position counter = data.position("カウンター", 2);
        User taro = data.user("taro", "山田太郎", false);

        mvc.perform(post("/admin/staff/{id}", taro.getId()).with(user(boss)).with(csrf())
                        .param("loginId", "taro2").param("name", "山田太郎（社員）")
                        .param("positionId", counter.getId().toString()).param("admin", "true"))
                .andExpect(redirectedUrl("/admin/staff"));

        User updated = userMapper.findById(taro.getId());
        assertThat(updated.getLoginId()).isEqualTo("taro2");
        assertThat(updated.getName()).isEqualTo("山田太郎（社員）");
        assertThat(updated.getPositionId()).isEqualTo(counter.getId());
        assertThat(updated.isAdmin()).isTrue();
    }

    @Test
    void 自分の管理者権限は外せない() throws Exception {
        mvc.perform(post("/admin/staff/{id}", bossUser.getId()).with(user(boss)).with(csrf())
                        .param("loginId", "boss").param("name", "店長").param("_admin", "on"))
                .andExpect(view().name("admin/staff/edit"))
                .andExpect(model().attributeHasErrors("form"));

        assertThat(userMapper.findById(bossUser.getId()).isAdmin()).isTrue();
    }

    @Test
    void パスワードをリセットすると仮パスワードが有効になり次回変更が必要になる() throws Exception {
        User taro = data.user("taro", "山田太郎", false);

        mvc.perform(post("/admin/staff/{id}/password", taro.getId()).with(user(boss)).with(csrf())
                        .param("tempPassword", "temppass1"))
                .andExpect(redirectedUrl("/admin/staff/" + taro.getId() + "/edit"));

        User updated = userMapper.findById(taro.getId());
        assertThat(passwordEncoder.matches("temppass1", updated.getPasswordHash())).isTrue();
        assertThat(updated.isMustChangePassword()).isTrue();
    }

    @Test
    void 仮パスワードが短いとリセットされない() throws Exception {
        User taro = data.user("taro", "山田太郎", false);

        mvc.perform(post("/admin/staff/{id}/password", taro.getId()).with(user(boss)).with(csrf())
                        .param("tempPassword", "short"))
                .andExpect(flash().attributeExists("error"));

        assertThat(userMapper.findById(taro.getId()).isMustChangePassword()).isFalse();
    }

    @Test
    void 無効化と有効化ができる() throws Exception {
        User taro = data.user("taro", "山田太郎", false);

        mvc.perform(post("/admin/staff/{id}/enabled", taro.getId()).with(user(boss)).with(csrf())
                        .param("enabled", "false"))
                .andExpect(redirectedUrl("/admin/staff"));
        assertThat(userMapper.findById(taro.getId()).isEnabled()).isFalse();

        mvc.perform(post("/admin/staff/{id}/enabled", taro.getId()).with(user(boss)).with(csrf())
                        .param("enabled", "true"))
                .andExpect(redirectedUrl("/admin/staff"));
        assertThat(userMapper.findById(taro.getId()).isEnabled()).isTrue();
    }

    @Test
    void 自分自身は無効化できない() throws Exception {
        mvc.perform(post("/admin/staff/{id}/enabled", bossUser.getId()).with(user(boss)).with(csrf())
                        .param("enabled", "false"))
                .andExpect(flash().attribute("error", "自分自身は無効化できません"));

        assertThat(userMapper.findById(bossUser.getId()).isEnabled()).isTrue();
    }

    @Test
    void 一般スタッフはスタッフ管理を使えない() throws Exception {
        LoginUser staff = data.login(data.user("taro", "山田太郎", false));

        mvc.perform(get("/admin/staff").with(user(staff))).andExpect(status().isForbidden());
    }
}
```

- [x] **Step 2: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=StaffAdminTest`
Expected: FAIL（`/admin/staff` が404のため多数失敗）

- [x] **Step 3: DTO・Mapper・Repositoryを拡張する**

`src/main/java/jp/bk/shiftmanager/dto/StaffRow.java`：

```java
package jp.bk.shiftmanager.dto;

import lombok.Data;

/** スタッフ一覧の1行 */
@Data
public class StaffRow {
    private Long id;
    private String loginId;
    private String name;
    private String positionName;
    private boolean admin;
    private boolean enabled;
}
```

`mapper/UserMapper.java` に追加（import `java.util.List`、`jp.bk.shiftmanager.dto.StaffRow`）：

```java
    /** 有効なスタッフが先、ポジションの表示順、名前の順 */
    @Select("""
            SELECT u.id, u.login_id, u.name, u.admin, u.enabled, p.name AS position_name
            FROM users u LEFT JOIN positions p ON p.id = u.position_id
            ORDER BY u.enabled DESC, p.display_order NULLS LAST, u.name
            """)
    List<StaffRow> findStaffRows();

    @Update("""
            UPDATE users SET login_id = #{loginId}, name = #{name}, position_id = #{positionId},
                   admin = #{admin}, updated_at = now()
            WHERE id = #{id}
            """)
    int updateProfile(User user);

    @Update("UPDATE users SET enabled = #{enabled}, updated_at = now() WHERE id = #{id}")
    int updateEnabled(@Param("id") long id, @Param("enabled") boolean enabled);

    /** 同じログインIDの件数（excludeIdは更新時の自分自身を除外するため。新規時は0） */
    @Select("SELECT COUNT(*) FROM users WHERE login_id = #{loginId} AND id <> #{excludeId}")
    long countByLoginId(@Param("loginId") String loginId, @Param("excludeId") long excludeId);
```

`repository/UserRepository.java` に追加（import `java.util.List`、`jp.bk.shiftmanager.dto.StaffRow`）：

```java
    public List<StaffRow> findStaffRows() {
        return userMapper.findStaffRows();
    }

    public void updateProfile(User user) {
        userMapper.updateProfile(user);
    }

    public void updateEnabled(long id, boolean enabled) {
        userMapper.updateEnabled(id, enabled);
    }

    public boolean existsLoginId(String loginId, long excludeId) {
        return userMapper.countByLoginId(loginId, excludeId) > 0;
    }
```

- [x] **Step 4: フォームとサービスを実装する**

`src/main/java/jp/bk/shiftmanager/form/StaffEditForm.java`：

```java
package jp.bk.shiftmanager.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jp.bk.shiftmanager.util.LoginIds;
import lombok.Data;

@Data
public class StaffEditForm {

    @NotBlank(message = "ログインIDを入力してください")
    @Pattern(regexp = "[a-z0-9._-]{3,50}", message = "ログインIDは半角英数字と . _ - の3〜50文字で入力してください")
    private String loginId;

    @NotBlank(message = "名前を入力してください")
    @Size(max = 50, message = "名前は50文字以内で入力してください")
    private String name;

    /** 初期ポジション（未設定はnull） */
    private Long positionId;

    private boolean admin;

    public void setLoginId(String loginId) {
        this.loginId = LoginIds.normalize(loginId);
    }

    public void setName(String name) {
        this.name = name == null ? null : name.strip();
    }
}
```

`src/main/java/jp/bk/shiftmanager/form/StaffCreateForm.java`：

```java
package jp.bk.shiftmanager.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jp.bk.shiftmanager.auth.PasswordRules;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class StaffCreateForm extends StaffEditForm {

    /** 初期パスワード（本人に口頭やLINEで伝える） */
    @NotBlank(message = "初期パスワードを入力してください")
    @Pattern(regexp = PasswordRules.REGEXP, message = PasswordRules.MESSAGE)
    private String password;
}
```

`src/main/java/jp/bk/shiftmanager/service/StaffService.java`：

```java
package jp.bk.shiftmanager.service;

import java.util.List;
import jp.bk.shiftmanager.auth.PasswordRules;
import jp.bk.shiftmanager.dto.StaffRow;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.StaffCreateForm;
import jp.bk.shiftmanager.form.StaffEditForm;
import jp.bk.shiftmanager.repository.PositionRepository;
import jp.bk.shiftmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final UserRepository userRepository;
    private final PositionRepository positionRepository;
    private final PasswordEncoder passwordEncoder;

    public List<StaffRow> findAll() {
        return userRepository.findStaffRows();
    }

    /** スタッフを登録する。初回ログイン時にパスワード変更が必要な状態で作成する */
    @Transactional
    public long create(StaffCreateForm form) {
        checkLoginIdUnique(form.getLoginId(), 0);
        checkPosition(form.getPositionId());
        User user = new User();
        user.setLoginId(form.getLoginId());
        user.setName(form.getName());
        user.setPositionId(form.getPositionId());
        user.setAdmin(form.isAdmin());
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        user.setEnabled(true);
        user.setMustChangePassword(true);
        userRepository.insert(user);
        return user.getId();
    }

    public StaffEditForm editForm(long id) {
        User user = find(id);
        StaffEditForm form = new StaffEditForm();
        form.setLoginId(user.getLoginId());
        form.setName(user.getName());
        form.setPositionId(user.getPositionId());
        form.setAdmin(user.isAdmin());
        return form;
    }

    @Transactional
    public void update(long id, StaffEditForm form, long actorId) {
        User user = find(id);
        if (id == actorId && !form.isAdmin()) {
            throw new BusinessException("自分の管理者権限は外せません");
        }
        checkLoginIdUnique(form.getLoginId(), id);
        checkPosition(form.getPositionId());
        user.setLoginId(form.getLoginId());
        user.setName(form.getName());
        user.setPositionId(form.getPositionId());
        user.setAdmin(form.isAdmin());
        userRepository.updateProfile(user);
    }

    /** 管理者による仮パスワードへのリセット。次回ログイン時に変更が必要になる */
    @Transactional
    public void resetPassword(long id, String tempPassword) {
        find(id);
        if (tempPassword == null || !tempPassword.matches(PasswordRules.REGEXP)) {
            throw new BusinessException(PasswordRules.MESSAGE);
        }
        userRepository.updatePassword(id, passwordEncoder.encode(tempPassword), true);
    }

    @Transactional
    public void setEnabled(long id, boolean enabled, long actorId) {
        find(id);
        if (id == actorId && !enabled) {
            throw new BusinessException("自分自身は無効化できません");
        }
        userRepository.updateEnabled(id, enabled);
    }

    private User find(long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("スタッフが見つかりません"));
    }

    private void checkLoginIdUnique(String loginId, long excludeId) {
        if (userRepository.existsLoginId(loginId, excludeId)) {
            throw new BusinessException("このログインIDは既に使われています");
        }
    }

    private void checkPosition(Long positionId) {
        if (positionId != null && positionRepository.findById(positionId).isEmpty()) {
            throw new BusinessException("ポジションが見つかりません");
        }
    }
}
```

- [x] **Step 5: コントローラーとテンプレートを実装する**

`src/main/java/jp/bk/shiftmanager/controller/StaffAdminController.java`：

```java
package jp.bk.shiftmanager.controller;

import jakarta.validation.Valid;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.StaffCreateForm;
import jp.bk.shiftmanager.form.StaffEditForm;
import jp.bk.shiftmanager.service.PositionService;
import jp.bk.shiftmanager.service.StaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/staff")
@RequiredArgsConstructor
public class StaffAdminController {

    private final StaffService staffService;
    private final PositionService positionService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("staff", staffService.findAll());
        return "admin/staff/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new StaffCreateForm());
        model.addAttribute("positions", positionService.findAll());
        return "admin/staff/new";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") StaffCreateForm form, BindingResult bindingResult,
            Model model, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                staffService.create(form);
                redirectAttributes.addFlashAttribute("message",
                        form.getName() + "さんを登録しました。ログインIDと初期パスワードを本人に伝えてください");
                return "redirect:/admin/staff";
            } catch (BusinessException e) {
                bindingResult.reject("business", e.getMessage());
            }
        }
        model.addAttribute("positions", positionService.findAll());
        return "admin/staff/new";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        model.addAttribute("id", id);
        model.addAttribute("form", staffService.editForm(id));
        model.addAttribute("positions", positionService.findAll());
        return "admin/staff/edit";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable long id, @AuthenticationPrincipal LoginUser me,
            @Valid @ModelAttribute("form") StaffEditForm form, BindingResult bindingResult,
            Model model, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                staffService.update(id, form, me.getId());
                redirectAttributes.addFlashAttribute("message", "保存しました");
                return "redirect:/admin/staff";
            } catch (BusinessException e) {
                bindingResult.reject("business", e.getMessage());
            }
        }
        model.addAttribute("id", id);
        model.addAttribute("positions", positionService.findAll());
        return "admin/staff/edit";
    }

    @PostMapping("/{id}/password")
    public String resetPassword(@PathVariable long id, @RequestParam String tempPassword,
            RedirectAttributes redirectAttributes) {
        try {
            staffService.resetPassword(id, tempPassword);
            redirectAttributes.addFlashAttribute("message", "仮パスワードにリセットしました。本人に伝えてください");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/staff/" + id + "/edit";
    }

    @PostMapping("/{id}/enabled")
    public String setEnabled(@PathVariable long id, @RequestParam boolean enabled,
            @AuthenticationPrincipal LoginUser me, RedirectAttributes redirectAttributes) {
        try {
            staffService.setEnabled(id, enabled, me.getId());
            redirectAttributes.addFlashAttribute("message", enabled ? "有効にしました" : "無効にしました");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/staff";
    }
}
```

`src/main/resources/templates/admin/staff/list.html`：

```html
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout :: head('スタッフ管理')}"></head>
<body class="min-h-screen bg-stone-50 text-stone-900">
<header th:replace="~{layout :: header}"></header>
<main class="mx-auto max-w-5xl px-4 py-6">
  <div class="mb-4 flex items-center justify-between">
    <h1 class="text-lg font-bold">スタッフ管理</h1>
    <a th:href="@{/admin/staff/new}" class="btn-primary">スタッフを登録する</a>
  </div>
  <div th:replace="~{layout :: flash}"></div>
  <div class="card overflow-x-auto p-0">
    <table class="w-full text-sm">
      <thead class="bg-stone-100 text-left">
      <tr>
        <th class="px-3 py-2">名前</th>
        <th class="px-3 py-2">ログインID</th>
        <th class="px-3 py-2">ポジション</th>
        <th class="px-3 py-2">権限</th>
        <th class="px-3 py-2">状態</th>
        <th class="px-3 py-2"></th>
      </tr>
      </thead>
      <tbody>
      <tr th:each="s : ${staff}" th:classappend="${s.enabled} ? '' : 'text-stone-400'" class="border-t border-stone-100">
        <td class="px-3 py-2" th:text="${s.name}"></td>
        <td class="px-3 py-2" th:text="${s.loginId}"></td>
        <td class="px-3 py-2" th:text="${s.positionName} ?: '未設定'"></td>
        <td class="px-3 py-2" th:text="${s.admin} ? '管理者' : 'スタッフ'"></td>
        <td class="px-3 py-2" th:text="${s.enabled} ? '有効' : '無効'"></td>
        <td class="flex gap-2 px-3 py-2">
          <a th:href="@{/admin/staff/{id}/edit(id=${s.id})}" class="btn-secondary py-1">編集</a>
          <form th:action="@{/admin/staff/{id}/enabled(id=${s.id})}" method="post">
            <input type="hidden" name="enabled" th:value="${!s.enabled}">
            <button th:class="${s.enabled} ? 'btn-danger py-1' : 'btn-secondary py-1'"
                    th:text="${s.enabled} ? '無効にする' : '有効にする'"></button>
          </form>
        </td>
      </tr>
      </tbody>
    </table>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/admin/staff/new.html`：

```html
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout :: head('スタッフ登録')}"></head>
<body class="min-h-screen bg-stone-50 text-stone-900">
<header th:replace="~{layout :: header}"></header>
<main class="mx-auto max-w-md px-4 py-6">
  <h1 class="mb-4 text-lg font-bold">スタッフ登録</h1>
  <form th:action="@{/admin/staff}" th:object="${form}" method="post" class="card space-y-4">
    <p th:each="err : ${#fields.globalErrors()}" th:text="${err}" class="rounded bg-red-50 p-3 text-sm text-red-700"></p>
    <label class="block">
      <span class="text-sm">名前</span>
      <input th:field="*{name}" maxlength="50" class="input">
      <span th:errors="*{name}" class="text-sm text-red-700"></span>
    </label>
    <label class="block">
      <span class="text-sm">ログインID（半角英数字と . _ - の3〜50文字）</span>
      <input th:field="*{loginId}" autocapitalize="none" class="input">
      <span th:errors="*{loginId}" class="text-sm text-red-700"></span>
    </label>
    <label class="block">
      <span class="text-sm">ポジション</span>
      <select th:field="*{positionId}" class="input">
        <option value="">未設定</option>
        <option th:each="p : ${positions}" th:value="${p.id}" th:text="${p.hidden} ? ${p.name} + '（非表示）' : ${p.name}"></option>
      </select>
    </label>
    <label class="flex items-center gap-2 text-sm">
      <input type="checkbox" th:field="*{admin}">
      管理者権限を付ける
    </label>
    <label class="block">
      <span class="text-sm">初期パスワード（半角英数字記号 8〜72文字）</span>
      <input th:field="*{password}" autocomplete="off" class="input">
      <span th:errors="*{password}" class="text-sm text-red-700"></span>
    </label>
    <div class="flex gap-2">
      <button class="btn-primary">登録する</button>
      <a th:href="@{/admin/staff}" class="btn-secondary">戻る</a>
    </div>
  </form>
</main>
</body>
</html>
```

`src/main/resources/templates/admin/staff/edit.html`：

```html
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout :: head('スタッフ編集')}"></head>
<body class="min-h-screen bg-stone-50 text-stone-900">
<header th:replace="~{layout :: header}"></header>
<main class="mx-auto max-w-md px-4 py-6">
  <h1 class="mb-4 text-lg font-bold">スタッフ編集</h1>
  <div th:replace="~{layout :: flash}"></div>
  <form th:action="@{/admin/staff/{id}(id=${id})}" th:object="${form}" method="post" class="card mb-6 space-y-4">
    <p th:each="err : ${#fields.globalErrors()}" th:text="${err}" class="rounded bg-red-50 p-3 text-sm text-red-700"></p>
    <label class="block">
      <span class="text-sm">名前</span>
      <input th:field="*{name}" maxlength="50" class="input">
      <span th:errors="*{name}" class="text-sm text-red-700"></span>
    </label>
    <label class="block">
      <span class="text-sm">ログインID</span>
      <input th:field="*{loginId}" autocapitalize="none" class="input">
      <span th:errors="*{loginId}" class="text-sm text-red-700"></span>
    </label>
    <label class="block">
      <span class="text-sm">ポジション</span>
      <select th:field="*{positionId}" class="input">
        <option value="">未設定</option>
        <option th:each="p : ${positions}" th:value="${p.id}" th:text="${p.hidden} ? ${p.name} + '（非表示）' : ${p.name}"></option>
      </select>
    </label>
    <label class="flex items-center gap-2 text-sm">
      <input type="checkbox" th:field="*{admin}">
      管理者権限を付ける
    </label>
    <div class="flex gap-2">
      <button class="btn-primary">保存する</button>
      <a th:href="@{/admin/staff}" class="btn-secondary">戻る</a>
    </div>
  </form>

  <h2 class="mb-2 font-bold">パスワードのリセット</h2>
  <form th:action="@{/admin/staff/{id}/password(id=${id})}" method="post" class="card space-y-3">
    <p class="text-sm text-stone-600">仮パスワードを設定します。本人は次回ログイン時に変更が必要になります。</p>
    <input name="tempPassword" autocomplete="off" placeholder="仮パスワード（半角8〜72文字）" class="input">
    <button class="btn-secondary">リセットする</button>
  </form>
</main>
</body>
</html>
```

- [x] **Step 6: テストが通ることを確認する**

Run: `./mvnw test -Dtest=StaffAdminTest`
Expected: PASS（13件）

- [x] **Step 7: 全テストを実行してコミットする**

Run: `./mvnw test`
Expected: PASS

```bash
git add -A
git commit -m "feat: スタッフ管理（登録・編集・パスワードリセット・無効化）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [x] **Step 8: この計画ファイルのTask 5のチェックボックスをすべて `[x]` にしてコミットし、停止してユーザーに報告する**

```bash
git add docs/superpowers/plans/2026-09-25-plan1-foundation.md
git commit -m "docs: Plan 1 Task 5 完了

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: ログイン中セッションへのユーザー状態の反映

**背景：** セッションにはログイン時点の `LoginUser` がそのまま残るため、Task 5の無効化・パスワードリセット・管理者権限の変更が、ログイン中のブラウザには反映されない（無効化した退職者がセッション切れまで操作できる、権限を外しても管理画面を使える）。リクエストごとにDBの最新状態と照合するフィルタを追加して、即時に反映させる。

**Files:**
- Create: `auth/UserStateCheckFilter.java`
- Modify: `auth/SecurityConfig.java`（フィルタを登録する）
- Test: `src/test/java/jp/bk/shiftmanager/auth/UserStateCheckFilterTest.java`

（Javaのパスはすべて `src/main/java/jp/bk/shiftmanager/` からの相対）

**Interfaces:**
- Consumes: `UserRepository#findById`・`LoginUser#from`・`LoginUser#getPasswordHash`・`SecurityConfig`（Task 2）、`POST /password`（Task 3）、`POST /admin/staff/{id}`・`/{id}/password`・`/{id}/enabled`（Task 5）、`IntegrationTestBase`・`TestData#user`・`#login`（Task 2）
- Produces: `auth.UserStateCheckFilter`（`OncePerRequestFilter`。コンストラクタ引数 `UserRepository`）

**仕様：**
- ログイン中ユーザー（principalが `LoginUser`）のリクエストごとに `users` を主キーで1件取得し、セッション上の `LoginUser` と比べる
- ユーザーが存在しない・無効・パスワードハッシュが違う（管理者によるリセット、別端末での変更）→ ログアウト（セッション破棄）して `/login` へリダイレクトする
- 管理者権限・変更必須フラグ・ログインID・名前が違う → セッション上の `LoginUser` をDBの内容で差し替えて、そのまま処理を続ける
- 自分でパスワードを変更した端末は、`PasswordChangeController` がセッションのハッシュを更新するため、ログアウトされない
- `@Component` にしない。Beanにするとサーブレットフィルタとしても自動登録され、`OncePerRequestFilter` の仕組みでセキュリティフィルタチェーン内の実行が飛ばされるため。`SecurityConfig` で `new` して `RememberMeAuthenticationFilter` の直後に登録する（認可判定より前に権限を差し替えるため）
- `LoginUser` に `CredentialsContainer` を実装しないこと（ログイン後にパスワードハッシュが消去され、照合できなくなる）

- [x] **Step 1: 失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/auth/UserStateCheckFilterTest.java`：

```java
package jp.bk.shiftmanager.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.TestData;
import jp.bk.shiftmanager.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/** 実際のログインで作ったセッションを使い、管理者の操作がログイン中のセッションに反映されることを確かめる */
class UserStateCheckFilterTest extends IntegrationTestBase {

    LoginUser boss;

    @BeforeEach
    void setUp() {
        boss = data.login(data.user("boss", "店長", true));
    }

    @Test
    void 無効化されたスタッフのログイン中セッションはログイン画面へ戻される() throws Exception {
        User taro = data.user("taro", "山田太郎", false);
        MockHttpSession session = login("taro");
        mvc.perform(get("/").session(session)).andExpect(status().isOk());

        mvc.perform(post("/admin/staff/{id}/enabled", taro.getId()).with(user(boss)).with(csrf())
                        .param("enabled", "false"))
                .andExpect(redirectedUrl("/admin/staff"));

        mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/login"));
    }

    @Test
    void 仮パスワードにリセットされたスタッフのログイン中セッションはログイン画面へ戻される() throws Exception {
        User taro = data.user("taro", "山田太郎", false);
        MockHttpSession session = login("taro");
        mvc.perform(get("/").session(session)).andExpect(status().isOk());

        mvc.perform(post("/admin/staff/{id}/password", taro.getId()).with(user(boss)).with(csrf())
                        .param("tempPassword", "temppass1"))
                .andExpect(redirectedUrl("/admin/staff/" + taro.getId() + "/edit"));

        mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/login"));
    }

    @Test
    void 別の端末でパスワードを変更すると他のセッションは戻され変更した端末は使い続けられる() throws Exception {
        data.user("taro", "山田太郎", false);
        MockHttpSession pc = login("taro");
        MockHttpSession phone = login("taro");

        mvc.perform(post("/password").session(phone).with(csrf())
                        .param("currentPassword", TestData.PASSWORD)
                        .param("newPassword", "newpass123")
                        .param("confirmPassword", "newpass123"))
                .andExpect(redirectedUrl("/"));

        mvc.perform(get("/").session(phone)).andExpect(status().isOk());
        mvc.perform(get("/").session(pc)).andExpect(redirectedUrl("/login"));
    }

    @Test
    void 管理者権限を外されると次のリクエストから管理画面を使えない() throws Exception {
        User hanako = data.user("hanako", "佐藤花子", true);
        MockHttpSession session = login("hanako");
        mvc.perform(get("/admin/staff").session(session)).andExpect(status().isOk());

        // adminを送らない＝チェックを外した状態
        mvc.perform(post("/admin/staff/{id}", hanako.getId()).with(user(boss)).with(csrf())
                        .param("loginId", "hanako").param("name", "佐藤花子"))
                .andExpect(redirectedUrl("/admin/staff"));

        mvc.perform(get("/admin/staff").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/").session(session)).andExpect(status().isOk());
    }

    @Test
    void 管理者権限を付けられると再ログインなしで管理画面を使える() throws Exception {
        User taro = data.user("taro", "山田太郎", false);
        MockHttpSession session = login("taro");
        mvc.perform(get("/admin/staff").session(session)).andExpect(status().isForbidden());

        mvc.perform(post("/admin/staff/{id}", taro.getId()).with(user(boss)).with(csrf())
                        .param("loginId", "taro").param("name", "山田太郎").param("admin", "true"))
                .andExpect(redirectedUrl("/admin/staff"));

        mvc.perform(get("/admin/staff").session(session)).andExpect(status().isOk());
    }

    /** ログイン画面から実際にログインし、そのセッションを返す */
    private MockHttpSession login(String loginId) throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").param("loginId", loginId)
                        .param("password", TestData.PASSWORD).with(csrf()))
                .andExpect(authenticated())
                .andReturn().getRequest().getSession(false);
    }
}
```

- [x] **Step 2: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=UserStateCheckFilterTest`
Expected: FAIL（5件。無効化・リセット・別端末の変更後もセッションが使えてしまい、権限の変更も反映されないため）

- [x] **Step 3: フィルタを実装する**

`src/main/java/jp/bk/shiftmanager/auth/UserStateCheckFilter.java`：

```java
package jp.bk.shiftmanager.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * ログイン中ユーザーの状態をリクエストごとにDBと照合する。
 * セッションにはログイン時点の情報が残るため、管理者による無効化・パスワードリセット・権限変更を即時に反映させる。
 * Beanにするとサーブレットフィルタとしても自動登録されてしまうため、SecurityConfigでnewして登録する
 */
@RequiredArgsConstructor
public class UserStateCheckFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser current)) {
            chain.doFilter(request, response);
            return;
        }
        Optional<User> latest = userRepository.findById(current.getId());
        // 無効化・パスワードの変更（管理者によるリセット、別端末での変更）はログアウトさせる
        if (latest.isEmpty() || !latest.get().isEnabled()
                || !latest.get().getPasswordHash().equals(current.getPasswordHash())) {
            logoutHandler.logout(request, response, authentication);
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        LoginUser refreshed = LoginUser.from(latest.get());
        if (changed(current, refreshed)) {
            // 権限・名前などの変更は、セッション上のログイン情報を差し替えて反映する
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(refreshed, null, refreshed.getAuthorities()));
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
        }
        chain.doFilter(request, response);
    }

    private boolean changed(LoginUser current, LoginUser refreshed) {
        return current.isAdmin() != refreshed.isAdmin()
                || current.isMustChangePassword() != refreshed.isMustChangePassword()
                || !current.getLoginId().equals(refreshed.getLoginId())
                || !current.getName().equals(refreshed.getName());
    }
}
```

- [x] **Step 4: SecurityConfigにフィルタを登録する**

`auth/SecurityConfig.java` の `securityFilterChain` に引数 `UserRepository userRepository` を追加し、`http` の設定の最後（`.rememberMe(...)` の後）に次を追加する（import `jp.bk.shiftmanager.repository.UserRepository`、`org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter`）：

```java
                // 管理者による無効化・リセット・権限変更をログイン中のセッションにも反映する
                .addFilterAfter(new UserStateCheckFilter(userRepository), RememberMeAuthenticationFilter.class);
```

- [x] **Step 5: テストが通ることを確認する**

Run: `./mvnw test -Dtest=UserStateCheckFilterTest`
Expected: PASS（5件）

- [x] **Step 6: 全テストを実行してコミットする**

Run: `./mvnw test`
Expected: PASS（既存テストは `data.login` でDBの最新状態から `LoginUser` を作っているため、フィルタの影響を受けない）

```bash
git add -A
git commit -m "feat: 無効化・パスワードリセット・権限変更をログイン中のセッションに反映

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [x] **Step 7: この計画ファイルのTask 6のチェックボックスをすべて `[x]` にしてコミットし、停止してユーザーに報告する**

```bash
git add docs/superpowers/plans/2026-09-25-plan1-foundation.md
git commit -m "docs: Plan 1 Task 6 完了

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: ユーザー状態照合の改善（静的ファイルの除外・強制ログアウトの理由表示）

**背景：** Task 6のフィルタには2つの問題がある。(1) ログイン中は静的ファイル（CSS・JS・アイコン・マニフェスト）のリクエストでも毎回 `users` を問い合わせるため、DBがNeon（外部）だと1画面ごとに数回分の通信遅延が加わる。(2) 強制ログアウトされた人は理由の表示なしにログイン画面へ戻るため、不具合と誤解されやすい。

**Files:**
- Modify: `auth/SecurityConfig.java`（静的ファイルのパスを定数にまとめる）
- Modify: `auth/UserStateCheckFilter.java`（静的ファイルを対象外にする・リダイレクト先を `/login?expired` にする）
- Modify: `src/main/resources/templates/login.html`（`expired` のメッセージを表示する）
- Test: `src/test/java/jp/bk/shiftmanager/auth/UserStateCheckFilterTest.java`

（Javaのパスはすべて `src/main/java/jp/bk/shiftmanager/` からの相対）

**Interfaces:**
- Consumes: `UserStateCheckFilter`・`UserStateCheckFilterTest`（Task 6）、`SecurityConfig`（Task 2）、`login.html`（Task 2）
- Produces: `SecurityConfig.STATIC_RESOURCES`（`String[]`。未ログインでも取得でき、ユーザー状態の照合もしない静的ファイルのパスパターン）

**仕様：**
- `/css/**`・`/js/**`・`/icons/**`・`/manifest.webmanifest` はフィルタの対象外にする（`shouldNotFilter`）。パスは `SecurityConfig.STATIC_RESOURCES` にまとめ、認可設定の `permitAll` と同じ定数を使う（片方だけ変更して食い違うのを防ぐ）
- `/login`・`/error` は対象外にしない（静的ファイルではなく、照合してもログアウト済みなら何もしないため）
- 強制ログアウト時のリダイレクト先を `/login?expired` にし、ログイン画面に「ログイン情報が変更されたため、ログアウトしました」と表示する

- [x] **Step 1: 失敗するテストを書く**

`UserStateCheckFilterTest.java` を次のように変更する。

既存の3テスト（無効化・リセット・別端末での変更）の `redirectedUrl("/login")` をすべて `redirectedUrl("/login?expired")` に変える。

import を追加する：

```java
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import org.hamcrest.Matchers;
```

テストを2件追加する（`login` ヘルパーの前）：

```java
    @Test
    void 静的ファイルのリクエストではユーザー状態を照合しない() throws Exception {
        User taro = data.user("taro", "山田太郎", false);
        MockHttpSession session = login("taro");

        mvc.perform(post("/admin/staff/{id}/enabled", taro.getId()).with(user(boss)).with(csrf())
                        .param("enabled", "false"))
                .andExpect(redirectedUrl("/admin/staff"));

        // 照合するとログアウトされてログイン画面へ戻るが、静的ファイルは照合しないのでそのまま処理される（ファイルが無いので404）
        mvc.perform(get("/css/not-exists.css").session(session)).andExpect(status().isNotFound());
        // 静的ファイルでログアウトされていないので、通常の画面で初めてログアウトされる
        mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/login?expired"));
    }

    @Test
    void 強制ログアウト後のログイン画面に理由が表示される() throws Exception {
        mvc.perform(get("/login").param("expired", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("ログイン情報が変更されたため、ログアウトしました")));
    }
```

- [x] **Step 2: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=UserStateCheckFilterTest`
Expected: FAIL（5件。リダイレクト先が `/login` のままの3件、静的ファイルでログアウトされる1件、メッセージが無い1件）

- [x] **Step 3: SecurityConfigに静的ファイルのパスをまとめる**

`auth/SecurityConfig.java` の `REMEMBER_ME_SECONDS` の下に追加する：

```java
    /** 未ログインでも取得でき、ログイン中ユーザーの状態照合も行わない静的ファイル */
    public static final String[] STATIC_RESOURCES = {"/css/**", "/js/**", "/icons/**", "/manifest.webmanifest"};
```

`authorizeHttpRequests` の `permitAll` を次のように変える：

```java
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(STATIC_RESOURCES).permitAll()
                        .requestMatchers("/login", "/error").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
```

- [x] **Step 4: フィルタを変更する**

`auth/UserStateCheckFilter.java` に次を追加する（import `java.util.Arrays`、`java.util.List`、`org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher`、`org.springframework.security.web.util.matcher.RequestMatcher`）：

```java
    /** 照合しないリクエスト。静的ファイルの取得ごとにDBへ問い合わせないため */
    private static final List<RequestMatcher> SKIPPED = Arrays.stream(SecurityConfig.STATIC_RESOURCES)
            .<RequestMatcher>map(PathPatternRequestMatcher.withDefaults()::matcher)
            .toList();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SKIPPED.stream().anyMatch(matcher -> matcher.matches(request));
    }
```

`doFilterInternal` のリダイレクト先を変える：

```java
            response.sendRedirect(request.getContextPath() + "/login?expired");
```

- [x] **Step 5: ログイン画面にメッセージを追加する**

`login.html` の `param.logout` の行の直後に追加する：

```html
  <p th:if="${param.expired}" class="mb-4 rounded bg-amber-50 p-3 text-sm text-amber-800">ログイン情報が変更されたため、ログアウトしました</p>
```

- [x] **Step 6: テストが通ることを確認する**

Run: `./mvnw test -Dtest=UserStateCheckFilterTest`
Expected: PASS（7件）

- [x] **Step 7: 全テストを実行してコミットする**

Run: `./mvnw test`
Expected: PASS

```bash
git add -A
git commit -m "feat: 静的ファイルでのユーザー状態照合を省略し、強制ログアウトの理由を表示

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [x] **Step 8: この計画ファイルのTask 7のチェックボックスをすべて `[x]` にしてコミットし、停止してユーザーに報告する**

```bash
git add docs/superpowers/plans/2026-09-25-plan1-foundation.md
git commit -m "docs: Plan 1 Task 7 完了

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: 締切日数の設定

**Files:**
- Create: `mapper/AppSettingMapper.java`、`repository/AppSettingRepository.java`、`service/SettingService.java`、`controller/SettingsController.java`
- Create: `src/main/resources/templates/admin/settings.html`
- Test: `src/test/java/jp/bk/shiftmanager/controller/SettingsTest.java`

（Javaのパスはすべて `src/main/java/jp/bk/shiftmanager/` からの相対）

**Interfaces:**
- Consumes: `BusinessException`（Task 3）、`IntegrationTestBase`・`TestData`（Task 2）
- Produces:
  - `mapper.AppSettingMapper#getDeadlineDaysBefore(): int`、`#updateDeadlineDaysBefore(int): int`
  - `repository.AppSettingRepository#getDeadlineDaysBefore(): int`、`#updateDeadlineDaysBefore(int)`
  - `service.SettingService#getDeadlineDaysBefore(): int`（Plan 2のサイクル計算で使う）、`#updateDeadlineDaysBefore(String input)`

- [x] **Step 1: 失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/controller/SettingsTest.java`：

```java
package jp.bk.shiftmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.mapper.AppSettingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SettingsTest extends IntegrationTestBase {

    @Autowired
    AppSettingMapper appSettingMapper;

    LoginUser admin;

    @BeforeEach
    void setUp() {
        admin = data.login(data.user("boss", "店長", true));
    }

    @Test
    void 設定画面を表示できる() throws Exception {
        mvc.perform(get("/admin/settings").with(user(admin))).andExpect(status().isOk());
    }

    @Test
    void 締切日数を変更できる() throws Exception {
        mvc.perform(post("/admin/settings").with(user(admin)).with(csrf()).param("deadlineDaysBefore", "7"))
                .andExpect(redirectedUrl("/admin/settings"));

        assertThat(appSettingMapper.getDeadlineDaysBefore()).isEqualTo(7);
    }

    @Test
    void 範囲外や数値以外の締切日数は保存されない() throws Exception {
        for (String invalid : new String[] {"-1", "31", "abc", ""}) {
            mvc.perform(post("/admin/settings").with(user(admin)).with(csrf()).param("deadlineDaysBefore", invalid))
                    .andExpect(redirectedUrl("/admin/settings"))
                    .andExpect(flash().attribute("error", "締切日数は0〜30の整数で入力してください"));
        }
        assertThat(appSettingMapper.getDeadlineDaysBefore()).isEqualTo(5);
    }

    @Test
    void 一般スタッフは設定を変更できない() throws Exception {
        LoginUser staff = data.login(data.user("taro", "山田太郎", false));

        mvc.perform(post("/admin/settings").with(user(staff)).with(csrf()).param("deadlineDaysBefore", "7"))
                .andExpect(status().isForbidden());
    }
}
```

- [x] **Step 2: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=SettingsTest`
Expected: FAIL（コンパイルエラー：`AppSettingMapper` が存在しない）

- [x] **Step 3: Mapper・Repository・Serviceを実装する**

`src/main/java/jp/bk/shiftmanager/mapper/AppSettingMapper.java`：

```java
package jp.bk.shiftmanager.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AppSettingMapper {

    /** 締切＝サイクル開始日の何日前か */
    @Select("SELECT deadline_days_before FROM app_settings WHERE id = 1")
    int getDeadlineDaysBefore();

    @Update("UPDATE app_settings SET deadline_days_before = #{days} WHERE id = 1")
    int updateDeadlineDaysBefore(@Param("days") int days);
}
```

`src/main/java/jp/bk/shiftmanager/repository/AppSettingRepository.java`：

```java
package jp.bk.shiftmanager.repository;

import jp.bk.shiftmanager.mapper.AppSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AppSettingRepository {

    private final AppSettingMapper appSettingMapper;

    public int getDeadlineDaysBefore() {
        return appSettingMapper.getDeadlineDaysBefore();
    }

    public void updateDeadlineDaysBefore(int days) {
        appSettingMapper.updateDeadlineDaysBefore(days);
    }
}
```

`src/main/java/jp/bk/shiftmanager/service/SettingService.java`：

```java
package jp.bk.shiftmanager.service;

import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettingService {

    private static final String INVALID_DAYS = "締切日数は0〜30の整数で入力してください";

    private final AppSettingRepository appSettingRepository;

    /** 締切＝サイクル開始日の何日前か */
    public int getDeadlineDaysBefore() {
        return appSettingRepository.getDeadlineDaysBefore();
    }

    /** 画面入力の文字列を検証して保存する */
    @Transactional
    public void updateDeadlineDaysBefore(String input) {
        int days;
        try {
            days = Integer.parseInt(input == null ? "" : input.strip());
        } catch (NumberFormatException e) {
            throw new BusinessException(INVALID_DAYS);
        }
        if (days < 0 || days > 30) {
            throw new BusinessException(INVALID_DAYS);
        }
        appSettingRepository.updateDeadlineDaysBefore(days);
    }
}
```

- [x] **Step 4: コントローラーとテンプレートを実装する**

`src/main/java/jp/bk/shiftmanager/controller/SettingsController.java`：

```java
package jp.bk.shiftmanager.controller;

import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingService settingService;

    @GetMapping
    public String show(Model model) {
        model.addAttribute("deadlineDaysBefore", settingService.getDeadlineDaysBefore());
        return "admin/settings";
    }

    @PostMapping
    public String update(@RequestParam String deadlineDaysBefore, RedirectAttributes redirectAttributes) {
        try {
            settingService.updateDeadlineDaysBefore(deadlineDaysBefore);
            redirectAttributes.addFlashAttribute("message", "保存しました");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/settings";
    }
}
```

`src/main/resources/templates/admin/settings.html`：

```html
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout :: head('設定')}"></head>
<body class="min-h-screen bg-stone-50 text-stone-900">
<header th:replace="~{layout :: header}"></header>
<main class="mx-auto max-w-md px-4 py-6">
  <h1 class="mb-4 text-lg font-bold">設定</h1>
  <div th:replace="~{layout :: flash}"></div>
  <form th:action="@{/admin/settings}" method="post" class="card space-y-4">
    <label class="block">
      <span class="text-sm">申請の締切（サイクル開始日の何日前か）</span>
      <input type="number" name="deadlineDaysBefore" th:value="${deadlineDaysBefore}" min="0" max="30" class="input w-24">
    </label>
    <p class="text-sm text-stone-600">例：5日前の場合、11日〜20日分の締切は6日です。</p>
    <button class="btn-primary">保存する</button>
  </form>
</main>
</body>
</html>
```

- [x] **Step 5: テストが通ることを確認する**

Run: `./mvnw test -Dtest=SettingsTest`
Expected: PASS（4件）

- [x] **Step 6: 全テストを実行してコミットする**

Run: `./mvnw test`
Expected: PASS

```bash
git add -A
git commit -m "feat: 申請締切日数の設定

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [x] **Step 7: この計画ファイルのTask 8のチェックボックスをすべて `[x]` にしてコミットし、停止してユーザーに報告する**

```bash
git add docs/superpowers/plans/2026-09-25-plan1-foundation.md
git commit -m "docs: Plan 1 Task 8 完了

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: PWA対応とDockerイメージ

**Files:**
- Create: `src/main/resources/static/manifest.webmanifest`、`src/main/resources/static/icons/icon.svg`
- Create: `Dockerfile`、`.dockerignore`
- Modify: `src/main/resources/templates/layout.html`（headにmanifest等を追加）
- Modify: `src/main/resources/application.yml`（プロキシ配下の設定）
- Test: `src/test/java/jp/bk/shiftmanager/PwaTest.java`

**Interfaces:**
- Consumes: `IntegrationTestBase`（Task 2）、layout.htmlの `head` フラグメント（Task 2）
- Produces: Dockerイメージ（環境変数 `DATABASE_URL`、`DATABASE_USERNAME`、`DATABASE_PASSWORD`、`REMEMBER_ME_KEY` で動作）

- [ ] **Step 1: 失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/PwaTest.java`：

```java
package jp.bk.shiftmanager;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

class PwaTest extends IntegrationTestBase {

    @Test
    void マニフェストとアイコンは未ログインでも取得できる() throws Exception {
        mvc.perform(get("/manifest.webmanifest"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("\"start_url\"")));
        mvc.perform(get("/icons/icon.svg")).andExpect(status().isOk());
    }

    @Test
    void ログイン画面からマニフェストが参照されている() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(content().string(Matchers.containsString("manifest.webmanifest")));
    }
}
```

- [ ] **Step 2: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=PwaTest`
Expected: FAIL（`/manifest.webmanifest` が404）

- [ ] **Step 3: マニフェストとアイコンを作成し、layoutから参照する**

`src/main/resources/static/manifest.webmanifest`：

```json
{
  "name": "シフト管理",
  "short_name": "シフト",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#fafaf9",
  "theme_color": "#b45309",
  "icons": [
    { "src": "/icons/icon.svg", "sizes": "any", "type": "image/svg+xml", "purpose": "any" }
  ]
}
```

`src/main/resources/static/icons/icon.svg`：

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
  <rect width="512" height="512" rx="96" fill="#b45309"/>
  <rect x="112" y="136" width="288" height="256" rx="24" fill="#fff"/>
  <rect x="112" y="136" width="288" height="64" rx="24" fill="#fde68a"/>
  <rect x="168" y="104" width="24" height="64" rx="12" fill="#fff"/>
  <rect x="320" y="104" width="24" height="64" rx="12" fill="#fff"/>
  <rect x="160" y="240" width="192" height="24" rx="12" fill="#b45309"/>
  <rect x="160" y="300" width="128" height="24" rx="12" fill="#b45309"/>
</svg>
```

`layout.html` の `head` フラグメント内、`<link rel="stylesheet" ...>` の直後に追加：

```html
  <link rel="manifest" th:href="@{/manifest.webmanifest}">
  <link rel="icon" th:href="@{/icons/icon.svg}" type="image/svg+xml">
  <link rel="apple-touch-icon" th:href="@{/icons/icon.svg}">
  <meta name="theme-color" content="#b45309">
```

- [ ] **Step 4: テストが通ることを確認する**

Run: `./mvnw test -Dtest=PwaTest`
Expected: PASS（2件）

- [ ] **Step 5: プロキシ配下（Render等）向けの設定を追加する**

`application.yml` の末尾に追加（HTTPS終端のリバースプロキシ配下でリダイレクト先をhttpsにするため）：

```yaml
server:
  forward-headers-strategy: framework
```

- [ ] **Step 6: DockerfileとDockerignoreを作成する**

`Dockerfile`：

```dockerfile
# CSSのビルド
FROM node:24-alpine AS css
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY src/main/frontend src/main/frontend
COPY src/main/resources/templates src/main/resources/templates
RUN npm run build

# アプリのビルド
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline
COPY src src
COPY --from=css /app/src/main/resources/static/css src/main/resources/static/css
RUN ./mvnw -q package -DskipTests

# 実行
FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Tokyo
COPY --from=build /app/target/shiftmanager-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

`.dockerignore`：

```
target/
node_modules/
.git/
.idea/
.vscode/
documents/
docs/
src/main/resources/static/css/app.css
```

- [ ] **Step 7: イメージをビルドし、ローカルDBにつないで起動確認する**

```bash
docker build -t shiftmanager .
docker compose up -d db
docker run --rm -d --name shiftmanager-app -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/shiftmanager \
  -e REMEMBER_ME_KEY=local-check-key shiftmanager
```

アプリの起動を待ってから確認する：

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/login
curl -s http://localhost:8080/css/app.css | head -c 100
```

Expected: 1行目が `200`、2行目にTailwindのCSS（`/*! tailwindcss` で始まる）が出力される

ユーザーにブラウザで `http://localhost:8080/login` を開いてもらい、`admin` / `admin` でログイン → パスワード変更画面に遷移すること、変更後にトップ画面とヘッダーの管理メニューが表示されることを確認してもらう。確認後にコンテナを停止する：

```bash
docker stop shiftmanager-app
```

- [ ] **Step 8: 全テストを実行してコミットする**

Run: `./mvnw test`
Expected: PASS

```bash
git add -A
git commit -m "feat: PWAマニフェストとDockerイメージ

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 9: この計画ファイルのTask 9のチェックボックスをすべて `[x]` にしてコミットし、停止してユーザーに報告する**

```bash
git add docs/superpowers/plans/2026-09-25-plan1-foundation.md
git commit -m "docs: Plan 1 Task 9 完了

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## 後続Plan（Plan 1完了後に、実装済みコードを前提に詳細化する）

| Plan | 内容 |
|---|---|
| Plan 2 申請 | サイクル計算（1日・11日・21日、締切＝開始日−設定日数、Clock注入でテスト）、時刻選択肢（8:00〜23:00、30分刻み）、パターン管理、月ごとの申請画面（一括「登録する」、締切済みサイクルは編集不可、「この期間は出勤できない」）、管理者の申請閲覧（サイクル×スタッフ表、未提出者の背景色、代理編集） |
| Plan 3 転記・公開 | 1日単位の転記画面（ポジション別、名前候補の並び、6行の空欄、「+ 追加する」、申請IN/OUT表示と差分警告、IN順の並べ替え）、二重登録防止、「この日を公開」「○日〜○日まで公開」、公開済みの日の編集で `shift_changes` を記録 |
| Plan 4 閲覧 | スタッフのトップ画面（変更ありと確認済みボタン、次回出勤、直近の出勤、締切案内、今月・来月の予定時間）、日別シフト一覧（ポジション別・IN順、過去1週間まで） |
