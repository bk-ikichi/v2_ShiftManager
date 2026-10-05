# Plan 7 取扱説明書（ヘルプ画面・PDF） Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **セッション運用：** 1セッション1 Task。Taskの最後のステップ（コミットとチェックボックス更新）が終わったら停止してユーザーに報告する。次のTaskは `/clear` 後の新しいセッションで行う。新しいセッションではこの冒頭（File Structureまで）と、未完了の最初のTaskの範囲だけを読む（`CLAUDE.md` 参照）。

**Goal:** スタッフ用・管理者用の使い方をアプリ内のヘルプ画面として表示し、同じ内容のPDFをアプリからダウンロードできるようにする。画像とPDFは `npm run manual` 1回で作り直せるようにする。

**Architecture:** ヘルプ画面（`/help`・`/admin/help`）はThymeleafの静的な本文で、`HelpController` がテンプレートを返すだけ。画像とPDFは `static/manual/`・`static/admin/manual/` に置き、既存のSpring Securityの設定（静的ファイルの許可は css・js・icons のみ、`/admin/**` は管理者のみ）でアクセスを制限する。`scripts/manual/build.mjs`（Node）が説明書専用DBを作り直してアプリを8081で起動し、デモデータを入れ、Playwright（`playwright-core` + インストール済みのEdge）で撮影とPDF出力を行う。

**Tech Stack:** Java 17、Spring Boot 4.1.1、Thymeleaf 3.1 + thymeleaf-extras-springsecurity、Tailwind CSS 4、JUnit 5 + MockMvc + Testcontainers 2、Node 24 + playwright-core、PostgreSQL 17（compose）

**Spec:** `docs/superpowers/specs/2026-10-05-user-manual-design.md`

## Global Constraints

- 画面の文言・コードのコメントはすべて日本語
- パッケージは層ごと。`HelpController` は業務ロジックがないためServiceを呼ばず、テンプレート名を返すだけ
- URL：スタッフ用ヘルプ `/help`、管理者用ヘルプ `/admin/help`、PDF `/manual/staff.pdf`・`/admin/manual/admin.pdf`、画像 `/manual/images/*.png`・`/admin/manual/images/*.png`
- `SecurityConfig.STATIC_RESOURCES` と `WebConfig` の除外パスは変更しない（PDF・画像はログイン必須、`/admin/` 配下は管理者のみ、のままにする）
- 配信用のコントローラーは作らない。PDF・画像は `src/main/resources/static/` 配下の静的ファイルとして配る
- 画像のファイル名は下の「画像の一覧」のとおり。テンプレートと `build.mjs` で一致させる
- スタッフ用の画像はスマホ幅（390×844、デバイスピクセル比2）、管理者用の画像はPC幅（1280×800、デバイスピクセル比1）で撮る
- 説明書専用DBは `shiftmanager_manual`。普段の開発DB `shiftmanager` には触れない。アプリはポート8081で起動する
- デモデータの日付は実行した日（日本時間）からの相対で作る。アプリに「今日を固定する設定」は追加しない
- 追加する依存は開発用の `playwright-core` のみ（ブラウザ本体はダウンロードしない）。Javaの依存は増やさない。`Dockerfile` のCSSビルド段階の `npm ci` でも入るが、ブラウザを落とさないため本番イメージには影響しない（`Dockerfile` は変更しない）
- ビルド済みCSS `src/main/resources/static/css/app.css` は `.gitignore` 対象のためコミットしない
- Tailwindのクラス名は文字列連結で組み立てない
- コミットメッセージの末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` を付ける
- テスト実行には Docker Desktop の起動が必要（Testcontainers）

### 画像の一覧

| ファイル（`static/` からの相対パス） | 撮る画面 | 撮り方 |
|---|---|---|
| `manual/images/login.png` | `/login`（未ログイン） | スマホ・画面内 |
| `manual/images/password.png` | `taro` でログイン直後の `/password` | スマホ・画面内 |
| `manual/images/home.png` | `hanako` の `/` | スマホ・ページ全体 |
| `manual/images/menu.png` | `hanako` の `/` でメニューを開いた状態 | スマホ・画面内 |
| `manual/images/requests.png` | `hanako` の `/requests?month=<来月>` | スマホ・画面内 |
| `manual/images/patterns.png` | `hanako` の `/mypage/patterns` | スマホ・ページ全体 |
| `manual/images/shifts.png` | `hanako` の `/shifts?date=<今日+2日>` | スマホ・画面内 |
| `admin/manual/images/positions.png` | `/admin/positions` | PC・ページ全体 |
| `admin/manual/images/staff-list.png` | `/admin/staff` | PC・ページ全体 |
| `admin/manual/images/staff-new.png` | `/admin/staff/new` | PC・ページ全体 |
| `admin/manual/images/staff-edit.png` | `/admin/staff/3/edit`（山田 花子） | PC・ページ全体 |
| `admin/manual/images/requests.png` | `/admin/requests?date=<次のサイクル開始日>` | PC・画面内 |
| `admin/manual/images/request-edit.png` | `/admin/requests/edit?userId=3&date=<次のサイクル開始日>` | PC・ページ全体 |
| `admin/manual/images/shifts.png` | `/admin/shifts?date=<今日+1日>`（公開済み） | PC・ページ全体 |
| `admin/manual/images/shifts-reflect.png` | `/admin/shifts?date=<今日+9日>`（未登録） | PC・画面内 |
| `admin/manual/images/settings.png` | `/admin/settings` | PC・画面内 |

管理者用の画像は `admin`（佐藤 店長）でログインして撮る。

## Review Focus

1. パスワード変更が必要なユーザー（初回ログイン・リセット直後）が `/help` を開く：ヘルプではなく `/password` へ誘導される（既存の `ForcePasswordChangeInterceptor` の動き）→ Task 1でテスト
2. スタッフが管理者用のPDF・画像のURLを直接開く（`/admin/manual/admin.pdf`・`/admin/manual/images/shifts.png`）：403になり取得できない → Task 3でテスト
3. 未ログインでPDFのURLを開く（LINEなどでURLだけ共有された）：ログイン画面へリダイレクトされる → Task 3でテスト
4. ヘルプ本文の `<img>` のパスと撮影スクリプトのファイル名がずれる（画像のリンク切れ）：ヘルプ画面のすべての画像が200で取得できる → Task 3でテスト
5. 撮影の途中で失敗する（Edgeがない、ポート8081が使用中、composeのDBが止まっている、画面の要素が見つからない）：エラーの理由が表示され、8081で起動したアプリのプロセスが残らない → Task 3で手動確認（Step参照）

## File Structure

```
src/main/java/jp/bk/shiftmanager/
  controller/  HelpController（新規：/help と /admin/help のテンプレートを返す）
