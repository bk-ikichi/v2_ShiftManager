# Plan 5 初期パスワードの固定値と仮パスワードの自動生成 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **セッション運用：** 1セッション1 Task。Taskの最後のステップ（コミットとチェックボックス更新）が終わったら停止してユーザーに報告する。次のTaskは `/clear` 後の新しいセッションで行う。新しいセッションではこの冒頭（File Structureまで）と、未完了の最初のTaskの範囲だけを読む（`CLAUDE.md` 参照）。

**Goal:** スタッフ登録時の初期パスワードを省略可能にして省略時は共通の固定値を使い、パスワードリセットの仮パスワードはシステムがランダムに生成して1回だけ表示する。

**Architecture:** 固定値は環境変数 `STAFF_INITIAL_PASSWORD`（プロパティ `app.staff-initial-password`）から読み、起動時に検証する `StaffInitialPassword`（auth）に持たせる。仮パスワードの生成は状態を持たない `TempPasswords.generate()`（auth）が担当する。`StaffService` がこの2つを使い、`resetPassword` は生成した仮パスワードを返す。Controllerはそれをフラッシュ属性 `tempPassword` で編集画面に渡す。

**Tech Stack:** Java 17、Spring Boot 4.1.1、MyBatis、Spring Security 7、Thymeleaf 3.1、Tailwind CSS 4、JUnit 5 + MockMvc + Testcontainers 2

**Spec:** `documents/2026-09-25-shift-manager-v2-spec.md`（「アカウント」）。DBの変更はない

## Global Constraints

- 画面の文言・コードのコメントはすべて日本語
- パッケージは層ごと。controllerはserviceのみ、serviceはrepositoryのみを呼ぶ（既存の `PasswordEncoder`・`PasswordRules` と同じく auth の部品は使ってよい）
- パスワードのルールは既存の `PasswordRules.REGEXP`（半角 `\x21-\x7E` の8〜72文字）
- 初期パスワードは省略可能。省略時は固定値。固定値は環境変数 `STAFF_INITIAL_PASSWORD` で指定し、未設定またはルールに合わない場合はアプリを起動しない
- 固定値の実際の値はリポジトリ（ソース・設定・ドキュメント・この計画）に書かない。テストでは `testinit1` を使う
- 仮パスワード：英小文字と数字の8文字。`0` `o` `1` `l` `i` は使わない。生成には `SecureRandom` を使う。リセット直後の画面に1回だけ表示し、再表示はできない
- 初回ログイン時のパスワード変更の強制（`must_change_password = true`）と、初期管理者 `admin` / `admin` は変えない
- コミットメッセージの末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` を付ける

## Review Focus

1. 初期パスワードを空欄で登録する：固定値でログインでき、初回にパスワード変更が必要になる → Task 1でテスト
2. 初期パスワードを入力して登録する：固定値ではなく入力した値が使われる。全角を含む値は従来どおりエラー → Task 1でテスト
3. 固定値が未設定・ルール違反（7文字、全角）：アプリが起動しない（`StaffInitialPassword` の生成で例外） → Task 1でテスト
4. 存在しないスタッフIDでリセットする：500エラーにせず、エラーメッセージを出して編集画面へ戻る → Task 1でテスト
5. 仮パスワードの内容：毎回8文字で、使ってよい文字だけで構成され、繰り返し生成すると異なる値になる → Task 1でテスト

## File Structure

```
src/main/java/jp/bk/shiftmanager/
  auth/        StaffInitialPassword（新規：固定値の保持と起動時検証）
               TempPasswords（新規：仮パスワードの生成）
  form/        StaffCreateForm（password を任意入力に変更）
  service/     StaffService（create で固定値を使う、resetPassword を String を返す形に変更）
  controller/  StaffAdminController（resetPassword の引数をなくし、フラッシュ属性 tempPassword を渡す）
src/main/resources/
  application.yml                      app.staff-initial-password を追加
  templates/admin/staff/new.html       初期パスワード欄の説明を変更
  templates/admin/staff/edit.html      リセットの入力欄をなくし、生成した仮パスワードを表示
src/test/resources/
  config/application.yml               （新規）テスト用の固定値 testinit1
src/test/java/jp/bk/shiftmanager/
  auth/        StaffInitialPasswordTest, TempPasswordsTest（新規）
  controller/  StaffAdminTest（変更）