src/main/resources/
  templates/help/parts.html    （新規：画像の部品 shot）
  templates/help/staff.html    （新規：スタッフ用の本文）
  templates/help/admin.html    （新規：管理者用の本文）
  templates/layout.html        ヘッダーに「使い方」を追加、印刷時はヘッダーを隠す
  static/manual/staff.pdf、static/manual/images/*.png              （新規：build.mjs が生成）
  static/admin/manual/admin.pdf、static/admin/manual/images/*.png  （新規：build.mjs が生成）
src/main/frontend/app.css      ヘルプ本文のスタイル（.help）と印刷時の改ページ
scripts/manual/build.mjs       （新規：説明書専用DB・アプリ起動・デモデータ・撮影・PDF出力）
scripts/manual/demo-data.sql   （新規：撮影用のデモデータ）
package.json                   manual スクリプトと playwright-core
CLAUDE.md、README.md            画面を変えたら npm run manual を実行する旨
src/test/java/jp/bk/shiftmanager/
  controller/HelpTest（新規）
```

---

### Task 1: ヘルプ画面の土台とスタッフ用の本文

**Files:**
- Create: `src/main/java/jp/bk/shiftmanager/controller/HelpController.java`
- Create: `src/main/resources/templates/help/parts.html`
- Create: `src/main/resources/templates/help/staff.html`
- Create: `src/test/java/jp/bk/shiftmanager/controller/HelpTest.java`
- Modify: `src/main/resources/templates/layout.html`（`header` の class、マイページの前に「使い方」）
- Modify: `src/main/frontend/app.css`（`@layer components` の末尾に `.help` を追加）

**Interfaces:**
- Consumes: `IntegrationTestBase`（`mvc`・`data`）、`TestData.user(loginId, name, admin)`・`login(user)`・`requirePasswordChange(user)`、`layout :: head(title)`・`layout :: header`・`layout :: navLink(path, label)`
- Produces:
  - `HelpController`：`GET /help` → `"help/staff"`、`GET /admin/help` → `"help/admin"`（テンプレートはTask 2で作る）
  - フラグメント `help/parts :: shot(src, caption, phone)`（`src`：`/manual/images/home.png` のような絶対パス、`caption`：説明文、`phone`：スマホ画像なら `true`）
  - CSSクラス `.help`（本文の見出し・段落・リスト・`.note`・`figure` の印刷時の扱い）
  - ヘルプ本文の構成ルール：`<main class="help ...">` 直下に `h1`、`print:hidden` のボタン行、`<nav>` の目次、章ごとの `<section id="...">`

この時点では画像はまだないため、ヘルプ画面の画像はリンク切れで表示される（Task 3で生成する）。

- [ ] **Step 1: 失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/controller/HelpTest.java`

```java
package jp.bk.shiftmanager.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.entity.User;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HelpTest extends IntegrationTestBase {

    User hanako;
    LoginUser staff;
    LoginUser admin;

    @BeforeEach
    void setUp() {
        hanako = data.user("hanako", "山田花子", false);
        staff = data.login(hanako);
        admin = data.login(data.user("boss", "店長", true));
    }

    @Test
    void スタッフ向けの使い方を表示できる() throws Exception {
        mvc.perform(get("/help").with(user(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("使い方（スタッフ向け）")))
                .andExpect(content().string(Matchers.containsString("href=\"/manual/staff.pdf\"")))
                // スタッフには管理者向けへのリンクを出さない
                .andExpect(content().string(Matchers.not(Matchers.containsString("href=\"/admin/help\""))));
    }

    @Test
    void 管理者にはスタッフ向けの使い方に管理者向けへのリンクが出る() throws Exception {
        mvc.perform(get("/help").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("href=\"/admin/help\"")));
    }

    @Test
    void ヘッダーに使い方のリンクがある() throws Exception {
        mvc.perform(get("/mypage").with(user(staff)))
                .andExpect(content().string(Matchers.containsString("href=\"/help\"")));
    }

    @Test
    void 未ログインではログイン画面へ移動する() throws Exception {
        mvc.perform(get("/help")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void パスワード変更が必要な人はパスワード変更画面へ移動する() throws Exception {
        data.requirePasswordChange(hanako);
        mvc.perform(get("/help").with(user(data.login(hanako))))
                .andExpect(redirectedUrl("/password"));
    }
}
```

- [x] **Step 2: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=HelpTest`
Expected: `スタッフ向けの使い方を表示できる` などが FAIL（`/help` が404）

- [x] **Step 3: Controllerを作る**

`src/main/java/jp/bk/shiftmanager/controller/HelpController.java`

```java
package jp.bk.shiftmanager.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 使い方（ヘルプ画面）。本文はテンプレートに直接書いているため、業務処理は呼ばない */
@Controller
public class HelpController {

    @GetMapping("/help")
    public String staff() {
        return "help/staff";
    }

    /** 管理者向け。/admin/** のため管理者のみ表示できる */
    @GetMapping("/admin/help")
    public String admin() {
        return "help/admin";
    }
}
```

- [x] **Step 4: ヘッダーに「使い方」を足し、印刷時にヘッダーを隠す**

`src/main/resources/templates/layout.html` の `header` の class に `print:hidden` を足す。

```html
<header th:fragment="header" id="site-header" class="group border-b border-stone-200 bg-white print:hidden">
```

右側のグループ（名前・マイページ・ログアウト）で、マイページの前に「使い方」を足す。

```html
        <span class="px-3 py-2 text-stone-500" sec:authentication="principal.name">名前</span>
        <a th:replace="~{layout :: navLink('/help', '使い方')}"></a>
        <a th:replace="~{layout :: navLink('/mypage', 'マイページ')}"></a>
```

- [x] **Step 5: ヘルプ本文のスタイルを足す**

`src/main/frontend/app.css` の `@layer components { ... }` の中、`.time-grid` の後ろに追加する。

```css
  /* 使い方（ヘルプ画面）の本文 */
  .help h2 {
    @apply mt-10 mb-3 border-b border-stone-300 pb-1 text-lg font-bold;
  }
  .help h3 {
    @apply mt-6 mb-2 font-bold;
  }
  .help p {
    @apply my-2 leading-relaxed;
  }
  .help ol {
    @apply my-2 list-decimal space-y-1 pl-6;
  }
  .help ul {
    @apply my-2 list-disc space-y-1 pl-6;
  }
  .help section a,
  .help nav a {
    @apply text-amber-800 underline;
  }
  .help .note {
    @apply my-3 rounded bg-amber-50 p-3 text-sm text-amber-900;
  }
  /* 印刷（PDF）：章ごとに改ページし、画像の途中で改ページしない。縦に長い画像は1ページに収める */
  @media print {
    .help section {
      break-before: page;
    }
    .help figure {
      break-inside: avoid;
    }
    .help figure img {
      width: auto;
      max-width: 100%;
      max-height: 230mm;
      margin-inline: auto;
    }
  }
```

- [x] **Step 6: 画像の部品を作る**

`src/main/resources/templates/help/parts.html`

```html
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org">
<body>
<!-- 使い方の画面の画像。phone が true ならスマホの画像として幅を抑えて中央に置く -->
<figure th:fragment="shot(src, caption, phone)" class="my-4">
  <img th:src="@{${src}}" th:alt="${caption}" class="rounded border border-stone-200"
       th:classappend="${phone} ? 'mx-auto w-full max-w-xs' : 'w-full'">
  <figcaption th:text="${caption}" class="mt-1 text-center text-xs text-stone-500">説明</figcaption>
</figure>
</body>
</html>
```

- [x] **Step 7: スタッフ用の本文を作る**

`src/main/resources/templates/help/staff.html`

```html
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security">
<head th:replace="~{layout :: head('使い方')}"></head>
<body class="min-h-screen bg-stone-50 text-stone-900 print:bg-white">
<header th:replace="~{layout :: header}"></header>
<main class="help mx-auto max-w-3xl px-4 py-6">
  <h1 class="text-xl font-bold">シフト管理 使い方（スタッフ向け）</h1>
  <div class="mt-3 flex flex-wrap items-center gap-3 print:hidden">
    <a th:href="@{/manual/staff.pdf}" download class="btn-secondary">PDFをダウンロード</a>
    <a sec:authorize="hasRole('ADMIN')" th:href="@{/admin/help}" class="text-amber-800 underline">管理者向けの使い方はこちら</a>
  </div>

  <nav class="card mt-6" aria-label="目次">
    <p class="font-bold">目次</p>
    <ol>
      <li><a href="#start">はじめに</a></li>
      <li><a href="#home">トップ画面の見方</a></li>
      <li><a href="#requests">シフト希望を出す</a></li>
      <li><a href="#patterns">パターンを作る</a></li>
      <li><a href="#shifts">確定シフトを見る</a></li>
      <li><a href="#trouble">困ったとき</a></li>
    </ol>
  </nav>

  <section id="start">
    <h2>1. はじめに</h2>
    <h3>ログイン</h3>
    <figure th:replace="~{help/parts :: shot('/manual/images/login.png', 'ログイン画面', true)}"></figure>
    <ol>
      <li>店長（管理者）から受け取ったログインIDと初期パスワードを入力します。</li>
      <li>「ログイン状態を保持する（10日間）」にチェックを入れておくと、10日間はログインし直さずに使えます。家族などと共用の端末ではチェックを外してください。</li>
      <li>「ログイン」を押します。</li>
    </ol>

    <h3>初回のパスワード変更</h3>
    <figure th:replace="~{help/parts :: shot('/manual/images/password.png', 'パスワード変更画面', true)}"></figure>
    <p>初めてログインしたときと、管理者がパスワードをリセットした後は、パスワード変更画面が開きます。変更するまでほかの画面は使えません。</p>
    <ol>
      <li>「現在のパスワード」に、初期パスワード（リセットされた場合は仮パスワード）を入力します。</li>
      <li>「新しいパスワード」と「新しいパスワード（確認）」に同じパスワードを入力します。半角英数字記号で8〜72文字です。</li>
      <li>「変更する」を押します。</li>
    </ol>
    <p class="note">パスワードは、マイページの「パスワード変更」からいつでも変えられます。</p>

    <h3>メニュー</h3>
    <figure th:replace="~{help/parts :: shot('/manual/images/menu.png', 'メニューを開いたところ', true)}"></figure>
    <p>スマホでは、右上の三本線のボタンでメニューを開きます。左上の「シフト管理」を押すと、トップ画面に戻ります。</p>
    <ul>
      <li>シフト：公開されたシフトを日ごとに見る</li>
      <li>申請：シフト希望を出す</li>
      <li>使い方：この説明</li>
      <li>マイページ：申請パターンの登録、パスワード変更</li>
      <li>ログアウト</li>
    </ul>

    <h3>ホーム画面に追加する</h3>
    <p>スマホのホーム画面に追加すると、アプリのように開けます。</p>
    <ul>
      <li>iPhone（Safari）：画面下の共有ボタン →「ホーム画面に追加」</li>
      <li>Android（Chrome）：右上のメニュー →「ホーム画面に追加」</li>
    </ul>
  </section>

  <section id="home">
    <h2>2. トップ画面の見方</h2>
    <figure th:replace="~{help/parts :: shot('/manual/images/home.png', 'トップ画面', true)}"></figure>
    <p>ログインすると最初に開く画面です。上から順に、次の内容が表示されます。</p>

    <h3>変更あり</h3>
    <p>公開済みのシフトが変更されたとき（時間の変更・追加・取り消し）に表示されます。内容を確認したら「確認済み」を押してください。押すまで表示され続けます。シフトが取り消された場合は「この日のシフトは取り消されました」と表示されます。</p>

    <h3>次回の出勤</h3>
    <p>公開されているシフトのうち、次に出勤する日・時間・ポジションです。</p>

    <h3>カレンダー</h3>
    <ul>
      <li>自分の出勤日は色付きで、出勤時刻（IN）が表示されます。</li>
      <li>公開済みの日を押すと、その日の全員のシフト（日別シフト一覧）が開きます。</li>
      <li>灰色の日はまだ公開されていないため、押せません。</li>
      <li>「&lt; 前の月」「次の月 &gt;」で、先月から2か月後まで切り替えられます。</li>
    </ul>

    <h3>申請の締切</h3>
    <p>次の締切と、提出済みかどうかが表示されます。「未提出です。申請してください」と出ていたら、締切までに申請してください。</p>

    <h3>勤務予定時間</h3>
    <p>今月・来月の公開済みシフトの、IN〜OUTの合計です。休憩時間を含みます。</p>
  </section>

  <section id="requests">
    <h2>3. シフト希望を出す</h2>
    <h3>サイクルと締切</h3>
    <p>シフト希望は、1か月を次の3つの期間（サイクル）に分けて締め切ります。</p>
    <ul>
      <li>1日〜10日</li>
      <li>11日〜20日</li>
      <li>21日〜月末</li>
    </ul>
    <p>締切は、通常はサイクルが始まる日の5日前です（例：11日〜20日分は6日まで）。店長が変更している場合があるため、申請画面に表示される締切日を確認してください。締切を過ぎたサイクルは灰色になり、変更できません。申請は翌々月の末日まで出せます。</p>

    <h3>申請のしかた</h3>
    <figure th:replace="~{help/parts :: shot('/manual/images/requests.png', 'シフト希望の申請画面', true)}"></figure>
    <ol>
      <li>メニューの「申請」を開きます。1か月が1ページで、1行が1日です。</li>
      <li>出勤できる日に、IN（出勤時刻）とOUT（退勤時刻）を選びます。8:00〜23:00の30分単位です。パターンを選ぶと、IN・OUTが自動で入ります（あとから変えられます）。</li>
      <li>伝えたいことがあれば「備考」に書きます。申請できるのは1日1つの時間帯なので、1日に2回に分けて入りたい場合なども備考に書いてください。</li>
      <li>画面の下の「登録する」を押します。そのページの全部の日がまとめて保存されます。</li>
    </ol>
    <p class="note">「登録する」を押さずに別の月へ移動すると、入力した内容は保存されません（移動する前に確認が表示されます）。</p>

    <h3>この期間は出勤できない</h3>
    <p>サイクルの間ずっと出勤できないときは、サイクルの見出しにある「この期間は出勤できない」にチェックを入れて「登録する」を押してください。申請が1日もなくても「提出済み」になります。</p>
    <p>サイクルの中に申請が1日以上あるか、このチェックが入っていれば「提出済み」です。</p>

    <h3>締切後に変えたいとき</h3>
    <p>締切を過ぎたサイクルは、自分では変更できません。店長（管理者）に直接伝えてください。管理者が代わりに変更します。</p>
  </section>

  <section id="patterns">
    <h2>4. パターンを作る</h2>
    <figure th:replace="~{help/parts :: shot('/manual/images/patterns.png', '申請パターン画面', true)}"></figure>
    <p>よく使う時間帯を「パターン」として登録しておくと、申請画面で選ぶだけでIN・OUTが入ります。パターンは自分だけが使えます。</p>
    <ol>
      <li>メニューの「マイページ」から「申請パターン」を開きます。</li>
      <li>下の「追加」に、名前（例：朝）、IN、OUTを入れて「追加する」を押します。</li>
      <li>変えるときは、その行を直して「保存」を押します。要らなくなったら「削除」を押します。</li>
    </ol>
  </section>

  <section id="shifts">
    <h2>5. 確定シフトを見る</h2>
    <figure th:replace="~{help/parts :: shot('/manual/images/shifts.png', '日別シフト一覧', true)}"></figure>
    <p>公開されたシフトを、その日の全員分見られます。メニューの「シフト」か、トップ画面のカレンダーで日付を押すと開きます。</p>
    <ul>
      <li>左に名前、右に8:00〜23:00の時間軸があり、出勤している時間を横の帯で表しています。時刻は帯の中（短い勤務は帯の横）に書かれています。</li>
      <li>帯の色はポジションを表します。色の意味は上の凡例に書かれています。緑は社員です。</li>
      <li>ポジションごとにまとまり、出勤の早い順に並びます。</li>
      <li>上の日付を押すと、前後の日に移動できます。</li>
    </ul>

    <h3>見られる日の範囲</h3>
    <ul>
      <li>過去の日は、前月の1日以降を見られます。</li>
      <li>先の日は、公開されている最後の日まで見られます。まだ公開されていない日は「この日のシフトはまだ公開されていません」と表示されます。</li>
    </ul>
  </section>

  <section id="trouble">
    <h2>6. 困ったとき</h2>
    <h3>パスワードを忘れた</h3>
    <p>店長（管理者）に伝えてください。仮パスワードを発行してもらい、それでログインしてから新しいパスワードに変更します。</p>

    <h3>ログインできない</h3>
    <p>ログインIDとパスワードの入力ミス（大文字・小文字、全角・半角）がないか確認してください。退職などで無効になったアカウントはログインできません。</p>

    <h3>「ログイン情報が変更されたため、ログアウトしました」と出た</h3>
    <p>管理者がパスワードのリセットなどを行うと表示されます。もう一度ログインしてください。</p>

    <h3>シフトが表示されない</h3>
    <p>その日のシフトがまだ公開されていない可能性があります。公開されるまでお待ちください。</p>

    <h3>締切後に申請を直したい</h3>
    <p>自分では変更できません。店長（管理者）に伝えてください。</p>
  </section>