README.md                              ローカル起動時に STAFF_INITIAL_PASSWORD が必要なことを追記
```

---

### Task 1: 初期パスワードの固定値と仮パスワードの自動生成

**Files:**
- Create: `src/main/java/jp/bk/shiftmanager/auth/StaffInitialPassword.java`
- Create: `src/main/java/jp/bk/shiftmanager/auth/TempPasswords.java`
- Create: `src/test/resources/config/application.yml`
- Create: `src/test/java/jp/bk/shiftmanager/auth/StaffInitialPasswordTest.java`
- Create: `src/test/java/jp/bk/shiftmanager/auth/TempPasswordsTest.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/jp/bk/shiftmanager/form/StaffCreateForm.java`
- Modify: `src/main/java/jp/bk/shiftmanager/service/StaffService.java`（`create`・`resetPassword`）
- Modify: `src/main/java/jp/bk/shiftmanager/controller/StaffAdminController.java`（`resetPassword`）
- Modify: `src/main/resources/templates/admin/staff/new.html`
- Modify: `src/main/resources/templates/admin/staff/edit.html`
- Modify: `src/test/java/jp/bk/shiftmanager/controller/StaffAdminTest.java`
- Modify: `README.md`

**Interfaces:**
- Consumes: `PasswordRules.REGEXP` / `PasswordRules.MESSAGE`（auth）、`UserRepository.updatePassword(long id, String hash, boolean mustChange)`、`StaffService.find(long id)`（存在しなければ `BusinessException("スタッフが見つかりません")`）
- Produces:
  - `StaffInitialPassword(String value)`（`@Component`。値がルールに合わなければ `IllegalStateException`）、`String value()`
  - `TempPasswords.generate()`：`static String`
  - `StaffService.resetPassword(long id)`：`String`（生成した仮パスワード）
  - フラッシュ属性 `tempPassword`（`POST /admin/staff/{id}/password` のリダイレクト先で使う）

- [x] **Step 1: 失敗するテストを書く（部品の単体テスト）**

`src/test/java/jp/bk/shiftmanager/auth/TempPasswordsTest.java`

```java
package jp.bk.shiftmanager.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TempPasswordsTest {

    @Test
    void 英小文字と数字の8文字で紛らわしい文字を含まない() {
        for (int i = 0; i < 200; i++) {
            String password = TempPasswords.generate();
            assertThat(password).matches("[a-z2-9]{8}");
            assertThat(password).doesNotContain("0", "o", "1", "l", "i");
            assertThat(password).matches(PasswordRules.REGEXP);
        }
    }

    @Test
    void 繰り返し生成すると異なる値になる() {
        Set<String> passwords = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            passwords.add(TempPasswords.generate());
        }
        assertThat(passwords).hasSize(50);
    }
}
```

`src/test/java/jp/bk/shiftmanager/auth/StaffInitialPasswordTest.java`

```java
package jp.bk.shiftmanager.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StaffInitialPasswordTest {

    @Test
    void ルールに合う値を保持する() {
        assertThat(new StaffInitialPassword("testinit1").value()).isEqualTo("testinit1");
    }

    @Test
    void 短い値は起動時にエラーにする() {
        assertThatThrownBy(() -> new StaffInitialPassword("short12"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STAFF_INITIAL_PASSWORD");
    }

    @Test
    void 全角を含む値は起動時にエラーにする() {
        assertThatThrownBy(() -> new StaffInitialPassword("パスワード１２３"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 空の値は起動時にエラーにする() {
        assertThatThrownBy(() -> new StaffInitialPassword(""))
                .isInstanceOf(IllegalStateException.class);
    }
}
```

- [x] **Step 2: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=TempPasswordsTest,StaffInitialPasswordTest`
Expected: コンパイルエラー（`TempPasswords`・`StaffInitialPassword` が存在しない）

- [x] **Step 3: 部品を実装する**

`src/main/java/jp/bk/shiftmanager/auth/TempPasswords.java`

```java
package jp.bk.shiftmanager.auth;

import java.security.SecureRandom;

/** 管理者がリセットしたときの仮パスワードを生成する。口頭やLINEで伝えやすいよう英小文字と数字だけにする */
public final class TempPasswords {

    /** 読み間違えやすい 0 o 1 l i を除いた文字 */
    private static final String CHARS = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TempPasswords() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
```

`src/main/java/jp/bk/shiftmanager/auth/StaffInitialPassword.java`

```java
package jp.bk.shiftmanager.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** スタッフ登録で初期パスワードを省略したときに使う共通の固定値。ルールに合わなければ起動を止める */
@Component
public class StaffInitialPassword {

    private final String value;

    public StaffInitialPassword(@Value("${app.staff-initial-password}") String value) {
        if (value == null || !value.matches(PasswordRules.REGEXP)) {
            throw new IllegalStateException(
                    "環境変数 STAFF_INITIAL_PASSWORD がパスワードのルールに合いません：" + PasswordRules.MESSAGE);
        }
        this.value = value;
    }

    public String value() {
        return value;
    }
}
```

`src/main/resources/application.yml` の `app:` に追加（既定値は付けない。未設定なら起動時にプレースホルダーを解決できずエラーになる）

```yaml
app:
  # remember-me Cookieの署名鍵。本番では必ず環境変数で上書きする
  remember-me-key: ${REMEMBER_ME_KEY:dev-only-remember-me-key}
  # スタッフ登録で初期パスワードを省略したときの共通の固定値。未設定だと起動しない
  staff-initial-password: ${STAFF_INITIAL_PASSWORD}
```

`src/test/resources/config/application.yml`（新規。`classpath:/config/application.yml` は `classpath:/application.yml` より優先されるので、main の設定を残したまま上書きできる）

```yaml
app:
  # テスト用の固定値（本番の値はここに書かない）
  staff-initial-password: testinit1
```

- [x] **Step 4: 部品のテストが通ることを確認する**

Run: `./mvnw test -Dtest=TempPasswordsTest,StaffInitialPasswordTest`
Expected: PASS（6件）

- [x] **Step 5: 失敗するテストを書く（StaffAdminTest の変更）**

`src/test/java/jp/bk/shiftmanager/controller/StaffAdminTest.java` を以下のとおり変更する。

import に追加：

```java
import org.springframework.test.web.servlet.MvcResult;
```

既存の `登録したスタッフは初期パスワードでログインでき初回に変更が必要になる` の直後に追加：

```java
    @Test
    void 初期パスワードを空欄で登録すると共通の固定値になり初回に変更が必要になる() throws Exception {
        mvc.perform(post("/admin/staff").with(user(boss)).with(csrf())
                        .param("loginId", "hanako").param("name", "佐藤花子").param("password", ""))
                .andExpect(redirectedUrl("/admin/staff"));

        User hanako = userMapper.findByLoginId("hanako");
        assertThat(passwordEncoder.matches("testinit1", hanako.getPasswordHash())).isTrue();
        assertThat(hanako.isMustChangePassword()).isTrue();
    }

    @Test
    void 初期パスワードの項目自体を送らなくても共通の固定値で登録できる() throws Exception {
        mvc.perform(post("/admin/staff").with(user(boss)).with(csrf())
                        .param("loginId", "hanako").param("name", "佐藤花子"))
                .andExpect(redirectedUrl("/admin/staff"));

        User hanako = userMapper.findByLoginId("hanako");
        assertThat(passwordEncoder.matches("testinit1", hanako.getPasswordHash())).isTrue();
    }

    @Test
    void 初期パスワードを入力した場合は固定値ではなく入力した値になる() throws Exception {
        mvc.perform(post("/admin/staff").with(user(boss)).with(csrf())
                        .param("loginId", "hanako").param("name", "佐藤花子").param("password", "initpass1"))
                .andExpect(redirectedUrl("/admin/staff"));

        User hanako = userMapper.findByLoginId("hanako");
        assertThat(passwordEncoder.matches("initpass1", hanako.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("testinit1", hanako.getPasswordHash())).isFalse();
    }
```

既存の `パスワードをリセットすると仮パスワードが有効になり次回変更が必要になる` と `仮パスワードが短いとリセットされない` を削除し、以下に置き換える：

```java
    @Test
    void パスワードをリセットすると生成された仮パスワードが有効になり次回変更が必要になる() throws Exception {
        User taro = data.user("taro", "山田太郎", false);

        MvcResult result = mvc.perform(post("/admin/staff/{id}/password", taro.getId()).with(user(boss)).with(csrf()))
                .andExpect(redirectedUrl("/admin/staff/" + taro.getId() + "/edit"))
                .andExpect(flash().attributeExists("tempPassword"))
                .andReturn();

        String tempPassword = (String) result.getFlashMap().get("tempPassword");
        assertThat(tempPassword).matches("[a-z2-9]{8}");
        User updated = userMapper.findById(taro.getId());
        assertThat(passwordEncoder.matches(tempPassword, updated.getPasswordHash())).isTrue();
        assertThat(updated.isMustChangePassword()).isTrue();
    }

    @Test
    void リセット後の編集画面に仮パスワードが表示される() throws Exception {
        User taro = data.user("taro", "山田太郎", false);

        mvc.perform(get("/admin/staff/{id}/edit", taro.getId()).with(user(boss))
                        .flashAttr("tempPassword", "abcd2345"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("abcd2345")))
                .andExpect(content().string(Matchers.containsString("再表示できません")));
    }

    @Test
    void 存在しないスタッフはリセットできずエラーを表示する() throws Exception {
        mvc.perform(post("/admin/staff/{id}/password", 999999).with(user(boss)).with(csrf()))
                .andExpect(redirectedUrl("/admin/staff/999999/edit"))
                .andExpect(flash().attribute("error", "スタッフが見つかりません"))
                .andExpect(flash().attributeCount(1));
    }
```

既存の `全角を含む初期パスワードは登録できない` はそのまま残す（入力した場合のルールは変わらない）。

- [x] **Step 6: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=StaffAdminTest`
Expected: FAIL。空欄・未送信の登録はバリデーションエラーで `admin/staff/new` を返す。リセットは `tempPassword` パラメータ不足で400になる。編集画面に仮パスワードが表示されない

- [x] **Step 7: フォーム・サービス・コントローラーを実装する**

`src/main/java/jp/bk/shiftmanager/form/StaffCreateForm.java`

```java
package jp.bk.shiftmanager.form;

import jakarta.validation.constraints.Pattern;
import jp.bk.shiftmanager.auth.PasswordRules;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class StaffCreateForm extends StaffEditForm {

    /** 初期パスワード（本人に口頭やLINEで伝える）。空欄なら共通の固定値を使う */
    @Pattern(regexp = "|" + PasswordRules.REGEXP, message = PasswordRules.MESSAGE)
    private String password;
}
```

`src/main/java/jp/bk/shiftmanager/service/StaffService.java`

フィールドと import を追加：

```java
import jp.bk.shiftmanager.auth.StaffInitialPassword;
import jp.bk.shiftmanager.auth.TempPasswords;
```

```java
    private final UserRepository userRepository;
    private final PositionRepository positionRepository;
    private final PasswordEncoder passwordEncoder;
    private final StaffInitialPassword staffInitialPassword;
```

`create` のパスワード設定を変更：

```java
    /** スタッフを登録する。初期パスワードが空欄なら共通の固定値を使い、初回ログイン時にパスワード変更が必要な状態で作成する */
    @Transactional
    public long create(StaffCreateForm form) {
        checkLoginIdUnique(form.getLoginId(), 0);
        checkPosition(form.getPositionId());
        String password = (form.getPassword() == null || form.getPassword().isEmpty())
                ? staffInitialPassword.value()
                : form.getPassword();
        User user = new User();
        user.setLoginId(form.getLoginId());
        user.setName(form.getName());
        user.setPositionId(form.getPositionId());
        user.setAdmin(form.isAdmin());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEnabled(true);
        user.setMustChangePassword(true);
        userRepository.insert(user);
        return user.getId();
    }
```

`resetPassword` を置き換え（`PasswordRules` の import が不要になれば削除する）：

```java
    /** 管理者による仮パスワードへのリセット。生成した仮パスワードを返す。次回ログイン時に変更が必要になる */
    @Transactional
    public String resetPassword(long id) {
        find(id);
        String tempPassword = TempPasswords.generate();
        userRepository.updatePassword(id, passwordEncoder.encode(tempPassword), true);
        return tempPassword;
    }
```

`src/main/java/jp/bk/shiftmanager/controller/StaffAdminController.java` の `resetPassword` を置き換え（`@RequestParam` は `setEnabled` で使うので import は残す）：

```java
    @PostMapping("/{id}/password")
    public String resetPassword(@PathVariable long id, RedirectAttributes redirectAttributes) {
        try {
            String tempPassword = staffService.resetPassword(id);
            redirectAttributes.addFlashAttribute("tempPassword", tempPassword);
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/staff/" + id + "/edit";
    }
```

- [x] **Step 8: 画面を変更する**

`src/main/resources/templates/admin/staff/new.html` の初期パスワード欄を置き換え：

```html
    <label class="block">
      <span class="text-sm">初期パスワード（空欄なら共通の初期パスワード。入力する場合は半角英数字記号 8〜72文字）</span>
      <input th:field="*{password}" autocomplete="off" class="input">
      <span th:errors="*{password}" class="text-sm text-red-700"></span>
    </label>
```

`src/main/resources/templates/admin/staff/edit.html` の「パスワードのリセット」以降（`</main>` の前まで）を置き換え：

```html
  <h2 class="mb-2 font-bold">パスワードのリセット</h2>
  <div th:if="${tempPassword}" class="mb-3 rounded bg-green-50 p-3 text-sm text-green-800">
    <p>仮パスワードにリセットしました。本人に伝えてください。</p>
    <p class="my-2 font-mono text-2xl font-bold tracking-widest text-stone-900" th:text="${tempPassword}"></p>
    <p>この画面を離れると再表示できません。わからなくなった場合はもう一度リセットしてください。</p>
  </div>
  <form th:action="@{/admin/staff/{id}/password(id=${id})}" method="post" class="card space-y-3">
    <p class="text-sm text-stone-600">仮パスワードを自動で作成します。本人は次回ログイン時に変更が必要になります。</p>
    <button class="btn-secondary">リセットする</button>
  </form>
```

- [x] **Step 9: README を更新する**

`README.md` の「ローカルでの起動」を以下に置き換える（実際の固定値は書かない）：

````markdown
## ローカルでの起動
テストには Docker Desktop が必要（Testcontainers）。

起動には環境変数 `STAFF_INITIAL_PASSWORD`（スタッフ登録で初期パスワードを省略したときの共通の値。半角8〜72文字）が必要。未設定だと起動しない。

```
npm run build
$env:STAFF_INITIAL_PASSWORD = "（共通の初期パスワード）"
./mvnw spring-boot:run
```
````

- [x] **Step 10: 全テストが通ることを確認する**

Run: `./mvnw test`
Expected: 全件 PASS（Docker Desktop の起動が必要）

- [x] **Step 11: ブラウザで確認する（ユーザーに依頼）**

環境変数を設定して起動し、以下をユーザーに確認してもらう。
- 初期パスワードを空欄でスタッフを登録し、そのスタッフが共通の値でログインでき、パスワード変更画面に移ること
- 編集画面で「リセットする」を押すと仮パスワードが大きく表示され、そのパスワードでログインできること。再読み込みすると表示が消えること

- [x] **Step 12: コミットしてチェックボックスを更新する**

```bash
git add src/main/java/jp/bk/shiftmanager/auth/StaffInitialPassword.java \
        src/main/java/jp/bk/shiftmanager/auth/TempPasswords.java \
        src/main/java/jp/bk/shiftmanager/form/StaffCreateForm.java \
        src/main/java/jp/bk/shiftmanager/service/StaffService.java \
        src/main/java/jp/bk/shiftmanager/controller/StaffAdminController.java \
        src/main/resources/application.yml \
        src/main/resources/templates/admin/staff/new.html \
        src/main/resources/templates/admin/staff/edit.html \
        src/test/resources/config/application.yml \
        src/test/java/jp/bk/shiftmanager/auth/StaffInitialPasswordTest.java \
        src/test/java/jp/bk/shiftmanager/auth/TempPasswordsTest.java \
        src/test/java/jp/bk/shiftmanager/controller/StaffAdminTest.java \
        README.md
git commit -m "feat: 初期パスワードを省略時は共通の固定値にし、仮パスワードを自動生成する

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

その後、この計画ファイルの Task 1 のチェックボックスをすべて `- [x]` にして `docs: Plan 5 Task 1 完了` でコミットし、作業を停止して報告する。