</main>
</body>
</html>
```

- [x] **Step 8: CSSをビルドしてテストを通す**

Run: `npm run build`、続けて `./mvnw test -Dtest=HelpTest,HeaderNavTest`
Expected: PASS（`HeaderNavTest` が既存のヘッダーの動きを壊していないこと）

- [ ] **Step 9: 画面を目で確認する**

アプリを起動し（`README.md` の「ローカルでの起動」）、スタッフでログインしてスマホ幅とPC幅で `/help` を開く。目次・見出し・リストの見た目が崩れていないこと、ヘッダーに「使い方」があり現在地の色が付くことを確認する（画像はリンク切れでよい）。

- [x] **Step 10: 全テストを流してコミットし、チェックボックスを更新する**

Run: `./mvnw test`
Expected: PASS

```bash
git add src/main/java/jp/bk/shiftmanager/controller/HelpController.java src/main/resources/templates/help src/main/resources/templates/layout.html src/main/frontend/app.css src/test/java/jp/bk/shiftmanager/controller/HelpTest.java
git commit -m "feat: 使い方（スタッフ向け）のヘルプ画面を追加する

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

この計画ファイルの Task 1 のチェックボックスを `[x]` にしてコミットし、停止して報告する。

---

### Task 2: 管理者用の本文

**Files:**
- Create: `src/main/resources/templates/help/admin.html`
- Modify: `src/test/java/jp/bk/shiftmanager/controller/HelpTest.java`

**Interfaces:**
- Consumes: `HelpController`（`GET /admin/help` → `"help/admin"`）、`help/parts :: shot(src, caption, phone)`、CSSクラス `.help`（Task 1）
- Produces: `/admin/help` の本文（`build.mjs` がこのページをPDFにする）

この時点では画像はまだないため、リンク切れで表示される（Task 3で生成する）。

- [ ] **Step 1: 失敗するテストを書く**

`HelpTest` に追加する。

```java
    @Test
    void 管理者向けの使い方は管理者だけが表示できる() throws Exception {
        mvc.perform(get("/admin/help").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("使い方（管理者向け）")))
                .andExpect(content().string(Matchers.containsString("href=\"/admin/manual/admin.pdf\"")))
                .andExpect(content().string(Matchers.containsString("href=\"/help\"")));
        mvc.perform(get("/admin/help").with(user(staff))).andExpect(status().isForbidden());
    }
```

- [ ] **Step 2: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=HelpTest`
Expected: `管理者向けの使い方は管理者だけが表示できる` が FAIL（テンプレート `help/admin` がなく500）

- [ ] **Step 3: 管理者用の本文を作る**

`src/main/resources/templates/help/admin.html`

```html
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout :: head('使い方（管理者向け）')}"></head>
<body class="min-h-screen bg-stone-50 text-stone-900 print:bg-white">
<header th:replace="~{layout :: header}"></header>
<main class="help mx-auto max-w-4xl px-4 py-6">
  <h1 class="text-xl font-bold">シフト管理 使い方（管理者向け）</h1>
  <div class="mt-3 flex flex-wrap items-center gap-3 print:hidden">
    <a th:href="@{/admin/manual/admin.pdf}" download class="btn-secondary">PDFをダウンロード</a>
  </div>
  <p class="mt-4">申請の出し方やシフトの見方など、スタッフと共通の操作は<a th:href="@{/help}" class="text-amber-800 underline">スタッフ向けの使い方</a>を見てください。ここでは管理者だけが使う機能を説明します。</p>

  <nav class="card mt-6" aria-label="目次">
    <p class="font-bold">目次</p>
    <ol>
      <li><a href="#setup">最初にやること</a></li>
      <li><a href="#positions">ポジション管理</a></li>
      <li><a href="#staff">スタッフ管理</a></li>
      <li><a href="#requests">申請の確認</a></li>
      <li><a href="#shifts">シフトの転記</a></li>
      <li><a href="#publish">公開</a></li>
      <li><a href="#settings">設定</a></li>
      <li><a href="#trouble">困ったとき</a></li>
    </ol>
  </nav>

  <section id="setup">
    <h2>1. 最初にやること</h2>
    <p>アプリを使い始めるときは、次の順で準備します。</p>
    <ol>
      <li>ログインID <code>admin</code>、パスワード <code>admin</code> でログインし、表示されたパスワード変更画面で新しいパスワードに変えます（最初の1回だけ）。</li>
      <li>ポジションを登録します（<a href="#positions">ポジション管理</a>）。</li>
      <li>スタッフを登録します（<a href="#staff">スタッフ管理</a>）。自分以外の管理者も、「管理者権限を付ける」にチェックを入れてここで登録します。</li>
      <li>申請の締切日数を確認します（<a href="#settings">設定</a>）。初期値は「サイクル開始日の5日前」です。</li>
      <li>スタッフにログインIDと初期パスワードを伝え、スタッフ向けの使い方（PDF）を渡します。</li>
    </ol>
    <p class="note">最初の管理者の名前は「管理者」になっています。スタッフ管理の「編集」で自分の名前に変えてください。管理者もスタッフと同じようにシフトに載り、申請もできます。</p>
    <p>管理者のメニューには、スタッフのメニューに加えて「転記」「申請一覧」「スタッフ」「ポジション」「設定」が表示されます。</p>
  </section>

  <section id="positions">
    <h2>2. ポジション管理</h2>
    <figure th:replace="~{help/parts :: shot('/admin/manual/images/positions.png', 'ポジション管理画面', false)}"></figure>
    <p>メニューの「ポジション」で、キッチンやカウンターなどの持ち場を登録します。</p>
    <ul>
      <li>名前：転記画面と日別シフト一覧の見出しになります。</li>
      <li>表示順：小さいものから、転記画面と日別シフト一覧の上に並びます。</li>
      <li>色：転記画面と日別シフト一覧の帯の色です。水色・ピンク・赤・オレンジ・黄・紫・青・グレーから選びます。管理者は色に関係なく緑（社員）で表示されます。</li>
    </ul>
    <ol>
      <li>追加するときは、下の「追加」に名前・表示順・色を入れて「追加する」を押します。</li>
      <li>変えるときは、その行を直して「保存」を押します。</li>
    </ol>
    <p class="note">スタッフやシフトで使われているポジションは削除できません。使わなくなったら「非表示」にチェックを入れて保存してください。</p>
  </section>

  <section id="staff">
    <h2>3. スタッフ管理</h2>
    <figure th:replace="~{help/parts :: shot('/admin/manual/images/staff-list.png', 'スタッフ管理画面', false)}"></figure>
    <p>メニューの「スタッフ」で、スタッフ（管理者を含む）の登録・編集・無効化を行います。</p>

    <h3>登録する</h3>
    <figure th:replace="~{help/parts :: shot('/admin/manual/images/staff-new.png', 'スタッフ登録画面', false)}"></figure>
    <ol>
      <li>「スタッフを登録する」を押します。</li>
      <li>名前、ログインID（半角英数字と . _ - の3〜50文字）、ポジションを入力します。</li>
      <li>初期パスワードを入力します。空欄にすると、全スタッフ共通の初期パスワードになります（値はアプリの設定で決めてあります。わからない場合はアプリを設置した担当者に確認してください）。</li>
      <li>管理者にする場合は「管理者権限を付ける」にチェックを入れます。</li>
      <li>「登録する」を押します。</li>
    </ol>
    <p>スタッフは初めてログインしたときに、自分でパスワードを変更します。ポジションは、転記画面で名前の候補を並べる順番に使われます（そのポジションのスタッフが候補の上に出ます）。</p>

    <h3>編集する・パスワードをリセットする</h3>
    <figure th:replace="~{help/parts :: shot('/admin/manual/images/staff-edit.png', 'スタッフ編集画面', false)}"></figure>
    <p>一覧の「編集」で、名前・ログインID・ポジション・管理者権限を変えられます。自分の管理者権限は外せません。</p>
    <p>スタッフがパスワードを忘れたときは、編集画面の「パスワードのリセット」で「リセットする」を押します。表示された仮パスワードを本人に伝えてください。本人は次にログインしたとき、新しいパスワードに変更します。</p>
    <p class="note">仮パスワードはリセットした直後の画面にしか表示されません。わからなくなった場合は、もう一度リセットしてください。</p>

    <h3>退職したとき</h3>
    <p>一覧の「無効にする」を押します。無効にしたスタッフはログインできなくなりますが、過去のシフトは残ります。戻すときは「有効にする」を押します。スタッフは削除できません。自分自身は無効にできません。</p>
  </section>

  <section id="requests">
    <h2>4. 申請の確認</h2>
    <figure th:replace="~{help/parts :: shot('/admin/manual/images/requests.png', '申請一覧画面', false)}"></figure>
    <p>メニューの「申請一覧」で、サイクルごとに全員のシフト希望を「スタッフ × 日付」の表で見られます。PCでの利用を想定しています。</p>
    <ul>
      <li>「&lt; 前」「次 &gt;」でサイクルを切り替えます。そのサイクルの締切日も表示されます。</li>
      <li>まだ提出していないスタッフは、行の色が変わります。</li>
      <li>「この期間は出勤できない」にしたスタッフは、その旨が表示されます。</li>
      <li>備考があるマスには印が付きます。押すと備考が表示されます。</li>
      <li>日付を押すと、その日の転記画面が開きます。</li>
      <li>名前をドラッグすると、行を並び替えられます。並び順は保存されます。</li>
      <li>「Excel用にコピー」を押すと、時刻だけをコピーできます（1日につきIN・OUTの2列、申請のない日は空欄）。Excelなどにそのまま貼り付けられます。</li>
    </ul>

    <h3>代わりに申請を直す</h3>
    <figure th:replace="~{help/parts :: shot('/admin/manual/images/request-edit.png', '申請の代理編集画面', false)}"></figure>
    <ol>
      <li>表のマスを押します。</li>
      <li>IN・OUT・備考を入れて「保存する」を押します。申請を取り消すときは「この日の申請を削除する」を押します。</li>
    </ol>
    <p class="note">締切後でも保存できます。締切後の変更は、スタッフから直接聞いてここで直してください。「この期間は出勤できない」にしている期間に保存すると、そのチェックは外れます。</p>
  </section>

  <section id="shifts">
    <h2>5. シフトの転記</h2>
    <figure th:replace="~{help/parts :: shot('/admin/manual/images/shifts.png', '転記画面', false)}"></figure>
    <p>本部のシステムで作った確定シフトを、メニューの「転記」で1日ずつこのアプリに写します。</p>
    <ol>
      <li>上の日付で、転記する日を選びます。</li>
      <li>各ポジションの表で「名前」を選びます。そのポジションのスタッフが候補の上に並びます（ほかのポジションのスタッフも選べます）。同じ日に同じスタッフは1回しか選べません。</li>
      <li>IN・OUTを選びます。名前を選ぶと、その人の申請IN・申請OUT・備考が表示されるので、見比べながら入力できます。</li>
      <li>行が足りなければ「+ 追加する」を押します。行を空にするには「クリア」を押します。</li>
      <li>「登録する」を押します。ポジションの中は出勤の早い順に並び替えられ、空の行は消えます。</li>
    </ol>
    <ul>
      <li>まだ登録していない日を開くと、ポジションごとに空の行が6行表示されます。登録済みの日は、登録した行だけが表示されます。</li>
      <li>各行の右端の帯は、8時〜23時のうちIN〜OUTの時間を表します。色はポジションの色、管理者は緑です。</li>
    </ul>

    <h3>警告が出たとき</h3>
    <p>申請と違う時間を入れたときや、申請がない日に入れたときは、名前の下に警告が表示されます。確認のための表示なので、そのまま登録できます。</p>

    <h3>希望シフトを反映する</h3>
    <figure th:replace="~{help/parts :: shot('/admin/manual/images/shifts-reflect.png', 'まだ登録していない日の転記画面', false)}"></figure>
    <p>「希望シフトを反映する」を押すと、まだどの行にも入っていないスタッフの申請を、そのスタッフのポジションの空いている行に入れます。入力済みのスタッフはそのままです。必要に応じて直してから「登録する」で保存してください。</p>
    <p class="note">登録しただけでは、スタッフには表示されません。次の「公開」が必要です。</p>
  </section>

  <section id="publish">
    <h2>6. 公開</h2>
    <p>登録した内容は下書きです。公開した日だけがスタッフに表示されます。転記画面の日付の横に「公開済み」か「下書き」が表示されます。</p>
    <ul>
      <li>「この日を公開」：表示している日を公開します。</li>
      <li>期間を選んで「まで公開」：開始日と終了日を選んで、その間の日をまとめて公開します。初期値は、表示している日を含むサイクルの初日〜末日です。</li>
    </ul>
    <p class="note">公開されるのは登録済みの内容です。シフトが登録されていない日は公開されません。</p>

    <h3>公開した後に直すとき</h3>
    <p>公開済みの日を直して「登録する」を押すと、確認のあと、すぐにスタッフに表示されます。時間の変更・追加・取り消しがあったスタッフのトップ画面には「変更あり」が表示されます（本人が「確認済み」を押すまで）。</p>
    <p>メニューの「シフト」（日別シフト一覧）では、管理者には「この日を転記画面で開く」が表示されます。管理者は過去の日も制限なく見られます。</p>
  </section>

  <section id="settings">
    <h2>7. 設定</h2>
    <figure th:replace="~{help/parts :: shot('/admin/manual/images/settings.png', '設定画面', false)}"></figure>
    <p>メニューの「設定」で、申請の締切を「サイクル開始日の何日前にするか」（0〜30日）を決めます。たとえば5日前なら、11日〜20日分の締切は6日です。入力して「保存する」を押します。</p>
  </section>

  <section id="trouble">
    <h2>8. 困ったとき</h2>
    <h3>スタッフがパスワードを忘れた</h3>
    <p>スタッフ管理の「編集」でパスワードをリセットし、仮パスワードを本人に伝えてください。</p>

    <h3>自分（管理者）がパスワードを忘れた</h3>
    <p>ほかの管理者にリセットしてもらってください。管理者が1人だけだとリセットできる人がいなくなるため、管理者は2人以上登録しておくことをおすすめします。</p>

    <h3>スタッフが退職した</h3>
    <p>スタッフ管理で「無効にする」を押してください。</p>

    <h3>スタッフから「シフトが見られない」と言われた</h3>
    <p>転記画面でその日が「下書き」のままになっていないか確認し、公開してください。</p>

    <h3>締切後に申請を変えたいと言われた</h3>
    <p>申請一覧でマスを押して、代わりに直してください。</p>

    <h3>ポジションを削除できない</h3>
    <p>スタッフかシフトで使われているためです。「非表示」にしてください。</p>
  </section>
</main>
</body>
</html>
```

- [ ] **Step 4: CSSをビルドしてテストを通す**

Run: `npm run build`、続けて `./mvnw test -Dtest=HelpTest`
Expected: PASS

- [ ] **Step 5: 画面を目で確認する**

アプリを起動し、管理者でログインしてPC幅で `/admin/help` を開く。目次のリンク、スタッフ向けへのリンクが動くこと、レイアウトが崩れていないことを確認する（画像はリンク切れでよい）。

- [ ] **Step 6: 全テストを流してコミットし、チェックボックスを更新する**

Run: `./mvnw test`
Expected: PASS

```bash
git add src/main/resources/templates/help/admin.html src/test/java/jp/bk/shiftmanager/controller/HelpTest.java
git commit -m "feat: 使い方（管理者向け）のヘルプ画面を追加する

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

この計画ファイルの Task 2 のチェックボックスを `[x]` にしてコミットし、停止して報告する。

---

### Task 3: 画像とPDFの生成（npm run manual）

**Files:**
- Create: `scripts/manual/build.mjs`
- Create: `scripts/manual/demo-data.sql`
- Create（生成物）: `src/main/resources/static/manual/staff.pdf`、`src/main/resources/static/manual/images/*.png`、`src/main/resources/static/admin/manual/admin.pdf`、`src/main/resources/static/admin/manual/images/*.png`
- Modify: `package.json`（`scripts.manual`、`devDependencies.playwright-core`）、`package-lock.json`
- Modify: `src/test/java/jp/bk/shiftmanager/controller/HelpTest.java`
- Modify: `CLAUDE.md`、`README.md`

**Interfaces:**
- Consumes: `/help`・`/admin/help`（Task 1・2）、ログイン画面のフォーム（`input[name="loginId"]`・`input[name="password"]`・「ログイン」ボタン）、ヘッダーのメニューボタン `[data-nav-open]`、`InitialAdminRunner`（ユーザーが0人なら `admin` を作る）、composeのサービス `db`（ユーザー・パスワード `shiftmanager`、ポート5433）
- Produces: Global Constraints の「画像の一覧」のファイルとPDF2つ、`npm run manual`

- [ ] **Step 1: 失敗するテストを書く**

`HelpTest` に追加する。`import java.util.regex.Matcher;`・`import java.util.regex.Pattern;`・`import java.util.ArrayList;`・`import java.util.List;`・`import static org.assertj.core.api.Assertions.assertThat;`・`import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;`（既存）を使う。

```java
    @Test
    void スタッフ向けのPDFはログインした人が取得できる() throws Exception {
        mvc.perform(get("/manual/staff.pdf").with(user(staff)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
        mvc.perform(get("/manual/staff.pdf")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void 管理者向けのPDFと画像は管理者だけが取得できる() throws Exception {
        mvc.perform(get("/admin/manual/admin.pdf").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
        mvc.perform(get("/admin/manual/admin.pdf").with(user(staff))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/manual/images/shifts.png").with(user(staff))).andExpect(status().isForbidden());
    }

    @Test
    void スタッフ向けの使い方の画像がすべて取得できる() throws Exception {
        assertImagesExist("/help", staff, 7);
    }

    @Test
    void 管理者向けの使い方の画像がすべて取得できる() throws Exception {
        assertImagesExist("/admin/help", admin, 9);
    }

    /** ヘルプ画面の img の src をすべて取得し、画像が取得できることを確認する */
    private void assertImagesExist(String page, LoginUser loginUser, int expectedCount) throws Exception {
        String html = mvc.perform(get(page).with(user(loginUser))).andReturn().getResponse().getContentAsString();
        List<String> sources = new ArrayList<>();
        Matcher matcher = Pattern.compile("<img src=\"([^\"]+)\"").matcher(html);
        while (matcher.find()) {
            sources.add(matcher.group(1));
        }
        assertThat(sources).hasSize(expectedCount);
        for (String source : sources) {
            mvc.perform(get(source).with(user(loginUser))).andExpect(status().isOk());
        }
    }
```

Thymeleafは `th:src` を先頭の属性として出力する（`parts.html` で `img` の最初の属性が `th:src` のため `<img src="...` の形になる）。

- [ ] **Step 2: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=HelpTest`
Expected: 追加した4つが FAIL（PDF・画像がなく404）。`管理者向けのPDFと画像は管理者だけが取得できる` のうち403の確認は通るが、最初の200の確認で落ちる

- [ ] **Step 3: playwright-core を入れ、npmスクリプトを足す**

Run: `npm install -D playwright-core`

`package.json` の `scripts` に追加する。

```json
    "manual": "node scripts/manual/build.mjs"
```

- [ ] **Step 4: デモデータのSQLを書く**

`scripts/manual/demo-data.sql`

```sql
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

-- 今日と、次・その次・さらに次のサイクルの開始日
CREATE TEMP TABLE demo AS SELECT (now() AT TIME ZONE 'Asia/Tokyo')::date AS today;
ALTER TABLE demo ADD COLUMN c1 DATE, ADD COLUMN c2 DATE, ADD COLUMN c3 DATE;
UPDATE demo SET c1 = pg_temp.next_cycle(today);
UPDATE demo SET c2 = pg_temp.next_cycle(c1);
UPDATE demo SET c3 = pg_temp.next_cycle(c2);

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

-- 申請：明日〜45日後。4日に1日は申請なし。健は退勤を1時間遅く申請し、転記画面に警告を出す
-- 悠斗は次のサイクルから未提出、美香はその次のサイクルを「出勤できない」にする
INSERT INTO shift_requests (user_id, work_date, start_time, end_time, note)
SELECT w.user_id, demo.today + o,
       CASE WHEN o % 2 = 0 THEN w.s1 ELSE w.s2 END,
       CASE WHEN o % 2 = 0 THEN w.e1 ELSE w.e2 END
           + CASE WHEN w.user_id = 4 THEN INTERVAL '1 hour' ELSE INTERVAL '0' END,
       CASE WHEN w.user_id = 3 AND o % 7 = 3 THEN '学校の行事のため、できれば早めに上がりたいです' END
FROM demo, work w, generate_series(1, 45) AS o
WHERE (o + w.user_id) % 4 <> 0
  AND NOT (w.user_id = 6 AND demo.today + o >= demo.c1)
  AND NOT (w.user_id = 5 AND demo.today + o >= demo.c2 AND demo.today + o < demo.c3);

INSERT INTO cycle_unavailable (user_id, cycle_start) SELECT 5, c2 FROM demo;
```

- [ ] **Step 5: 生成スクリプトを書く**

`scripts/manual/build.mjs`

```js
// 説明書（ヘルプ画面）の画像とPDFを作り直す。npm run manual で実行する
// 前提：Docker Desktop で compose の db が起動していること、Edge がインストールされていること
// 流れ：説明書専用DBを作り直す → アプリを8081で起動 → デモデータを入れる → 撮影 → PDF出力 → アプリを停止
import { execFileSync, execSync, spawn } from 'node:child_process';
import { mkdirSync, openSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { chromium } from 'playwright-core';

const PORT = 8081;
const BASE = `http://localhost:${PORT}`;
const DB = 'shiftmanager_manual';
// デモデータ共通のパスワード（説明書専用DBでのみ使う）
const PASSWORD = 'manual-pass1';
// 画像とPDFの保存先。target 側にも書くのは、起動中のアプリが新しい画像を配信できるようにするため
const STATIC_DIRS = ['src/main/resources/static', 'target/classes/static'];
const PHONE = { viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 };
const PC = { viewport: { width: 1280, height: 800 }, deviceScaleFactor: 1 };
const isWindows = process.platform === 'win32';

// 日付（日本時間、YYYY-MM-DD の文字列で扱う）
const TODAY = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Tokyo' }).format(new Date());

function addDays(text, days) {
  const date = new Date(`${text}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

/** 翌月（YYYY-MM） */
function nextMonth(text) {
  const [year, month] = text.split('-').map(Number);
  return new Date(Date.UTC(year, month, 1)).toISOString().slice(0, 7);
}

/** 指定日より後で最初のサイクル開始日（1日・11日・21日）。demo-data.sql の next_cycle と同じ */
function nextCycleStart(text) {
  const [year, month, day] = text.split('-').map(Number);
  if (day < 11) return `${text.slice(0, 8)}11`;
  if (day < 21) return `${text.slice(0, 8)}21`;
  return `${nextMonth(text)}-01`;
}

/** compose の db コンテナで psql を実行する */
function psql(database, args, input) {
  execFileSync('docker', ['compose', 'exec', '-T', 'db', 'psql', '-U', 'shiftmanager', '-d', database,
    '-v', 'ON_ERROR_STOP=1', ...args], { input, stdio: ['pipe', 'ignore', 'inherit'] });
}

function checkDatabase() {
  try {
    execFileSync('docker', ['compose', 'exec', '-T', 'db', 'pg_isready', '-U', 'shiftmanager'], { stdio: 'ignore' });
  } catch {
    throw new Error('compose の DB が起動していません。docker compose up -d db を実行してください');
  }
}

async function isUp() {
  try {
    return (await fetch(`${BASE}/login`)).ok;
  } catch {
    return false;
  }
}

function startApp() {
  const log = openSync('target/manual-app.log', 'w');
  const env = {
    ...process.env,
    DATABASE_URL: `jdbc:postgresql://localhost:5433/${DB}`,
    DATABASE_USERNAME: 'shiftmanager',
    DATABASE_PASSWORD: 'shiftmanager',
    STAFF_INITIAL_PASSWORD: 'manual-initial1',
  };
  const args = ['spring-boot:run', `-Dspring-boot.run.arguments=--server.port=${PORT}`];
  // Windows の .cmd は shell 経由でないと起動できない。それ以外はプロセスグループごと止められるよう detached にする
  return isWindows
    ? spawn('mvnw.cmd', args, { env, shell: true, stdio: ['ignore', log, log] })
    : spawn('./mvnw', args, { env, detached: true, stdio: ['ignore', log, log] });
}

async function waitForApp(app) {
  const limit = Date.now() + 180_000;
  while (Date.now() < limit) {
    if (app.exitCode !== null) throw new Error('アプリが起動中に終了しました。target/manual-app.log を確認してください');
    if (await isUp()) return;
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  throw new Error('アプリが3分以内に起動しませんでした。target/manual-app.log を確認してください');
}

function stopApp(app) {
  if (app.exitCode !== null) return;
  try {
    if (isWindows) {
      // mvnw から起動した java も含めて止める
      execFileSync('taskkill', ['/pid', String(app.pid), '/T', '/F'], { stdio: 'ignore' });
    } else {
      process.kill(-app.pid, 'SIGTERM');
    }
  } catch {
    // 既に終了している
  }
}

function save(relativePath, data) {
  for (const dir of STATIC_DIRS) {
    const path = join(dir, relativePath);
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, data);
  }
  console.log(`保存しました：${relativePath}`);
}

async function shot(page, relativePath, url, { fullPage = false } = {}) {
  if (url) await page.goto(BASE + url, { waitUntil: 'networkidle' });
  save(relativePath, await page.screenshot({ fullPage }));
}

async function login(browser, loginId, device) {
  const context = await browser.newContext(device);
  const page = await context.newPage();
  await page.goto(`${BASE}/login`);
  await page.fill('input[name="loginId"]', loginId);
  await page.fill('input[name="password"]', PASSWORD);
  await page.click('button:has-text("ログイン")');
  await page.waitForLoadState('networkidle');
  return { context, page };
}

async function staffShots(browser) {
  const guest = await browser.newContext(PHONE);
  await shot(await guest.newPage(), 'manual/images/login.png', '/login');
  await guest.close();

  // 初回ログインの人はパスワード変更画面へ移動する
  const first = await login(browser, 'taro', PHONE);
  await first.page.waitForURL('**/password');
  await shot(first.page, 'manual/images/password.png');
  await first.context.close();

  const { context, page } = await login(browser, 'hanako', PHONE);
  await shot(page, 'manual/images/home.png', '/', { fullPage: true });
  await page.click('[data-nav-open]');
  // メニューが開くアニメーション（200ms）を待つ
  await page.waitForTimeout(500);
  await shot(page, 'manual/images/menu.png');
  await shot(page, 'manual/images/requests.png', `/requests?month=${nextMonth(TODAY)}`);
  await shot(page, 'manual/images/patterns.png', '/mypage/patterns', { fullPage: true });
  await shot(page, 'manual/images/shifts.png', `/shifts?date=${addDays(TODAY, 2)}`);
  await context.close();
}

async function adminShots(browser) {
  const cycleStart = nextCycleStart(TODAY);
  const { context, page } = await login(browser, 'admin', PC);
  await shot(page, 'admin/manual/images/positions.png', '/admin/positions', { fullPage: true });
  await shot(page, 'admin/manual/images/staff-list.png', '/admin/staff', { fullPage: true });
  await shot(page, 'admin/manual/images/staff-new.png', '/admin/staff/new', { fullPage: true });
  // id=3 は山田 花子（demo-data.sql）
  await shot(page, 'admin/manual/images/staff-edit.png', '/admin/staff/3/edit', { fullPage: true });
  await shot(page, 'admin/manual/images/requests.png', `/admin/requests?date=${cycleStart}`);
  await shot(page, 'admin/manual/images/request-edit.png',
    `/admin/requests/edit?userId=3&date=${cycleStart}`, { fullPage: true });
  await shot(page, 'admin/manual/images/shifts.png', `/admin/shifts?date=${addDays(TODAY, 1)}`, { fullPage: true });
  await shot(page, 'admin/manual/images/shifts-reflect.png', `/admin/shifts?date=${addDays(TODAY, 9)}`);
  await shot(page, 'admin/manual/images/settings.png', '/admin/settings');
  await context.close();
}

async function pdf(browser, loginId, url, relativePath) {
  const { context, page } = await login(browser, loginId, PC);
  await page.goto(BASE + url, { waitUntil: 'networkidle' });
  save(relativePath, await page.pdf({
    format: 'A4',
    printBackground: true,
    margin: { top: '15mm', bottom: '15mm', left: '12mm', right: '12mm' },
  }));
  await context.close();
}

async function main() {
  console.log(`今日の日付：${TODAY}`);
  if (await isUp()) throw new Error(`ポート${PORT}は使用中です。起動中のアプリを止めてから実行してください`);
  checkDatabase();

  psql('postgres', ['-c', `DROP DATABASE IF EXISTS ${DB} WITH (FORCE)`]);
  psql('postgres', ['-c', `CREATE DATABASE ${DB}`]);
  execSync('npm run build', { stdio: 'inherit' });
  mkdirSync('target', { recursive: true });

  const app = startApp();
  try {
    await waitForApp(app);
    psql(DB, ['-v', `pw=${PASSWORD}`], readFileSync('scripts/manual/demo-data.sql'));
    const browser = await chromium.launch({ channel: 'msedge' });
    try {
      await staffShots(browser);
      await adminShots(browser);
      await pdf(browser, 'hanako', '/help', 'manual/staff.pdf');
      await pdf(browser, 'admin', '/admin/help', 'admin/manual/admin.pdf');
    } finally {
      await browser.close();
    }
  } finally {
    stopApp(app);
  }
  console.log('完了しました。画像とPDFを確認してからコミットしてください');
}

main().catch((error) => {
  console.error(`失敗しました：${error.message}`);
  process.exitCode = 1;
});
```

- [ ] **Step 6: 実行する**

Docker Desktop を起動し、`docker compose up -d db` を実行してから：

Run: `npm run manual`
Expected: 「保存しました：…」が画像16枚とPDF2つ分表示され、最後に「完了しました」。`http://localhost:8081/login` に接続できない（アプリが止まっている）こと

失敗した場合は `target/manual-app.log` とエラーメッセージを見て直す。`demo-data.sql` のエラーは psql のメッセージに行番号が出る。

- [ ] **Step 7: 画像とPDFを目で確認する**

- 画像16枚：デモデータが入った状態で写っていること（空の画面になっていない）。`home.png` に「変更あり」が2件、`requests.png`（管理者）に未提出の行と「この期間は出勤できない」、`shifts.png`（管理者）に健の警告、`shifts-reflect.png` に「希望シフトを反映する」、`menu.png` にメニューが開いた状態が写っていること
- PDF 2つ：ヘッダー・メニュー・ダウンロードボタンが出ていない、章ごとに改ページされる、画像が途中で切れていない、縦に長い `home.png` が1ページに収まっていること

- [ ] **Step 8: 失敗時にアプリが残らないことを確認する（Review Focus 5）**

1. アプリを8081で起動したまま（`./mvnw spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"`）`npm run manual` を実行し、「ポート8081は使用中です」で止まることを確認して、アプリを止める
2. `docker compose stop db` の状態で実行し、「compose の DB が起動していません」で止まることを確認して、`docker compose up -d db` で戻す
3. `build.mjs` の `adminShots` で、`settings.png` を撮る行の直前に `await page.click('#no-such-element', { timeout: 1000 });` を一時的に足して実行する。エラーで止まったあと、`http://localhost:8081/login` に接続できない（Windowsならタスクマネージャーにjavaのプロセスが残っていない）ことを確認する。確認後、足した行を消す

- [ ] **Step 9: テストを通す**

Run: `./mvnw test -Dtest=HelpTest`
Expected: PASS（画像の枚数：スタッフ用7枚、管理者用9枚）

- [ ] **Step 10: CLAUDE.md と README.md を更新する**

`CLAUDE.md` の「作業の進め方（重要）」の末尾に追加する。

```markdown
- 画面（テンプレート・CSS・画面の動き）を変えたら、`npm run manual` を実行して使い方の画像とPDFを作り直し、一緒にコミットする（Docker Desktop で `docker compose up -d db`、Edge が必要）
```

`README.md` の「ローカルでの起動」の後ろに追加する。

~~~markdown
## 使い方（取扱説明書）の画像とPDF
アプリ内の使い方（`/help`・`/admin/help`）の画像と、そこからダウンロードできるPDFは、次のコマンドで作り直す。画面を変えたら実行してコミットする。

```
docker compose up -d db
npm run manual
```

- 説明書専用のDB `shiftmanager_manual` を作り直し、アプリを8081で起動してデモデータ（`scripts/manual/demo-data.sql`）を入れ、PCのEdgeで撮影とPDF出力を行う。普段の開発DBには触れない
- デモデータの日付は実行した日からの相対で作るため、実行するたびに画像の日付は変わる
- 失敗したときはアプリのログ `target/manual-app.log` を確認する
~~~

- [ ] **Step 11: 全テストを流してコミットし、チェックボックスを更新する**

Run: `./mvnw test`
Expected: PASS

```bash
git add scripts/manual package.json package-lock.json src/main/resources/static/manual src/main/resources/static/admin src/test/java/jp/bk/shiftmanager/controller/HelpTest.java CLAUDE.md README.md
git commit -m "feat: 使い方の画像とPDFを npm run manual で作り直せるようにする

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

この計画ファイルの Task 3 のチェックボックスを `[x]` にしてコミットし、停止して報告する。
