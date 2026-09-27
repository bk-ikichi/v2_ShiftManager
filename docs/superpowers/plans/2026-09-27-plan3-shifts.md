# Plan 3 転記・公開 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **セッション運用：** 1セッション1 Task。Taskの最後のステップ（コミットとチェックボックス更新）が終わったら停止してユーザーに報告する。次のTaskは `/clear` 後の新しいセッションで行う。新しいセッションではこの冒頭（File Structureまで）と、未完了の最初のTaskの範囲だけを読む（`CLAUDE.md` 参照）。

**Goal:** 管理者が本部で作成された確定シフトを1日単位で転記し（ポジション別・名前候補・申請との差分警告）、日ごと・期間ごとに公開でき、公開済みの日の変更を「変更あり」として記録できるようにする。

**Architecture:** Plan 1・2 と同じく controller → service → repository → mapper の一方向。転記画面は1日分のフォームを一括送信し、サーバー側で空欄行を捨てて全行を検証してから、その日のシフトを削除→登録し直す。並び順は保存せず、表示時にポジションの表示順 → INの早い順で並べる。公開状態は `published_dates` の有無で決まり、公開済みの日を登録したときだけ登録前後の差分を `shift_changes` に記録する。名前を選んだときの申請表示・警告・行の追加・二重選択の防止は素のJavaScript（`shifts.js`）で行う。

**Tech Stack:** Java 17、Spring Boot 4.1.1、MyBatis（アノテーションSQL）、Spring Security 7、Thymeleaf 3.1、Tailwind CSS 4、PostgreSQL 17、JUnit 5 + MockMvc + Testcontainers 2、素のJavaScript（転記画面のみ）

**Spec:** `documents/2026-09-25-shift-manager-v2-spec.md`（「確定シフトの転記（管理者）」）、DB設計：`documents/2026-09-25-db-design.md`（shifts・published_dates・shift_changes）。テーブルは Plan 1 の `V1__init.sql` で作成済みのため、マイグレーションは追加しない

## Global Constraints

- 画面の文言・コードのコメントはすべて日本語
- パッケージは層ごと。controllerはserviceのみ、serviceはrepositoryのみを呼ぶ（serviceから別のserviceを呼ばない）。`util` はどの層からも使ってよい
- 「今日」は必ず `Clock` Bean から得る（`LocalDate.now(clock)`）。引数なしの `LocalDate.now()` を使わない
- 時刻は 8:00〜23:00、30分刻み、IN < OUT。日またぎなし。画面の値は `HH:mm`（例 `08:00`）。検証は `util.TimeRange.parse` を使う
- 転記画面の入力はすべてプルダウン。未入力の日はポジションごとに空欄行を6行、登録済みの日は登録済みの行のみ表示する
- 名前の候補：そのポジションを初期ポジションとするスタッフが先頭、その下に他のスタッフ（ポジションの表示順 → 名前の順）。無効なスタッフは候補に出さない（その日に登録済みの場合を除く）
- 同一日に同一スタッフは1回だけ（画面とサーバーの両方で検証。DBにも一意制約あり）
- 申請との差分の警告は「申請がない」または「IN・OUTが申請の時間帯からはみ出している」ときだけ出す。警告があっても登録できる
- シフトが1件も登録されていない日は公開しない（「この日を公開」はエラー、期間の公開では飛ばして知らせる）
- 期間の公開の選択肢は、表示中の日を含むサイクルとその前後1サイクル。初期値は表示中の日を含むサイクルの初日〜末日。一度に公開できるのは31日分まで
- 公開の取り消し機能はない（`published_dates` は追加のみ）
- 「変更あり」は公開済みの日の登録でだけ記録する。初回公開は変更扱いにしない。種別は DB設計の規則どおり（前なし・後あり→ADDED、前あり・後なし→CANCELLED、IN・OUT・ポジションのどれかが違う→UPDATED、差分なし→記録しない）。未確認の変更は1人1日1件で、新しい種別に置き換える
- Thymeleafから `T(...)` で static メソッドを呼ばない。表示用の文字列（日付ラベル等）はDTOで作る。DTOは record ではなく Lombok の `@Data` クラスにする（既存DTOと同じ）
- 管理者の転記画面はPC前提（スマートフォンで横スクロールになってもよい）
- コミットメッセージの末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` を付ける

## Review Focus

1. 同じスタッフを別々の行（別ポジションを含む）で2回選んで「登録する」：何も保存されずエラーになり、入力した行は画面に残る → Task 2でテスト
2. 行の一部だけ入力（名前だけ・時刻だけ）、選択肢にない時刻（`07:30`・`23:30`）、OUT ≦ IN、数値でないID・存在しないID・不正な日付：500エラーにならず、どのポジション・誰の行か分かる入力エラー → Task 2でテスト
3. 下書きの状態で開いた画面を、別のタブで公開した後に「登録する」：確認なしにスタッフへ反映されないよう保存せずエラーにし、入力は残す → Task 4でテスト
4. 公開済みの日を何度も登録し直す（未確認の変更がある状態での再変更、確認済みの変更がある状態での変更、ポジションだけの変更、変更なしの登録）：未確認の変更は1人1日1件で最新の種別になり、確認済みの記録は消えない → Task 4でテスト
5. 期間の公開で開始日 > 終了日・31日超・不正な日付・シフト未登録の日だけの期間：500エラーにならず何も公開しない。シフト未登録の日が混ざる期間は、その日を飛ばして知らせる → Task 3でテスト

## File Structure

```
src/main/java/jp/bk/shiftmanager/
  entity/      Shift, ShiftChange, ShiftChangeType
  mapper/      ShiftMapper, PublishedDateMapper, ShiftChangeMapper       （UserMapperに findAll を追加）
  repository/  ShiftRepository, PublishedDateRepository, ShiftChangeRepository（UserRepositoryに findAll を追加）
  service/     ShiftService（転記の表示・登録・変更の記録）, PublishService（公開）
  controller/  ShiftAdminController（/admin/shifts）
  form/        ShiftDayForm, ShiftRowForm
  dto/         ShiftDayView, ShiftGroupView, ShiftRowView, ShiftCandidate, ShiftRequestInfo, DateOption
  util/        ShiftWarnings（申請との差分の警告）, ShiftChanges（変更の種別の判定）
src/main/resources/
  templates/layout.html                     ヘッダーに「転記」を追加
  templates/admin/shifts/day.html           転記画面
  templates/admin/shifts/row.html           転記画面の1行（フラグメント。「+ 追加する」の雛形にも使う）
  static/js/shifts.js                       行の追加・クリア、申請表示と警告、二重選択の防止、未保存・公開済みの確認
src/test/java/jp/bk/shiftmanager/
  TestData.java                             shift・publish・hide・acknowledgeChanges を追加
  util/ShiftWarningsTest, ShiftChangesTest
  controller/ShiftDayTest, ShiftSaveTest, ShiftPublishTest, ShiftChangeTest
```

---

### Task 1: 転記画面の表示（名前候補・申請IN/OUT・警告・行の追加）

**Files:**
- Create: `entity/Shift.java`、`mapper/ShiftMapper.java`、`mapper/PublishedDateMapper.java`、`repository/ShiftRepository.java`、`repository/PublishedDateRepository.java`
- Create: `util/ShiftWarnings.java`、`form/ShiftRowForm.java`
- Create: `dto/ShiftDayView.java`、`dto/ShiftGroupView.java`、`dto/ShiftRowView.java`、`dto/ShiftCandidate.java`、`dto/ShiftRequestInfo.java`
- Create: `service/ShiftService.java`、`controller/ShiftAdminController.java`
- Create: `src/main/resources/templates/admin/shifts/day.html`、`admin/shifts/row.html`、`src/main/resources/static/js/shifts.js`
- Modify: `mapper/UserMapper.java`・`repository/UserRepository.java`（`findAll`）、`src/main/resources/templates/layout.html`（ヘッダーに「転記」）
- Modify: `src/test/java/jp/bk/shiftmanager/TestData.java`（`shift`・`publish`・`hide`）
- Test: `util/ShiftWarningsTest.java`、`controller/ShiftDayTest.java`

**Interfaces:**
- Consumes: `util.TimeRange(LocalTime start, LocalTime end)`、`TimeSlots.parse(String): LocalTime`・`TimeSlots.format(LocalTime): String`・`TimeSlots.OPTIONS`、`DateLabels.monthDayWeek(LocalDate)`、`ShiftRequestRepository#findByPeriod(LocalDate, LocalDate): List<ShiftRequest>`（Plan 2）、`PositionRepository#findAll(): List<Position>`（表示順）、`exception.BusinessException`
- Produces:
  - `entity.Shift`（`id, workDate, userId, positionId, startTime, endTime`）
  - `ShiftMapper#findByDate(LocalDate): List<Shift>`（INの早い順）、`ShiftRepository#findByDate(LocalDate)`
  - `PublishedDateMapper#exists(LocalDate): boolean`、`PublishedDateRepository#isPublished(LocalDate): boolean`
  - `UserMapper#findAll(): List<User>`（名前の順）、`UserRepository#findAll()`
  - `util.ShiftWarnings.of(LocalTime start, LocalTime end, TimeRange request): String`（`NO_REQUEST`・`OUT_OF_REQUEST`・null）
  - `form.ShiftRowForm`（`String positionId, userId, startTime, endTime`）
  - `dto.ShiftDayView`（`date, dateLabel, previousDate, previousLabel, nextDate, nextLabel, published, groups, requests, nextIndex`）、`ShiftGroupView`（`positionId, positionName, primaryCandidates, otherCandidates, rows`）、`ShiftRowView`（`index, userId, startTime, endTime, requestStart, requestEnd, requestNote, warning`）、`ShiftCandidate`（`userId, name`）、`ShiftRequestInfo`（`userId, start, end, note`）
  - `service.ShiftService#resolveDate(String): LocalDate`、`#getDay(LocalDate): ShiftDayView`、非公開の `buildView(LocalDate, List<Shift>, Map<Long, List<ShiftRowForm>>, boolean)`・`parseId(String): Long`・`isBlank(String)`（Task 2で使う）
  - 画面：`GET /admin/shifts?date=yyyy-MM-dd`（テンプレート `admin/shifts/day`、モデル `view`・`timeOptions`）
  - `TestData#shift(User, Position, LocalDate, String start, String end)`、`#publish(LocalDate)`、`#hide(Position)`

- [x] **Step 1: 警告の判定の失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/util/ShiftWarningsTest.java`：

```java
package jp.bk.shiftmanager.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class ShiftWarningsTest {

    private static final TimeRange REQUEST = new TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0));

    @Test
    void 申請がなければ警告する() {
        assertThat(ShiftWarnings.of(time("09:00"), time("17:00"), null)).isEqualTo("申請がありません");
        assertThat(ShiftWarnings.of(null, null, null)).isEqualTo("申請がありません");
    }

    @Test
    void 申請の時間帯に収まっていれば警告しない() {
        assertThat(ShiftWarnings.of(time("09:00"), time("17:00"), REQUEST)).isNull();
        assertThat(ShiftWarnings.of(time("10:00"), time("15:00"), REQUEST)).isNull();
    }

    @Test
    void 申請の時間帯からはみ出していれば警告する() {
        assertThat(ShiftWarnings.of(time("08:30"), time("17:00"), REQUEST)).isEqualTo("申請の時間外です");
        assertThat(ShiftWarnings.of(time("09:00"), time("17:30"), REQUEST)).isEqualTo("申請の時間外です");
    }

    @Test
    void 時刻が未選択の側は判定しない() {
        assertThat(ShiftWarnings.of(null, null, REQUEST)).isNull();
        assertThat(ShiftWarnings.of(time("08:00"), null, REQUEST)).isEqualTo("申請の時間外です");
        assertThat(ShiftWarnings.of(null, time("16:00"), REQUEST)).isNull();
    }

    private static LocalTime time(String text) {
        return LocalTime.parse(text);
    }
}
```

- [x] **Step 2: 転記画面の失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/TestData.java` に次のメソッドを追加する（既存のメソッドは変更しない）：

```java
    /** 確定シフトを登録する（下書き） */
    public void shift(User user, Position position, LocalDate date, String start, String end) {
        jdbc.update("INSERT INTO shifts (work_date, user_id, position_id, start_time, end_time) "
                + "VALUES (?, ?, ?, ?::time, ?::time)", date, user.getId(), position.getId(), start, end);
    }

    /** その日を公開済みにする */
    public void publish(LocalDate date) {
        jdbc.update("INSERT INTO published_dates (work_date) VALUES (?)", date);
    }

    /** ポジションを非表示にする */
    public void hide(Position position) {
        jdbc.update("UPDATE positions SET hidden = TRUE WHERE id = ?", position.getId());
    }
```

`src/test/java/jp/bk/shiftmanager/controller/ShiftDayTest.java`：

```java
package jp.bk.shiftmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.dto.ShiftCandidate;
import jp.bk.shiftmanager.dto.ShiftDayView;
import jp.bk.shiftmanager.dto.ShiftGroupView;
import jp.bk.shiftmanager.dto.ShiftRowView;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.User;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 転記画面の表示（今日は2026-09-25（金）） */
class ShiftDayTest extends IntegrationTestBase {

    private static final LocalDate OCT2 = LocalDate.of(2026, 10, 2);

    LoginUser admin;
    Position kitchen;
    Position counter;
    User taro;
    User hanako;
    User jiro;

    @BeforeEach
    void setUp() {
        admin = data.login(data.user("boss", "店長", true));
        // 作成順と表示順を逆にして、表示順で並ぶことを確かめる
        counter = data.position("カウンター", 2);
        kitchen = data.position("キッチン", 1);
        taro = data.user("taro", "山田太郎", false);
        data.assignPosition(taro, kitchen);
        hanako = data.user("hanako", "佐藤花子", false);
        data.assignPosition(hanako, counter);
        jiro = data.user("jiro", "鈴木次郎", false);
    }

    @Test
    void 未登録の日はポジションの表示順にグループ分けし空欄行を6行ずつ表示する() throws Exception {
        ShiftDayView view = view("2026-10-02");

        assertThat(view.getDateLabel()).isEqualTo("10/2（金）");
        assertThat(view.isPublished()).isFalse();
        assertThat(view.getGroups()).extracting(ShiftGroupView::getPositionName)
                .containsExactly("キッチン", "カウンター");
        assertThat(view.getGroups().get(0).getRows()).hasSize(6).allSatisfy(row -> {
            assertThat(row.getUserId()).isNull();
            // 名前を選んでいない行は申請欄を空にし、警告も出さない
            assertThat(row.getRequestStart()).isEmpty();
            assertThat(row.getWarning()).isNull();
        });
        // 添字は画面全体で連番
        assertThat(view.getGroups().get(1).getRows()).extracting(ShiftRowView::getIndex)
                .containsExactly(6, 7, 8, 9, 10, 11);
        assertThat(view.getNextIndex()).isEqualTo(12);
    }

    @Test
    void 登録済みの日は登録済みの行だけをポジション内のINの早い順に表示する() throws Exception {
        data.shift(taro, kitchen, OCT2, "10:00", "15:00");
        data.shift(hanako, counter, OCT2, "12:00", "20:00");
        data.shift(jiro, kitchen, OCT2, "08:00", "12:00");

        ShiftDayView view = view("2026-10-02");

        assertThat(view.getGroups().get(0).getRows()).extracting(ShiftRowView::getUserId)
                .containsExactly(jiro.getId().toString(), taro.getId().toString());
        assertThat(view.getGroups().get(0).getRows().get(0).getStartTime()).isEqualTo("08:00");
        assertThat(view.getGroups().get(0).getRows().get(0).getEndTime()).isEqualTo("12:00");
        assertThat(view.getGroups().get(1).getRows()).extracting(ShiftRowView::getUserId)
                .containsExactly(hanako.getId().toString());
        assertThat(view.getNextIndex()).isEqualTo(3);
    }

    @Test
    void 非表示のポジションはシフトがある日だけ表示する() throws Exception {
        Position old = data.position("旧ポジション", 3);
        data.hide(old);
        assertThat(view("2026-10-02").getGroups()).extracting(ShiftGroupView::getPositionName)
                .containsExactly("キッチン", "カウンター");

        data.shift(taro, old, OCT2, "09:00", "17:00");
        ShiftDayView view = view("2026-10-02");

        assertThat(view.getGroups()).extracting(ShiftGroupView::getPositionName)
                .containsExactly("キッチン", "カウンター", "旧ポジション");
        // 登録済みの日は空欄行を付けない（追加は「+ 追加する」）
        assertThat(view.getGroups().get(0).getRows()).isEmpty();
        assertThat(view.getGroups().get(2).getRows()).hasSize(1);
    }

    @Test
    void 名前の候補はそのポジションのスタッフが先で無効なスタッフは出さない() throws Exception {
        User saburo = data.user("saburo", "高橋三郎", false);
        data.assignPosition(saburo, kitchen);
        data.disable(saburo);

        ShiftGroupView kitchenGroup = view("2026-10-02").getGroups().get(0);

        assertThat(kitchenGroup.getPrimaryCandidates()).extracting(ShiftCandidate::getName)
                .containsExactly("山田太郎");
        assertThat(kitchenGroup.getOtherCandidates()).extracting(ShiftCandidate::getName)
                .containsExactlyInAnyOrder("佐藤花子", "店長", "鈴木次郎");
        // その他はポジションの表示順（未設定は最後）
        assertThat(kitchenGroup.getOtherCandidates().get(0).getName()).isEqualTo("佐藤花子");
    }

    @Test
    void 無効なスタッフでもその日に登録済みなら候補に残る() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.disable(taro);

        assertThat(view("2026-10-02").getGroups().get(0).getPrimaryCandidates())
                .extracting(ShiftCandidate::getName).containsExactly("山田太郎");
        assertThat(view("2026-10-03").getGroups().get(0).getPrimaryCandidates()).isEmpty();
    }

    @Test
    void 名前を選んだ行に申請のIN_OUT_備考と警告を表示する() throws Exception {
        data.request(taro, OCT2, "09:00", "17:00", "早めに上がりたい");
        data.request(hanako, OCT2, "12:00", "20:00", null);
        data.shift(taro, kitchen, OCT2, "08:00", "17:00");
        data.shift(jiro, kitchen, OCT2, "10:00", "14:00");
        data.shift(hanako, counter, OCT2, "12:00", "18:00");

        ShiftDayView view = view("2026-10-02");

        ShiftRowView taroRow = view.getGroups().get(0).getRows().get(0);
        assertThat(taroRow.getRequestStart()).isEqualTo("09:00");
        assertThat(taroRow.getRequestEnd()).isEqualTo("17:00");
        assertThat(taroRow.getRequestNote()).isEqualTo("早めに上がりたい");
        assertThat(taroRow.getWarning()).isEqualTo("申請の時間外です");

        ShiftRowView jiroRow = view.getGroups().get(0).getRows().get(1);
        assertThat(jiroRow.getRequestStart()).isEqualTo("--:--");
        assertThat(jiroRow.getRequestEnd()).isEqualTo("--:--");
        assertThat(jiroRow.getWarning()).isEqualTo("申請がありません");

        ShiftRowView hanakoRow = view.getGroups().get(1).getRows().get(0);
        assertThat(hanakoRow.getRequestNote()).isNull();
        assertThat(hanakoRow.getWarning()).isNull();
    }

    @Test
    void 名前を選んだときの表示用にその日の申請を画面に埋め込む() throws Exception {
        data.request(taro, OCT2, "09:00", "17:00", "メモ");

        mvc.perform(get("/admin/shifts").param("date", "2026-10-02").with(user(admin)))
                .andExpect(content().string(Matchers.containsString("data-user-id=\"" + taro.getId() + "\"")))
                .andExpect(content().string(Matchers.containsString("data-note=\"メモ\"")))
                .andExpect(content().string(Matchers.containsString("+ 追加する")))
                .andExpect(content().string(Matchers.containsString("下書き")));
    }

    @Test
    void 前後の日へ移動でき不正な日付は今日を表示する() throws Exception {
        ShiftDayView view = view("2026-10-01");
        assertThat(view.getPreviousDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(view.getPreviousLabel()).isEqualTo("9/30（水）");
        assertThat(view.getNextDate()).isEqualTo(OCT2);
        assertThat(view.getNextLabel()).isEqualTo("10/2（金）");

        assertThat(view("abc").getDate()).isEqualTo(LocalDate.of(2026, 9, 25));
    }

    @Test
    void 公開済みの日は公開済みと表示する() throws Exception {
        data.publish(OCT2);

        assertThat(view("2026-10-02").isPublished()).isTrue();
        mvc.perform(get("/admin/shifts").param("date", "2026-10-02").with(user(admin)))
                .andExpect(content().string(Matchers.containsString("公開済み")));
    }

    @Test
    void ポジションがないときは登録を案内する() throws Exception {
        data.reset();
        LoginUser boss = data.login(data.user("boss", "店長", true));

        mvc.perform(get("/admin/shifts").with(user(boss)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("ポジションが登録されていません")));
    }

    @Test
    void 一般スタッフは転記画面を開けない() throws Exception {
        mvc.perform(get("/admin/shifts").with(user(data.login(taro))))
                .andExpect(status().isForbidden());
    }

    private ShiftDayView view(String date) throws Exception {
        MvcResult result = mvc.perform(get("/admin/shifts").param("date", date).with(user(admin)))
                .andExpect(status().isOk())
                .andReturn();
        return (ShiftDayView) result.getModelAndView().getModel().get("view");
    }
}
```

- [x] **Step 3: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=ShiftWarningsTest,ShiftDayTest`
Expected: FAIL（コンパイルエラー：`ShiftWarnings`・`ShiftDayView` 等が存在しない）

- [x] **Step 4: エンティティ・Mapper・Repository・警告の判定を実装する**

`entity/Shift.java`：

```java
package jp.bk.shiftmanager.entity;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;

/** 確定シフト（1人1日1件）。どのポジション枠に入れたかを持つ */
@Data
public class Shift {
    private Long id;
    private LocalDate workDate;
    private Long userId;
    private Long positionId;
    private LocalTime startTime;
    private LocalTime endTime;
}
```

`mapper/ShiftMapper.java`：

```java
package jp.bk.shiftmanager.mapper;

import java.time.LocalDate;
import java.util.List;
import jp.bk.shiftmanager.entity.Shift;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 確定シフト */
@Mapper
public interface ShiftMapper {

    /** その日のシフト（INの早い順） */
    @Select("SELECT * FROM shifts WHERE work_date = #{date} ORDER BY start_time, end_time, id")
    List<Shift> findByDate(@Param("date") LocalDate date);
}
```

`mapper/PublishedDateMapper.java`：

```java
package jp.bk.shiftmanager.mapper;

import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 公開済みの日付 */
@Mapper
public interface PublishedDateMapper {

    @Select("SELECT EXISTS (SELECT 1 FROM published_dates WHERE work_date = #{date})")
    boolean exists(@Param("date") LocalDate date);
}
```

`repository/ShiftRepository.java`：

```java
package jp.bk.shiftmanager.repository;

import java.time.LocalDate;
import java.util.List;
import jp.bk.shiftmanager.entity.Shift;
import jp.bk.shiftmanager.mapper.ShiftMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ShiftRepository {

    private final ShiftMapper shiftMapper;

    /** その日のシフト（INの早い順） */
    public List<Shift> findByDate(LocalDate date) {
        return shiftMapper.findByDate(date);
    }
}
```

`repository/PublishedDateRepository.java`：

```java
package jp.bk.shiftmanager.repository;

import java.time.LocalDate;
import jp.bk.shiftmanager.mapper.PublishedDateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PublishedDateRepository {

    private final PublishedDateMapper publishedDateMapper;

    public boolean isPublished(LocalDate date) {
        return publishedDateMapper.exists(date);
    }
}
```

`mapper/UserMapper.java` に追加：

```java
    /** 全スタッフ（無効を含む。名前の順） */
    @Select("SELECT * FROM users ORDER BY name, id")
    List<User> findAll();
```

`repository/UserRepository.java` に追加：

```java
    /** 全スタッフ（無効を含む。名前の順） */
    public List<User> findAll() {
        return userMapper.findAll();
    }
```

`util/ShiftWarnings.java`：

```java
package jp.bk.shiftmanager.util;

import java.time.LocalTime;

/** 確定シフトの時刻と申請の差分の警告（警告があっても登録はできる） */
public final class ShiftWarnings {

    public static final String NO_REQUEST = "申請がありません";
    public static final String OUT_OF_REQUEST = "申請の時間外です";

    private ShiftWarnings() {
    }

    /**
     * 申請がなければ NO_REQUEST、IN・OUTが申請の時間帯からはみ出していれば OUT_OF_REQUEST、それ以外はnull。
     * 時刻が未選択（null）の側は判定しない。画面の shifts.js も同じ判定をする
     */
    public static String of(LocalTime start, LocalTime end, TimeRange request) {
        if (request == null) {
            return NO_REQUEST;
        }
        if (start != null && start.isBefore(request.start())) {
            return OUT_OF_REQUEST;
        }
        if (end != null && end.isAfter(request.end())) {
            return OUT_OF_REQUEST;
        }
        return null;
    }
}
```

- [x] **Step 5: フォーム・DTOを実装する**

`form/ShiftRowForm.java`：

```java
package jp.bk.shiftmanager.form;

import lombok.Data;

/** 転記画面の1行の入力。IDも文字列で受け取り、検証はServiceで行う */
@Data
public class ShiftRowForm {
    private String positionId;
    /** 空なら名前未選択 */
    private String userId;
    /** HH:mm */
    private String startTime;
    private String endTime;
}
```

`dto/ShiftCandidate.java`：

```java
package jp.bk.shiftmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 転記画面の名前の候補 */
@Data
@AllArgsConstructor
public class ShiftCandidate {
    /** 画面の選択値と比べるため文字列にする */
    private String userId;
    private String name;
}
```

`dto/ShiftRequestInfo.java`：

```java
package jp.bk.shiftmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 転記画面に埋め込むその日の申請（名前を選んだときの表示に使う） */
@Data
@AllArgsConstructor
public class ShiftRequestInfo {
    private String userId;
    /** HH:mm */
    private String start;
    private String end;
    /** 備考（なければnull） */
    private String note;
}
```

`dto/ShiftRowView.java`：

```java
package jp.bk.shiftmanager.dto;

import lombok.Data;

/** 転記画面の1行 */
@Data
public class ShiftRowView {
    /** フォームの添字（rows[index]）。画面全体で連番 */
    private int index;
    /** 名前未選択ならnullまたは空文字 */
    private String userId;
    /** HH:mm */
    private String startTime;
    private String endTime;
    /** 申請IN・OUT。名前未選択なら空文字、申請がなければ --:-- */
    private String requestStart;
    private String requestEnd;
    /** 申請の備考（なければnull） */
    private String requestNote;
    /** 申請との差分の警告（なければnull） */
    private String warning;
}
```

`dto/ShiftGroupView.java`：

```java
package jp.bk.shiftmanager.dto;

import java.util.List;
import lombok.Data;

/** 転記画面のポジション1つ分 */
@Data
public class ShiftGroupView {
    private long positionId;
    private String positionName;
    /** そのポジションを初期ポジションとするスタッフ（名前の順） */
    private List<ShiftCandidate> primaryCandidates;
    /** その他のスタッフ（ポジションの表示順、未設定は最後 → 名前の順） */
    private List<ShiftCandidate> otherCandidates;
    private List<ShiftRowView> rows;
}
```

`dto/ShiftDayView.java`：

```java
package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/** 転記画面の1日分 */
@Data
public class ShiftDayView {
    private LocalDate date;
    /** 例：10/2（金） */
    private String dateLabel;
    private LocalDate previousDate;
    private String previousLabel;
    private LocalDate nextDate;
    private String nextLabel;
    private boolean published;
    /** ポジションの表示順 */
    private List<ShiftGroupView> groups;
    /** その日の申請（名前を選んだときの表示に使う） */
    private List<ShiftRequestInfo> requests;
    /** 「+ 追加する」で追加する行の最初の添字 */
    private int nextIndex;
}
```

- [x] **Step 6: Service・コントローラーを実装する**

`service/ShiftService.java`：

```java
package jp.bk.shiftmanager.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jp.bk.shiftmanager.dto.ShiftCandidate;
import jp.bk.shiftmanager.dto.ShiftDayView;
import jp.bk.shiftmanager.dto.ShiftGroupView;
import jp.bk.shiftmanager.dto.ShiftRequestInfo;
import jp.bk.shiftmanager.dto.ShiftRowView;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.Shift;
import jp.bk.shiftmanager.entity.ShiftRequest;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.ShiftRowForm;
import jp.bk.shiftmanager.repository.PositionRepository;
import jp.bk.shiftmanager.repository.PublishedDateRepository;
import jp.bk.shiftmanager.repository.ShiftRepository;
import jp.bk.shiftmanager.repository.ShiftRequestRepository;
import jp.bk.shiftmanager.repository.UserRepository;
import jp.bk.shiftmanager.util.DateLabels;
import jp.bk.shiftmanager.util.ShiftWarnings;
import jp.bk.shiftmanager.util.TimeRange;
import jp.bk.shiftmanager.util.TimeSlots;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 確定シフトの転記（管理者） */
@Service
@RequiredArgsConstructor
public class ShiftService {

    /** 未入力の日に表示する、ポジションごとの空欄行の数 */
    private static final int BLANK_ROWS = 6;
    /** 申請がない場合の申請IN・OUTの表示 */
    private static final String NO_REQUEST_TIME = "--:--";

    private final Clock clock;
    private final ShiftRepository shiftRepository;
    private final PublishedDateRepository publishedDateRepository;
    private final PositionRepository positionRepository;
    private final UserRepository userRepository;
    private final ShiftRequestRepository shiftRequestRepository;

    /** 画面から指定された日。指定がない・不正なら今日 */
    public LocalDate resolveDate(String text) {
        if (text != null) {
            try {
                return LocalDate.parse(text);
            } catch (DateTimeParseException e) {
                // 今日を表示する
            }
        }
        return LocalDate.now(clock);
    }

    /** 転記画面の表示内容。未入力の日はポジションごとに空欄行を付ける */
    public ShiftDayView getDay(LocalDate date) {
        List<Shift> shifts = shiftRepository.findByDate(date);
        Map<Long, List<ShiftRowForm>> rows = new HashMap<>();
        for (Shift shift : shifts) {
            rows.computeIfAbsent(shift.getPositionId(), id -> new ArrayList<>()).add(toRowForm(shift));
        }
        return buildView(date, shifts, rows, shifts.isEmpty());
    }

    /**
     * 画面の内容を組み立てる。
     * shifts は登録済みのシフト（無効なスタッフを候補に残すかの判定に使う）、rowsByPosition はポジションごとの表示する行
     */
    private ShiftDayView buildView(LocalDate date, List<Shift> shifts, Map<Long, List<ShiftRowForm>> rowsByPosition,
            boolean addBlankRows) {
        List<Position> positions = positionRepository.findAll();
        List<User> candidates = candidates(positions, shifts);
        Map<String, ShiftRequest> requests = shiftRequestRepository.findByPeriod(date, date).stream()
                .collect(Collectors.toMap(request -> request.getUserId().toString(), request -> request));

        int index = 0;
        List<ShiftGroupView> groups = new ArrayList<>();
        for (Position position : positions) {
            List<ShiftRowForm> inputs = rowsByPosition.getOrDefault(position.getId(), List.of());
            // 非表示のポジションは、その日に使われている場合だけ表示する
            if (position.isHidden() && inputs.isEmpty()) {
                continue;
            }
            List<ShiftRowView> rows = new ArrayList<>();
            for (ShiftRowForm input : inputs) {
                rows.add(toRowView(index++, input, requests));
            }
            if (addBlankRows) {
                for (int i = 0; i < BLANK_ROWS; i++) {
                    rows.add(toRowView(index++, new ShiftRowForm(), requests));
                }
            }
            ShiftGroupView group = new ShiftGroupView();
            group.setPositionId(position.getId());
            group.setPositionName(position.getName());
            group.setPrimaryCandidates(candidates.stream()
                    .filter(user -> position.getId().equals(user.getPositionId()))
                    .map(this::toCandidate).toList());
            group.setOtherCandidates(candidates.stream()
                    .filter(user -> !position.getId().equals(user.getPositionId()))
                    .map(this::toCandidate).toList());
            group.setRows(rows);
            groups.add(group);
        }

        ShiftDayView view = new ShiftDayView();
        view.setDate(date);
        view.setDateLabel(DateLabels.monthDayWeek(date));
        view.setPreviousDate(date.minusDays(1));
        view.setPreviousLabel(DateLabels.monthDayWeek(date.minusDays(1)));
        view.setNextDate(date.plusDays(1));
        view.setNextLabel(DateLabels.monthDayWeek(date.plusDays(1)));
        view.setPublished(publishedDateRepository.isPublished(date));
        view.setGroups(groups);
        view.setRequests(requests.values().stream().map(this::toRequestInfo).toList());
        view.setNextIndex(index);
        return view;
    }

    /**
     * 名前の候補：有効なスタッフと、その日に登録済みのスタッフ（無効化されていても残す）。
     * 初期ポジションの表示順（未設定は最後）→ 名前の順
     */
    private List<User> candidates(List<Position> positions, List<Shift> shifts) {
        Map<Long, Integer> order = new HashMap<>();
        for (int i = 0; i < positions.size(); i++) {
            order.put(positions.get(i).getId(), i);
        }
        Set<Long> assigned = shifts.stream().map(Shift::getUserId).collect(Collectors.toSet());
        // findAll は名前の順のため、安定ソートでポジションの表示順に並べ替える
        return userRepository.findAll().stream()
                .filter(user -> user.isEnabled() || assigned.contains(user.getId()))
                .sorted(Comparator.comparingInt(
                        (User user) -> order.getOrDefault(user.getPositionId(), Integer.MAX_VALUE)))
                .toList();
    }

    private ShiftRowView toRowView(int index, ShiftRowForm input, Map<String, ShiftRequest> requests) {
        ShiftRowView row = new ShiftRowView();
        row.setIndex(index);
        row.setUserId(input.getUserId());
        row.setStartTime(input.getStartTime());
        row.setEndTime(input.getEndTime());
        if (isBlank(input.getUserId())) {
            row.setRequestStart("");
            row.setRequestEnd("");
            return row;
        }
        ShiftRequest request = requests.get(input.getUserId().strip());
        row.setRequestStart(request == null ? NO_REQUEST_TIME : TimeSlots.format(request.getStartTime()));
        row.setRequestEnd(request == null ? NO_REQUEST_TIME : TimeSlots.format(request.getEndTime()));
        row.setRequestNote(request == null ? null : request.getNote());
        TimeRange requested = request == null ? null : new TimeRange(request.getStartTime(), request.getEndTime());
        row.setWarning(ShiftWarnings.of(parseTimeOrNull(input.getStartTime()), parseTimeOrNull(input.getEndTime()),
                requested));
        return row;
    }

    /** 警告の判定用。不正な時刻は未選択として扱う（登録時に入力エラーになる） */
    private LocalTime parseTimeOrNull(String text) {
        try {
            return TimeSlots.parse(text);
        } catch (BusinessException e) {
            return null;
        }
    }

    private ShiftRowForm toRowForm(Shift shift) {
        ShiftRowForm row = new ShiftRowForm();
        row.setPositionId(shift.getPositionId().toString());
        row.setUserId(shift.getUserId().toString());
        row.setStartTime(TimeSlots.format(shift.getStartTime()));
        row.setEndTime(TimeSlots.format(shift.getEndTime()));
        return row;
    }

    private ShiftCandidate toCandidate(User user) {
        return new ShiftCandidate(user.getId().toString(), user.getName());
    }

    private ShiftRequestInfo toRequestInfo(ShiftRequest request) {
        return new ShiftRequestInfo(request.getUserId().toString(), TimeSlots.format(request.getStartTime()),
                TimeSlots.format(request.getEndTime()), request.getNote());
    }

    /** 数値でなければnull */
    private Long parseId(String text) {
        if (isBlank(text)) {
            return null;
        }
        try {
            return Long.valueOf(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
```

`parseId` は Task 2 で使う。この時点で未使用の警告が出る場合はそのままでよい。

`controller/ShiftAdminController.java`：

```java
package jp.bk.shiftmanager.controller;

import jp.bk.shiftmanager.service.ShiftService;
import jp.bk.shiftmanager.util.TimeSlots;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 確定シフトの転記・公開（管理者） */
@Controller
@RequestMapping("/admin/shifts")
@RequiredArgsConstructor
public class ShiftAdminController {

    private static final String VIEW = "admin/shifts/day";

    private final ShiftService shiftService;

    @GetMapping
    public String show(@RequestParam(required = false) String date, Model model) {
        model.addAttribute("view", shiftService.getDay(shiftService.resolveDate(date)));
        model.addAttribute("timeOptions", TimeSlots.OPTIONS);
        return VIEW;
    }
}
```

- [x] **Step 7: テンプレートとJavaScriptを作成し、ヘッダーに「転記」を追加する**

`src/main/resources/templates/admin/shifts/row.html`（1行のフラグメント。雛形では `index` が `ROW_INDEX`、`row` が null）：

```html
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org">
<body>
<table>
  <tr th:fragment="row(g, index, row)" data-row class="border-t border-stone-100 align-top">
    <td class="p-1">
      <input type="hidden" th:name="|rows[${index}].positionId|" th:value="${g.positionId}">
      <select th:name="|rows[${index}].userId|" data-user class="input mt-0 min-w-36 px-1" aria-label="名前">
        <option value="">--</option>
        <optgroup th:if="${!#lists.isEmpty(g.primaryCandidates)}" th:label="${g.positionName}">
          <option th:each="c : ${g.primaryCandidates}" th:value="${c.userId}" th:text="${c.name}"
                  th:selected="${row != null and c.userId == row.userId}">山田太郎</option>
        </optgroup>
        <optgroup th:if="${!#lists.isEmpty(g.otherCandidates)}" label="その他">
          <option th:each="c : ${g.otherCandidates}" th:value="${c.userId}" th:text="${c.name}"
                  th:selected="${row != null and c.userId == row.userId}">佐藤花子</option>
        </optgroup>
      </select>
    </td>
    <td class="p-1">
      <select th:name="|rows[${index}].startTime|" data-in class="input mt-0 px-1" aria-label="IN">
        <option value="">--:--</option>
        <option th:each="t : ${timeOptions}" th:value="${t}" th:text="${t}"
                th:selected="${row != null and t == row.startTime}"></option>
      </select>
    </td>
    <td class="p-1">
      <select th:name="|rows[${index}].endTime|" data-out class="input mt-0 px-1" aria-label="OUT">
        <option value="">--:--</option>
        <option th:each="t : ${timeOptions}" th:value="${t}" th:text="${t}"
                th:selected="${row != null and t == row.endTime}"></option>
      </select>
    </td>
    <td class="px-2 py-3 text-center text-stone-600" data-request-start th:text="${row?.requestStart}">08:00</td>
    <td class="px-2 py-3 text-center text-stone-600" data-request-end th:text="${row?.requestEnd}">18:00</td>
    <td class="px-2 py-3">
      <button type="button" data-note-button class="font-bold text-amber-700" aria-label="申請の備考を表示"
              th:hidden="${row == null or row.requestNote == null}">※</button>
      <p data-note class="text-xs text-stone-700" hidden th:text="${row?.requestNote}"></p>
      <p data-warning class="text-xs text-red-700" th:text="${row?.warning}"></p>
    </td>
    <td class="px-2 py-3 text-right">
      <button type="button" data-clear class="text-xs text-stone-500 underline">クリア</button>
    </td>
  </tr>
</table>
</body>
</html>
```

`src/main/resources/templates/admin/shifts/day.html`：

```html
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout :: head('シフトの転記')}"></head>
<body class="min-h-screen bg-stone-50 text-stone-900">
<header th:replace="~{layout :: header}"></header>
<main class="mx-auto max-w-5xl px-4 pb-28 pt-6">
  <div class="mb-4 flex flex-wrap items-center gap-2">
    <a th:href="@{/admin/shifts(date=${view.previousDate})}" data-leave-link class="btn-secondary px-3 py-1">
      &lt; <span th:text="${view.previousLabel}">10/1（木）</span></a>
    <h1 class="px-2 text-lg font-bold" th:text="${view.dateLabel}">10/2（金）</h1>
    <a th:href="@{/admin/shifts(date=${view.nextDate})}" data-leave-link class="btn-secondary px-3 py-1">
      <span th:text="${view.nextLabel}">10/3（土）</span> &gt;</a>
    <span th:if="${view.published}" class="rounded bg-green-100 px-2 py-1 text-sm text-green-800">公開済み</span>
    <span th:unless="${view.published}" class="rounded bg-stone-200 px-2 py-1 text-sm text-stone-700">下書き</span>
  </div>
  <div th:replace="~{layout :: flash}"></div>
  <p th:if="${#lists.isEmpty(view.groups)}" class="card text-sm">
    ポジションが登録されていません。先に <a th:href="@{/admin/positions}" class="underline">ポジション</a> を登録してください。
  </p>

  <form id="shift-form" th:action="@{/admin/shifts}" method="post" th:data-next-index="${view.nextIndex}">
    <input type="hidden" name="date" th:value="${view.date}">
    <section th:each="g : ${view.groups}" class="mb-6" data-group>
      <h2 class="mb-2 font-bold" th:text="${g.positionName}">キッチン</h2>
      <div class="overflow-x-auto rounded-lg border border-stone-200 bg-white">
        <table class="min-w-full border-collapse text-sm">
          <thead>
          <tr class="bg-stone-100 text-left">
            <th class="px-2 py-2">名前</th>
            <th class="px-2 py-2">IN</th>
            <th class="px-2 py-2">OUT</th>
            <th class="px-2 py-2 text-center">申請IN</th>
            <th class="px-2 py-2 text-center">申請OUT</th>
            <th class="px-2 py-2"></th>
            <th class="px-2 py-2"></th>
          </tr>
          </thead>
          <tbody data-rows>
          <tr th:each="row : ${g.rows}" th:replace="~{admin/shifts/row :: row(${g}, ${row.index}, ${row})}"></tr>
          </tbody>
        </table>
      </div>
      <template data-row-template>
        <tr th:replace="~{admin/shifts/row :: row(${g}, 'ROW_INDEX', null)}"></tr>
      </template>
      <button type="button" data-add-row class="mt-2 text-sm text-amber-800 underline">+ 追加する</button>
    </section>

    <div th:if="${!#lists.isEmpty(view.groups)}" class="fixed inset-x-0 bottom-0 border-t border-stone-200 bg-white p-3">
      <div class="mx-auto flex max-w-5xl items-center justify-end gap-4">
        <button class="btn-primary px-8">登録する</button>
      </div>
    </div>
  </form>

  <ul id="request-data" hidden>
    <li th:each="r : ${view.requests}" th:data-user-id="${r.userId}" th:data-start="${r.start}"
        th:data-end="${r.end}" th:data-note="${r.note}"></li>
  </ul>
</main>
<script th:src="@{/js/shifts.js}" defer></script>
</body>
</html>
```

`src/main/resources/static/js/shifts.js`：

```js
// 転記画面：行の追加・クリア、名前を選んだときの申請表示と警告、同じスタッフの二重選択の防止、未保存の確認
(() => {
  const form = document.getElementById('shift-form');
  if (!form) {
    return;
  }

  // その日の申請（userId → {start, end, note}）
  const requests = new Map();
  document.querySelectorAll('#request-data li').forEach((li) => {
    requests.set(li.dataset.userId, { start: li.dataset.start, end: li.dataset.end, note: li.dataset.note || '' });
  });

  let dirty = false;
  form.addEventListener('change', () => { dirty = true; });
  form.addEventListener('submit', () => { dirty = false; });

  // ShiftWarnings と同じ判定（時刻は HH:mm のため文字列のまま比較できる）
  const warningOf = (request, start, end) => {
    if (!request) {
      return '申請がありません';
    }
    if ((start && start < request.start) || (end && end > request.end)) {
      return '申請の時間外です';
    }
    return '';
  };

  // 名前に応じて申請IN・OUT・備考・警告を表示し直す
  const refreshRow = (row) => {
    const userId = row.querySelector('select[data-user]').value;
    const request = requests.get(userId);
    const noteButton = row.querySelector('[data-note-button]');
    const note = row.querySelector('[data-note]');
    const warning = row.querySelector('[data-warning]');
    if (!userId) {
      row.querySelector('[data-request-start]').textContent = '';
      row.querySelector('[data-request-end]').textContent = '';
      noteButton.hidden = true;
      note.hidden = true;
      warning.textContent = '';
      return;
    }
    row.querySelector('[data-request-start]').textContent = request ? request.start : '--:--';
    row.querySelector('[data-request-end]').textContent = request ? request.end : '--:--';
    note.textContent = request ? request.note : '';
    noteButton.hidden = !(request && request.note);
    if (noteButton.hidden) {
      note.hidden = true;
    }
    warning.textContent = warningOf(request, row.querySelector('select[data-in]').value,
      row.querySelector('select[data-out]').value);
  };

  // 他の行で選ばれているスタッフは選べないようにする（サーバー側でも検証する）
  const refreshDuplicates = () => {
    const selects = [...form.querySelectorAll('select[data-user]')];
    const chosen = selects.map((select) => select.value).filter((value) => value);
    selects.forEach((select) => {
      [...select.options].forEach((option) => {
        option.disabled = option.value !== '' && option.value !== select.value && chosen.includes(option.value);
      });
    });
  };

  const setUpRow = (row) => {
    row.querySelectorAll('select').forEach((select) => {
      select.addEventListener('change', () => {
        refreshRow(row);
        refreshDuplicates();
      });
    });
    row.querySelector('[data-note-button]').addEventListener('click', () => {
      const note = row.querySelector('[data-note]');
      note.hidden = !note.hidden;
    });
    // 名前・IN・OUTを空にする（空の行は登録時に削除される）
    row.querySelector('[data-clear]').addEventListener('click', () => {
      row.querySelectorAll('select').forEach((select) => { select.value = ''; });
      dirty = true;
      refreshRow(row);
      refreshDuplicates();
    });
  };

  form.querySelectorAll('[data-row]').forEach(setUpRow);
  refreshDuplicates();

  // 「+ 追加する」：雛形の ROW_INDEX を未使用の添字に置き換えて行を追加する
  let nextIndex = Number(form.dataset.nextIndex);
  form.querySelectorAll('[data-group]').forEach((group) => {
    group.querySelector('[data-add-row]').addEventListener('click', () => {
      const html = group.querySelector('template[data-row-template]').innerHTML
        .replaceAll('ROW_INDEX', String(nextIndex));
      nextIndex += 1;
      const tbody = group.querySelector('[data-rows]');
      tbody.insertAdjacentHTML('beforeend', html);
      const row = tbody.lastElementChild;
      setUpRow(row);
      refreshRow(row);
      refreshDuplicates();
    });
  });

  // 未保存の入力がある状態で別の日へ移動するときは確認する
  document.querySelectorAll('a[data-leave-link]').forEach((link) => {
    link.addEventListener('click', (event) => {
      if (dirty && !window.confirm('保存していない入力があります。移動しますか？')) {
        event.preventDefault();
      }
    });
  });
})();
```

`src/main/resources/templates/layout.html` のヘッダーで、`申請一覧` のリンクの直前に追加する：

```html
      <a th:href="@{/admin/shifts}" class="hover:underline">転記</a>
```

- [x] **Step 8: テストが通ることを確認する**

Run: `./mvnw test -Dtest=ShiftWarningsTest,ShiftDayTest`
Expected: PASS

- [x] **Step 9: ブラウザで動作を確認する**

`npm run build` の後、`./mvnw spring-boot:run` で起動し、管理者でログインしてポジション・スタッフ・申請を用意してから `/admin/shifts?date=（申請のある日）` を開き、次を確認する（確認できない場合はユーザーに報告して確認を依頼する）：
- 名前を選ぶと申請IN・OUTが表示され、申請がない人は `--:--` と「申請がありません」が出る
- IN・OUTを申請の時間帯からはみ出すように選ぶと「申請の時間外です」が出て、収まるように戻すと消える
- 備考のある申請は「※」が出て、押すと備考が表示される
- 「+ 追加する」でそのポジションに行が増え、増えた行でも上の3つが動く
- ある行で選んだスタッフは、他の行の名前の候補で選べなくなる。「クリア」で戻る
- 名前を変更してから前後の日へのリンクを押すと確認が出る
（「登録する」はTask 2で実装するため、この時点では押さない）

- [x] **Step 10: 全テストを実行してコミットする**

Run: `./mvnw test`
Expected: PASS

```bash
git add -A
git commit -m "feat: 転記画面の表示（名前候補・申請との差分警告・行の追加）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [x] **Step 11: この計画ファイルのTask 1のチェックボックスをすべて `[x]` にしてコミットし、停止してユーザーに報告する**

```bash
git add docs/superpowers/plans/2026-09-27-plan3-shifts.md
git commit -m "docs: Plan 3 Task 1 完了

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: 転記の登録（空欄行の削除・二重登録の防止・入力エラー時の再表示）

**Files:**
- Create: `form/ShiftDayForm.java`
- Modify: `mapper/ShiftMapper.java`・`repository/ShiftRepository.java`（`insert`・`deleteByDate`）
- Modify: `service/ShiftService.java`（`saveDay`・`getDay(LocalDate, ShiftDayForm)` を追加）、`controller/ShiftAdminController.java`（`POST /admin/shifts`）
- Test: `controller/ShiftSaveTest.java`

**Interfaces:**
- Consumes: Task 1 の `ShiftService`（非公開の `buildView`・`parseId`・`isBlank`）・`ShiftRowForm`・`ShiftDayView`、`TestData#shift`、`UserRepository#findAll`、`PositionRepository#findAll`、`util.TimeRange.parse(String, String)`
- Produces:
  - `form.ShiftDayForm`（`String date, List<ShiftRowForm> rows`）
  - `ShiftMapper#insert(Shift)`・`#deleteByDate(LocalDate)`、`ShiftRepository#insert(Shift)`・`#deleteByDate(LocalDate)`
  - `service.ShiftService#saveDay(ShiftDayForm)`（`@Transactional`）、`#getDay(LocalDate, ShiftDayForm): ShiftDayView`（入力をそのまま表示し直す）、非公開の `parseRows(LocalDate, ShiftDayForm, List<Shift> before): List<Shift>`（Task 4で使う）
  - 画面：`POST /admin/shifts`（成功：`/admin/shifts?date=` へリダイレクトし `message`「登録しました」、入力エラー：リダイレクトせず `admin/shifts/day` を表示し直し `error` にメッセージ）

- [ ] **Step 1: 失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/controller/ShiftSaveTest.java`：

```java
package jp.bk.shiftmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.dto.ShiftDayView;
import jp.bk.shiftmanager.dto.ShiftRowView;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.Shift;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.ShiftMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 転記の登録（今日は2026-09-25） */
class ShiftSaveTest extends IntegrationTestBase {

    private static final LocalDate OCT2 = LocalDate.of(2026, 10, 2);

    @Autowired
    ShiftMapper shiftMapper;

    LoginUser admin;
    Position kitchen;
    Position counter;
    User taro;
    User hanako;
    User jiro;

    @BeforeEach
    void setUp() {
        admin = data.login(data.user("boss", "店長", true));
        kitchen = data.position("キッチン", 1);
        counter = data.position("カウンター", 2);
        taro = data.user("taro", "山田太郎", false);
        data.assignPosition(taro, kitchen);
        hanako = data.user("hanako", "佐藤花子", false);
        data.assignPosition(hanako, counter);
        jiro = data.user("jiro", "鈴木次郎", false);
    }

    @Test
    void 登録すると空欄行を除いて保存し表示はポジション内のINの早い順になる() throws Exception {
        MockHttpServletRequestBuilder request = save("2026-10-02");
        row(request, 0, kitchen, taro, "12:00", "20:00");
        row(request, 1, kitchen, null, "", "");
        row(request, 2, kitchen, jiro, "08:00", "12:00");
        row(request, 3, counter, hanako, "10:00", "15:00");
        row(request, 4, counter, null, "", "");

        mvc.perform(request)
                .andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"))
                .andExpect(flash().attribute("message", "登録しました"));

        List<Shift> saved = shiftMapper.findByDate(OCT2);
        assertThat(saved).hasSize(3);
        Shift hanakoShift = saved.stream().filter(s -> s.getUserId().equals(hanako.getId())).findFirst().orElseThrow();
        assertThat(hanakoShift.getPositionId()).isEqualTo(counter.getId());
        assertThat(hanakoShift.getStartTime()).isEqualTo(LocalTime.of(10, 0));

        ShiftDayView view = dayView("2026-10-02");
        assertThat(view.getGroups().get(0).getRows()).extracting(ShiftRowView::getUserId)
                .containsExactly(jiro.getId().toString(), taro.getId().toString());
        assertThat(view.getGroups().get(1).getRows()).extracting(ShiftRowView::getUserId)
                .containsExactly(hanako.getId().toString());
    }

    @Test
    void 添字に欠番があっても登録できる() throws Exception {
        // 「+ 追加する」で追加した行は添字が飛ぶ
        MockHttpServletRequestBuilder request = save("2026-10-02");
        row(request, 0, kitchen, taro, "09:00", "17:00");
        row(request, 13, counter, hanako, "10:00", "15:00");

        mvc.perform(request).andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"));

        assertThat(shiftMapper.findByDate(OCT2)).hasSize(2);
    }

    @Test
    void 登録し直すと空にした行は削除される() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.shift(jiro, kitchen, OCT2, "12:00", "18:00");

        MockHttpServletRequestBuilder request = save("2026-10-02");
        row(request, 0, kitchen, taro, "10:00", "17:00");
        row(request, 1, kitchen, null, "", "");
        mvc.perform(request).andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"));

        List<Shift> saved = shiftMapper.findByDate(OCT2);
        assertThat(saved).extracting(Shift::getUserId).containsExactly(taro.getId());
        assertThat(saved.get(0).getStartTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void すべての行を空にして登録するとその日のシフトはなくなる() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");

        MockHttpServletRequestBuilder request = save("2026-10-02");
        row(request, 0, kitchen, null, "", "");
        mvc.perform(request).andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"));

        assertThat(shiftMapper.findByDate(OCT2)).isEmpty();
    }

    @Test
    void 同じスタッフを2回選ぶと保存されず入力が残る() throws Exception {
        data.shift(hanako, counter, OCT2, "10:00", "15:00");

        MockHttpServletRequestBuilder request = save("2026-10-02");
        row(request, 0, kitchen, taro, "08:00", "12:00");
        row(request, 1, counter, taro, "13:00", "18:00");
        row(request, 2, counter, hanako, "11:00", "15:00");
        MvcResult result = mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(view().name("admin/shifts/day"))
                .andExpect(model().attribute("error", "山田太郎が2回選ばれています。1日に1回だけ選択してください"))
                .andReturn();

        // 何も保存されない
        List<Shift> saved = shiftMapper.findByDate(OCT2);
        assertThat(saved).extracting(Shift::getUserId).containsExactly(hanako.getId());
        assertThat(saved.get(0).getStartTime()).isEqualTo(LocalTime.of(10, 0));

        // 入力した行はそのまま残る（空欄行は付け足さない）
        ShiftDayView view = (ShiftDayView) result.getModelAndView().getModel().get("view");
        assertThat(view.getGroups().get(0).getRows()).hasSize(1);
        assertThat(view.getGroups().get(0).getRows().get(0).getUserId()).isEqualTo(taro.getId().toString());
        assertThat(view.getGroups().get(0).getRows().get(0).getStartTime()).isEqualTo("08:00");
        assertThat(view.getGroups().get(1).getRows()).extracting(ShiftRowView::getStartTime)
                .containsExactly("13:00", "11:00");
    }

    @Test
    void 行の一部だけの入力や不正な時刻は保存されずどの行か分かるエラーになる() throws Exception {
        Object[][] cases = {
                {null, "09:00", "17:00", "キッチン：名前を選択してください"},
                {taro, "", "", "キッチン・山田太郎：INとOUTを選択してください"},
                {taro, "09:00", "", "キッチン・山田太郎：INとOUTを選択してください"},
                {taro, "07:30", "12:00", "キッチン・山田太郎：時刻は8:00〜23:00の30分刻みで選択してください"},
                {taro, "12:00", "23:30", "キッチン・山田太郎：時刻は8:00〜23:00の30分刻みで選択してください"},
                {taro, "abc", "12:00", "キッチン・山田太郎：時刻は8:00〜23:00の30分刻みで選択してください"},
                {taro, "13:00", "12:00", "キッチン・山田太郎：OUTはINより後の時刻にしてください"},
                {taro, "12:00", "12:00", "キッチン・山田太郎：OUTはINより後の時刻にしてください"},
        };
        for (Object[] c : cases) {
            MockHttpServletRequestBuilder request = save("2026-10-02");
            row(request, 0, kitchen, (User) c[0], (String) c[1], (String) c[2]);
            mvc.perform(request)
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("error", c[3]));
        }
        assertThat(shiftMapper.findByDate(OCT2)).isEmpty();
    }

    @Test
    void 不正なIDや日付は入力エラーになる() throws Exception {
        mvc.perform(save("2026-10-02")
                        .param("rows[0].positionId", "abc").param("rows[0].userId", taro.getId().toString())
                        .param("rows[0].startTime", "09:00").param("rows[0].endTime", "17:00"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "不正なポジションです"));

        for (String userId : new String[] {"99999", "abc"}) {
            mvc.perform(save("2026-10-02")
                            .param("rows[0].positionId", kitchen.getId().toString()).param("rows[0].userId", userId)
                            .param("rows[0].startTime", "09:00").param("rows[0].endTime", "17:00"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("error", "スタッフが見つかりません"));
        }

        MockHttpServletRequestBuilder invalidDate = save("2026-13-01");
        row(invalidDate, 0, kitchen, taro, "09:00", "17:00");
        mvc.perform(invalidDate)
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "不正な日付です"));

        assertThat(shiftMapper.findByDate(OCT2)).isEmpty();
    }

    @Test
    void 無効なスタッフは新しく選べないがその日に登録済みなら登録し直せる() throws Exception {
        User saburo = data.user("saburo", "高橋三郎", false);
        data.disable(saburo);

        MockHttpServletRequestBuilder request = save("2026-10-02");
        row(request, 0, kitchen, saburo, "09:00", "17:00");
        mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "キッチン・高橋三郎：無効なスタッフは選択できません"));
        assertThat(shiftMapper.findByDate(OCT2)).isEmpty();

        data.shift(saburo, kitchen, OCT2, "09:00", "17:00");
        MockHttpServletRequestBuilder again = save("2026-10-02");
        row(again, 0, kitchen, saburo, "10:00", "17:00");
        mvc.perform(again).andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"));
        assertThat(shiftMapper.findByDate(OCT2).get(0).getStartTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void 一般スタッフは登録できない() throws Exception {
        MockHttpServletRequestBuilder request = post("/admin/shifts").with(user(data.login(taro))).with(csrf())
                .param("date", "2026-10-02");
        row(request, 0, kitchen, taro, "09:00", "17:00");

        mvc.perform(request).andExpect(status().isForbidden());

        assertThat(shiftMapper.findByDate(OCT2)).isEmpty();
    }

    private MockHttpServletRequestBuilder save(String date) {
        return post("/admin/shifts").with(user(admin)).with(csrf()).param("date", date);
    }

    /** 1行分の入力を付ける（user が null なら名前未選択） */
    private void row(MockHttpServletRequestBuilder request, int index, Position position, User user, String start,
            String end) {
        String prefix = "rows[" + index + "].";
        request.param(prefix + "positionId", position.getId().toString())
                .param(prefix + "userId", user == null ? "" : user.getId().toString())
                .param(prefix + "startTime", start)
                .param(prefix + "endTime", end);
    }

    private ShiftDayView dayView(String date) throws Exception {
        MvcResult result = mvc.perform(get("/admin/shifts").param("date", date).with(user(admin)))
                .andExpect(status().isOk())
                .andReturn();
        return (ShiftDayView) result.getModelAndView().getModel().get("view");
    }
}
```

- [ ] **Step 2: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=ShiftSaveTest`
Expected: FAIL（`POST /admin/shifts` がないため、リダイレクト・モデルの検証が失敗する）

- [ ] **Step 3: フォーム・Mapper・Repositoryを実装する**

`form/ShiftDayForm.java`：

```java
package jp.bk.shiftmanager.form;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 転記画面の1日分の入力。検証はServiceで行う */
@Data
public class ShiftDayForm {
    /** yyyy-MM-dd */
    private String date;
    /** 添字の欠番（「+ 追加する」の行など）には空の行またはnullが入る */
    private List<ShiftRowForm> rows = new ArrayList<>();
}
```

`mapper/ShiftMapper.java` に追加（import に `org.apache.ibatis.annotations.Delete`・`Insert` を追加）：

```java
    @Insert("""
            INSERT INTO shifts (work_date, user_id, position_id, start_time, end_time)
            VALUES (#{workDate}, #{userId}, #{positionId}, #{startTime}, #{endTime})
            """)
    void insert(Shift shift);

    @Delete("DELETE FROM shifts WHERE work_date = #{date}")
    int deleteByDate(@Param("date") LocalDate date);
```

`repository/ShiftRepository.java` に追加：

```java
    public void insert(Shift shift) {
        shiftMapper.insert(shift);
    }

    public void deleteByDate(LocalDate date) {
        shiftMapper.deleteByDate(date);
    }
```

- [ ] **Step 4: Serviceに登録と再表示を追加する**

`service/ShiftService.java` の import に `java.util.HashSet`、`jp.bk.shiftmanager.form.ShiftDayForm`、`org.springframework.transaction.annotation.Transactional` を追加し、定数とメソッドを追加する：

```java
    private static final String USER_NOT_FOUND = "スタッフが見つかりません";
```

```java
    /** 登録できなかった入力をそのまま表示し直す（空欄行も送信されたまま残し、付け足さない） */
    public ShiftDayView getDay(LocalDate date, ShiftDayForm input) {
        Map<Long, List<ShiftRowForm>> rows = new HashMap<>();
        for (ShiftRowForm row : input.getRows()) {
            Long positionId = row == null ? null : parseId(row.getPositionId());
            if (positionId != null) {
                rows.computeIfAbsent(positionId, id -> new ArrayList<>()).add(row);
            }
        }
        return buildView(date, shiftRepository.findByDate(date), rows, false);
    }

    /**
     * 1日分のシフトを登録し直す。空欄の行は捨て、1行でも不正があれば何も保存しない。
     * 並び順は保存せず、表示時にポジションの表示順 → INの早い順に並べる
     */
    @Transactional
    public void saveDay(ShiftDayForm form) {
        LocalDate date = parseDate(form.getDate());
        List<Shift> before = shiftRepository.findByDate(date);
        List<Shift> after = parseRows(date, form, before);
        shiftRepository.deleteByDate(date);
        after.forEach(shiftRepository::insert);
    }

    /** 全行を検証してシフトにする。エラーはどのポジション・誰の行か分かるメッセージにする */
    private List<Shift> parseRows(LocalDate date, ShiftDayForm form, List<Shift> before) {
        Map<Long, Position> positions = positionRepository.findAll().stream()
                .collect(Collectors.toMap(Position::getId, position -> position));
        Map<Long, User> users = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        Set<Long> assigned = before.stream().map(Shift::getUserId).collect(Collectors.toSet());
        Set<Long> chosen = new HashSet<>();

        List<Shift> shifts = new ArrayList<>();
        for (ShiftRowForm row : form.getRows()) {
            if (row == null || isBlankRow(row)) {
                continue;
            }
            Position position = positions.get(parseId(row.getPositionId()));
            if (position == null) {
                throw new BusinessException("不正なポジションです");
            }
            if (isBlank(row.getUserId())) {
                throw new BusinessException(position.getName() + "：名前を選択してください");
            }
            User user = users.get(parseId(row.getUserId()));
            if (user == null) {
                throw new BusinessException(USER_NOT_FOUND);
            }
            String label = position.getName() + "・" + user.getName();
            // 無効化したスタッフは、その日に登録済みの場合だけ残せる
            if (!user.isEnabled() && !assigned.contains(user.getId())) {
                throw new BusinessException(label + "：無効なスタッフは選択できません");
            }
            if (!chosen.add(user.getId())) {
                throw new BusinessException(user.getName() + "が2回選ばれています。1日に1回だけ選択してください");
            }
            TimeRange range;
            try {
                range = TimeRange.parse(row.getStartTime(), row.getEndTime());
            } catch (BusinessException e) {
                throw new BusinessException(label + "：" + e.getMessage());
            }
            Shift shift = new Shift();
            shift.setWorkDate(date);
            shift.setUserId(user.getId());
            shift.setPositionId(position.getId());
            shift.setStartTime(range.start());
            shift.setEndTime(range.end());
            shifts.add(shift);
        }
        return shifts;
    }

    /** 名前・IN・OUTがすべて空の行（ポジションは雛形に常に入っているため見ない） */
    private boolean isBlankRow(ShiftRowForm row) {
        return isBlank(row.getUserId()) && isBlank(row.getStartTime()) && isBlank(row.getEndTime());
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text == null ? "" : text);
        } catch (DateTimeParseException e) {
            throw new BusinessException("不正な日付です");
        }
    }
```

- [ ] **Step 5: コントローラーに登録を追加する**

`controller/ShiftAdminController.java` の import に `jp.bk.shiftmanager.exception.BusinessException`、`jp.bk.shiftmanager.form.ShiftDayForm`、`org.springframework.web.bind.annotation.ModelAttribute`、`org.springframework.web.bind.annotation.PostMapping`、`org.springframework.web.servlet.mvc.support.RedirectAttributes` を追加し、次を追加する：

```java
    private static final String REDIRECT_DAY = "redirect:/admin/shifts";
```

```java
    @PostMapping
    public String save(@ModelAttribute ShiftDayForm form, Model model, RedirectAttributes redirectAttributes) {
        try {
            shiftService.saveDay(form);
        } catch (BusinessException e) {
            // 1日分の入力を消さないよう、リダイレクトせずに表示し直す
            model.addAttribute("view", shiftService.getDay(shiftService.resolveDate(form.getDate()), form));
            model.addAttribute("error", e.getMessage());
            model.addAttribute("timeOptions", TimeSlots.OPTIONS);
            return VIEW;
        }
        redirectAttributes.addFlashAttribute("message", "登録しました");
        redirectAttributes.addAttribute("date", form.getDate());
        return REDIRECT_DAY;
    }
```

- [ ] **Step 6: テストが通ることを確認する**

Run: `./mvnw test -Dtest=ShiftSaveTest,ShiftDayTest`
Expected: PASS

- [ ] **Step 7: ブラウザで動作を確認する**

`npm run build` の後、`./mvnw spring-boot:run` で起動し、管理者で `/admin/shifts` を開いて次を確認する（確認できない場合はユーザーに報告して確認を依頼する）：
- 行をばらばらのINで入力して「登録する」と、ポジション内がINの早い順に並び、空欄行が消える
- 登録済みの日を開き直すと登録済みの行だけが表示され、「+ 追加する」で行を足して登録できる
- 名前だけ選んで「登録する」とエラーが出て、入力した内容が消えていない。再表示後も「+ 追加する」・申請表示が動く

- [ ] **Step 8: 全テストを実行してコミットする**

Run: `./mvnw test`
Expected: PASS

```bash
git add -A
git commit -m "feat: 転記の登録（空欄行の削除・二重登録の防止）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 9: この計画ファイルのTask 2のチェックボックスをすべて `[x]` にしてコミットし、停止してユーザーに報告する**

```bash
git add docs/superpowers/plans/2026-09-27-plan3-shifts.md
git commit -m "docs: Plan 3 Task 2 完了

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: 公開（この日を公開・期間を指定して公開）

**Files:**
- Create: `dto/DateOption.java`、`service/PublishService.java`
- Modify: `mapper/ShiftMapper.java`・`repository/ShiftRepository.java`（`findDates`）、`mapper/PublishedDateMapper.java`・`repository/PublishedDateRepository.java`（`insert`・`publish`）
- Modify: `dto/ShiftDayView.java`（期間の選択肢）、`service/ShiftService.java`（`buildView` で期間の選択肢を設定）、`controller/ShiftAdminController.java`（公開2つ）
- Modify: `src/main/resources/templates/admin/shifts/day.html`（公開フォーム）、`src/main/resources/static/js/shifts.js`（未保存のまま公開するときの確認）
- Test: `controller/ShiftPublishTest.java`

**Interfaces:**
- Consumes: Task 1・2 の `ShiftService`・`ShiftDayView`・`ShiftAdminController`・`TestData#shift`・`#publish`、`util.Cycle`（`of`・`previous`・`next`・`start`・`end`）、`DateLabels.monthDay`・`monthDayWeek`
- Produces:
  - `ShiftMapper#findDates(LocalDate from, LocalDate to): List<LocalDate>`（シフトがある日）、`ShiftRepository#findDates(...)`
  - `PublishedDateMapper#insert(LocalDate)`（既に公開済みなら何もしない）、`PublishedDateRepository#publish(LocalDate)`
  - `dto.DateOption`（`LocalDate value, String label`）、`ShiftDayView` に `rangeOptions, rangeStart, rangeEnd`
  - `service.PublishService#publishDay(String date)`、`#publishRange(String from, String to): List<LocalDate>`（公開しなかった日）
  - 画面：`POST /admin/shifts/publish`（`date`）、`POST /admin/shifts/publish-range`（`date`・`from`・`to`）。どちらも `/admin/shifts?date=（表示中の日）` へリダイレクト

- [ ] **Step 1: 失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/controller/ShiftPublishTest.java`：

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

import java.time.LocalDate;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.dto.DateOption;
import jp.bk.shiftmanager.dto.ShiftDayView;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.PublishedDateMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 公開（今日は2026-09-25） */
class ShiftPublishTest extends IntegrationTestBase {

    private static final LocalDate OCT1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT2 = LocalDate.of(2026, 10, 2);
    private static final LocalDate OCT3 = LocalDate.of(2026, 10, 3);
    private static final LocalDate OCT4 = LocalDate.of(2026, 10, 4);
    private static final LocalDate OCT5 = LocalDate.of(2026, 10, 5);

    @Autowired
    PublishedDateMapper publishedDateMapper;

    LoginUser admin;
    Position kitchen;
    User taro;

    @BeforeEach
    void setUp() {
        admin = data.login(data.user("boss", "店長", true));
        kitchen = data.position("キッチン", 1);
        taro = data.user("taro", "山田太郎", false);
    }

    @Test
    void この日を公開すると公開済みになる() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");

        mvc.perform(post("/admin/shifts/publish").with(user(admin)).with(csrf()).param("date", "2026-10-02"))
                .andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"))
                .andExpect(flash().attribute("message", "公開しました"));

        assertThat(publishedDateMapper.exists(OCT2)).isTrue();
        assertThat(publishedDateMapper.exists(OCT1)).isFalse();
    }

    @Test
    void シフトが登録されていない日は公開できない() throws Exception {
        mvc.perform(post("/admin/shifts/publish").with(user(admin)).with(csrf()).param("date", "2026-10-02"))
                .andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"))
                .andExpect(flash().attribute("error", "シフトが登録されていないため公開できません"));

        assertThat(publishedDateMapper.exists(OCT2)).isFalse();
    }

    @Test
    void 公開済みの日をもう一度公開してもエラーにならない() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.publish(OCT2);

        mvc.perform(post("/admin/shifts/publish").with(user(admin)).with(csrf()).param("date", "2026-10-02"))
                .andExpect(flash().attribute("message", "公開しました"));

        assertThat(publishedDateMapper.exists(OCT2)).isTrue();
    }

    @Test
    void 期間を指定するとシフトがある日だけ公開し公開しなかった日を知らせる() throws Exception {
        data.shift(taro, kitchen, OCT1, "09:00", "17:00");
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.shift(taro, kitchen, OCT5, "09:00", "17:00");

        mvc.perform(post("/admin/shifts/publish-range").with(user(admin)).with(csrf())
                        .param("date", "2026-10-02").param("from", "2026-10-01").param("to", "2026-10-05"))
                .andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"))
                .andExpect(flash().attribute("message",
                        "10/1〜10/5を公開しました（シフトが登録されていないため公開しなかった日：10/3、10/4）"));

        assertThat(publishedDateMapper.exists(OCT1)).isTrue();
        assertThat(publishedDateMapper.exists(OCT2)).isTrue();
        assertThat(publishedDateMapper.exists(OCT3)).isFalse();
        assertThat(publishedDateMapper.exists(OCT4)).isFalse();
        assertThat(publishedDateMapper.exists(OCT5)).isTrue();
    }

    @Test
    void 期間内のすべての日にシフトがあれば公開しなかった日は出さない() throws Exception {
        data.shift(taro, kitchen, OCT1, "09:00", "17:00");
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");

        mvc.perform(post("/admin/shifts/publish-range").with(user(admin)).with(csrf())
                        .param("date", "2026-10-01").param("from", "2026-10-01").param("to", "2026-10-02"))
                .andExpect(flash().attribute("message", "10/1〜10/2を公開しました"));
    }

    @Test
    void 不正な期間は何も公開しない() throws Exception {
        data.shift(taro, kitchen, OCT1, "09:00", "17:00");
        data.shift(taro, kitchen, OCT5, "09:00", "17:00");

        String[][] cases = {
                {"2026-10-05", "2026-10-01", "終了日は開始日以降の日付を選択してください"},
                {"2026-10-01", "2026-11-01", "一度に公開できるのは31日分までです"},
                {"abc", "2026-10-05", "不正な日付です"},
                {"2026-10-01", "", "不正な日付です"},
                {"2026-10-02", "2026-10-04", "期間内にシフトが登録されている日がないため公開できません"},
        };
        for (String[] c : cases) {
            mvc.perform(post("/admin/shifts/publish-range").with(user(admin)).with(csrf())
                            .param("date", "2026-10-02").param("from", c[0]).param("to", c[1]))
                    .andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"))
                    .andExpect(flash().attribute("error", c[2]));
        }

        assertThat(publishedDateMapper.exists(OCT1)).isFalse();
        assertThat(publishedDateMapper.exists(OCT5)).isFalse();
    }

    @Test
    void 期間の初期値は表示中の日のサイクルで選択肢は前後1サイクルずつ() throws Exception {
        ShiftDayView view = view("2026-10-15");
        assertThat(view.getRangeStart()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(view.getRangeEnd()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(view.getRangeOptions()).hasSize(31);
        assertThat(view.getRangeOptions().get(0).getValue()).isEqualTo(OCT1);
        assertThat(view.getRangeOptions().get(0).getLabel()).isEqualTo("10/1（木）");
        assertThat(view.getRangeOptions()).extracting(DateOption::getValue).last()
                .isEqualTo(LocalDate.of(2026, 10, 31));

        // 年をまたぐ
        ShiftDayView december = view("2026-12-25");
        assertThat(december.getRangeStart()).isEqualTo(LocalDate.of(2026, 12, 21));
        assertThat(december.getRangeEnd()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(december.getRangeOptions().get(0).getValue()).isEqualTo(LocalDate.of(2026, 12, 11));
        assertThat(december.getRangeOptions()).extracting(DateOption::getValue).last()
                .isEqualTo(LocalDate.of(2027, 1, 10));
    }

    @Test
    void 公開済みの日にはこの日を公開ボタンを出さない() throws Exception {
        mvc.perform(get("/admin/shifts").param("date", "2026-10-02").with(user(admin)))
                .andExpect(content().string(Matchers.containsString("この日を公開")))
                .andExpect(content().string(Matchers.containsString("まで公開")));

        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.publish(OCT2);
        mvc.perform(get("/admin/shifts").param("date", "2026-10-02").with(user(admin)))
                .andExpect(content().string(Matchers.not(Matchers.containsString("この日を公開"))))
                .andExpect(content().string(Matchers.containsString("まで公開")));
    }

    @Test
    void 一般スタッフは公開できない() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        LoginUser staff = data.login(taro);

        mvc.perform(post("/admin/shifts/publish").with(user(staff)).with(csrf()).param("date", "2026-10-02"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/shifts/publish-range").with(user(staff)).with(csrf())
                        .param("date", "2026-10-02").param("from", "2026-10-01").param("to", "2026-10-05"))
                .andExpect(status().isForbidden());

        assertThat(publishedDateMapper.exists(OCT2)).isFalse();
    }

    private ShiftDayView view(String date) throws Exception {
        MvcResult result = mvc.perform(get("/admin/shifts").param("date", date).with(user(admin)))
                .andExpect(status().isOk())
                .andReturn();
        return (ShiftDayView) result.getModelAndView().getModel().get("view");
    }
}
```

- [ ] **Step 2: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=ShiftPublishTest`
Expected: FAIL（コンパイルエラー：`DateOption`・`ShiftDayView#getRangeStart` 等が存在しない）

- [ ] **Step 3: Mapper・Repositoryを実装する**

`mapper/ShiftMapper.java` に追加：

```java
    /** シフトが1件以上ある日（昇順） */
    @Select("""
            SELECT DISTINCT work_date FROM shifts
            WHERE work_date BETWEEN #{from} AND #{to}
            ORDER BY work_date
            """)
    List<LocalDate> findDates(@Param("from") LocalDate from, @Param("to") LocalDate to);
```

`repository/ShiftRepository.java` に追加：

```java
    /** シフトが1件以上ある日（昇順） */
    public List<LocalDate> findDates(LocalDate from, LocalDate to) {
        return shiftMapper.findDates(from, to);
    }
```

`mapper/PublishedDateMapper.java` に追加（import に `org.apache.ibatis.annotations.Insert` を追加）：

```java
    /** 公開済みなら何もしない */
    @Insert("INSERT INTO published_dates (work_date) VALUES (#{date}) ON CONFLICT DO NOTHING")
    void insert(@Param("date") LocalDate date);
```

`repository/PublishedDateRepository.java` に追加：

```java
    /** 公開済みなら何もしない */
    public void publish(LocalDate date) {
        publishedDateMapper.insert(date);
    }
```

- [ ] **Step 4: 公開のServiceを実装する**

`service/PublishService.java`：

```java
package jp.bk.shiftmanager.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.repository.PublishedDateRepository;
import jp.bk.shiftmanager.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 確定シフトの公開（管理者）。公開の取り消しはない */
@Service
@RequiredArgsConstructor
public class PublishService {

    /** 一度に公開できる日数（画面の選択肢は最大31日） */
    private static final int MAX_DAYS = 31;

    private final ShiftRepository shiftRepository;
    private final PublishedDateRepository publishedDateRepository;

    /** その日を公開する。シフトが登録されていない日は公開できない */
    @Transactional
    public void publishDay(String dateText) {
        LocalDate date = parseDate(dateText);
        if (shiftRepository.findDates(date, date).isEmpty()) {
            throw new BusinessException("シフトが登録されていないため公開できません");
        }
        publishedDateRepository.publish(date);
    }

    /**
     * 期間内のシフトが登録されている日を公開し、公開しなかった日（シフト未登録）を返す。
     * 1日も公開できない場合は入力エラー
     */
    @Transactional
    public List<LocalDate> publishRange(String fromText, String toText) {
        LocalDate from = parseDate(fromText);
        LocalDate to = parseDate(toText);
        if (to.isBefore(from)) {
            throw new BusinessException("終了日は開始日以降の日付を選択してください");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new BusinessException("一度に公開できるのは" + MAX_DAYS + "日分までです");
        }
        Set<LocalDate> withShifts = new HashSet<>(shiftRepository.findDates(from, to));
        if (withShifts.isEmpty()) {
            throw new BusinessException("期間内にシフトが登録されている日がないため公開できません");
        }
        List<LocalDate> skipped = new ArrayList<>();
        for (LocalDate date : from.datesUntil(to.plusDays(1)).toList()) {
            if (withShifts.contains(date)) {
                publishedDateRepository.publish(date);
            } else {
                skipped.add(date);
            }
        }
        return skipped;
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text == null ? "" : text);
        } catch (DateTimeParseException e) {
            throw new BusinessException("不正な日付です");
        }
    }
}
```

- [ ] **Step 5: 期間の選択肢を画面に渡す**

`dto/DateOption.java`：

```java
package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 日付のプルダウンの選択肢 */
@Data
@AllArgsConstructor
public class DateOption {
    private LocalDate value;
    /** 例：10/1（木） */
    private String label;
}
```

`dto/ShiftDayView.java` に追加：

```java
    /** 「○日〜○日まで公開」の選択肢（表示中の日を含むサイクルと前後1サイクル） */
    private List<DateOption> rangeOptions;
    /** 期間の初期値（表示中の日を含むサイクルの初日〜末日） */
    private LocalDate rangeStart;
    private LocalDate rangeEnd;
```

`service/ShiftService.java` の import に `jp.bk.shiftmanager.dto.DateOption`・`jp.bk.shiftmanager.util.Cycle` を追加し、`buildView` の `view.setNextIndex(index);` の直後に追加する：

```java
        Cycle cycle = Cycle.of(date);
        view.setRangeStart(cycle.start());
        view.setRangeEnd(cycle.end());
        view.setRangeOptions(cycle.previous().start().datesUntil(cycle.next().end().plusDays(1))
                .map(option -> new DateOption(option, DateLabels.monthDayWeek(option)))
                .toList());
```

- [ ] **Step 6: コントローラーに公開を追加する**

`controller/ShiftAdminController.java` の import に `java.time.LocalDate`、`java.util.List`、`java.util.stream.Collectors`、`jp.bk.shiftmanager.service.PublishService`、`jp.bk.shiftmanager.util.DateLabels` を追加し、フィールドとメソッドを追加する：

```java
    private final PublishService publishService;
```

```java
    @PostMapping("/publish")
    public String publishDay(@RequestParam(required = false) String date, RedirectAttributes redirectAttributes) {
        try {
            publishService.publishDay(date);
            redirectAttributes.addFlashAttribute("message", "公開しました");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        redirectAttributes.addAttribute("date", shiftService.resolveDate(date).toString());
        return REDIRECT_DAY;
    }

    /** date は戻り先（表示中の日） */
    @PostMapping("/publish-range")
    public String publishRange(@RequestParam(required = false) String date,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            RedirectAttributes redirectAttributes) {
        try {
            List<LocalDate> skipped = publishService.publishRange(from, to);
            String message = DateLabels.monthDay(LocalDate.parse(from)) + "〜"
                    + DateLabels.monthDay(LocalDate.parse(to)) + "を公開しました";
            if (!skipped.isEmpty()) {
                message += "（シフトが登録されていないため公開しなかった日："
                        + skipped.stream().map(DateLabels::monthDay).collect(Collectors.joining("、")) + "）";
            }
            redirectAttributes.addFlashAttribute("message", message);
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        redirectAttributes.addAttribute("date", shiftService.resolveDate(date).toString());
        return REDIRECT_DAY;
    }
```

- [ ] **Step 7: 画面に公開フォームを追加する**

`src/main/resources/templates/admin/shifts/day.html` の `<div th:replace="~{layout :: flash}"></div>` の直前に追加する（登録フォームの入れ子にしない）：

```html
  <div th:if="${!#lists.isEmpty(view.groups)}" class="mb-4 flex flex-wrap items-center gap-2">
    <form th:unless="${view.published}" th:action="@{/admin/shifts/publish}" method="post" data-publish-form>
      <input type="hidden" name="date" th:value="${view.date}">
      <button class="btn-secondary">この日を公開</button>
    </form>
    <form th:action="@{/admin/shifts/publish-range}" method="post" data-publish-form
          class="flex flex-wrap items-center gap-1">
      <input type="hidden" name="date" th:value="${view.date}">
      <select name="from" class="input mt-0 w-auto px-1" aria-label="公開の開始日">
        <option th:each="o : ${view.rangeOptions}" th:value="${o.value}" th:text="${o.label}"
                th:selected="${o.value == view.rangeStart}">10/1（木）</option>
      </select>
      <span>〜</span>
      <select name="to" class="input mt-0 w-auto px-1" aria-label="公開の終了日">
        <option th:each="o : ${view.rangeOptions}" th:value="${o.value}" th:text="${o.label}"
                th:selected="${o.value == view.rangeEnd}">10/10（土）</option>
      </select>
      <button class="btn-secondary">まで公開</button>
    </form>
    <span class="text-xs text-stone-500">公開されるのは登録済みの内容です。シフトが登録されていない日は公開されません。</span>
  </div>
```

`src/main/resources/static/js/shifts.js` の最後の `})();` の直前に追加する：

```js
  // 未保存の入力がある状態で公開するときは確認する（公開されるのは登録済みの内容だけ）
  document.querySelectorAll('form[data-publish-form]').forEach((publishForm) => {
    publishForm.addEventListener('submit', (event) => {
      if (dirty && !window.confirm('保存していない入力があります。公開されるのは登録済みの内容だけです。公開しますか？')) {
        event.preventDefault();
      }
    });
  });
```

- [ ] **Step 8: テストが通ることを確認する**

Run: `./mvnw test -Dtest=ShiftPublishTest,ShiftDayTest,ShiftSaveTest`
Expected: PASS

- [ ] **Step 9: ブラウザで動作を確認する**

`npm run build` の後、`./mvnw spring-boot:run` で起動し、管理者で `/admin/shifts` を開いて次を確認する（確認できない場合はユーザーに報告して確認を依頼する）：
- 期間のプルダウンの初期値が表示中の日を含むサイクルの初日〜末日になっている
- 「この日を公開」で「公開済み」になり、ボタンが消える
- 名前を変更して登録せずに公開ボタンを押すと確認が出る

- [ ] **Step 10: 全テストを実行してコミットする**

Run: `./mvnw test`
Expected: PASS

```bash
git add -A
git commit -m "feat: シフトの公開（この日を公開・期間を指定して公開）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 11: この計画ファイルのTask 3のチェックボックスをすべて `[x]` にしてコミットし、停止してユーザーに報告する**

```bash
git add docs/superpowers/plans/2026-09-27-plan3-shifts.md
git commit -m "docs: Plan 3 Task 3 完了

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: 公開済みの日の編集（確認と「変更あり」の記録）

**Files:**
- Create: `entity/ShiftChangeType.java`、`entity/ShiftChange.java`、`mapper/ShiftChangeMapper.java`、`repository/ShiftChangeRepository.java`、`util/ShiftChanges.java`
- Modify: `form/ShiftDayForm.java`（`published`）、`service/ShiftService.java`（`saveDay` で公開状態の確認と変更の記録）
- Modify: `src/main/resources/templates/admin/shifts/day.html`（公開済みの印と案内）、`src/main/resources/static/js/shifts.js`（登録前の確認）
- Modify: `src/test/java/jp/bk/shiftmanager/TestData.java`（`acknowledgeChanges`）
- Test: `util/ShiftChangesTest.java`、`controller/ShiftChangeTest.java`

**Interfaces:**
- Consumes: Task 1〜3 の `ShiftService#saveDay`・非公開の `parseRows`・`getDay(LocalDate, ShiftDayForm)`、`PublishedDateRepository#isPublished`、`entity.Shift`、`TestData#shift`・`#publish`
- Produces:
  - `entity.ShiftChangeType`（`ADDED`・`UPDATED`・`CANCELLED`）、`entity.ShiftChange`（`id, userId, workDate, changeType, acknowledgedAt`）
  - `ShiftChangeMapper#deleteUnacknowledged(long userId, LocalDate date)`・`#insert(long userId, LocalDate date, ShiftChangeType type)`・`#findByDate(LocalDate): List<ShiftChange>`
  - `ShiftChangeRepository#replaceUnacknowledged(long userId, LocalDate date, ShiftChangeType type)`・`#findByDate(LocalDate)`（Plan 4 でスタッフのトップ画面用の取得・確認済みを追加する）
  - `util.ShiftChanges.detect(Shift before, Shift after): Optional<ShiftChangeType>`
  - `ShiftDayForm#published`（画面を開いたときに公開済みだったか）
  - `TestData#acknowledgeChanges(User)`

- [ ] **Step 1: 変更の種別の判定の失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/util/ShiftChangesTest.java`：

```java
package jp.bk.shiftmanager.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import jp.bk.shiftmanager.entity.Shift;
import jp.bk.shiftmanager.entity.ShiftChangeType;
import org.junit.jupiter.api.Test;

class ShiftChangesTest {

    @Test
    void 前がなく後があれば追加() {
        assertThat(ShiftChanges.detect(null, shift(1L, "09:00", "17:00"))).contains(ShiftChangeType.ADDED);
    }

    @Test
    void 前があり後がなければ取り消し() {
        assertThat(ShiftChanges.detect(shift(1L, "09:00", "17:00"), null)).contains(ShiftChangeType.CANCELLED);
    }

    @Test
    void 時刻かポジションが違えば変更() {
        Shift before = shift(1L, "09:00", "17:00");
        assertThat(ShiftChanges.detect(before, shift(1L, "10:00", "17:00"))).contains(ShiftChangeType.UPDATED);
        assertThat(ShiftChanges.detect(before, shift(1L, "09:00", "16:00"))).contains(ShiftChangeType.UPDATED);
        assertThat(ShiftChanges.detect(before, shift(2L, "09:00", "17:00"))).contains(ShiftChangeType.UPDATED);
    }

    @Test
    void 差分がなければ記録しない() {
        Shift before = shift(1L, "09:00", "17:00");
        before.setId(10L);
        // 登録し直すとIDは変わるが、内容が同じなら変更ではない
        Shift after = shift(1L, "09:00", "17:00");
        after.setId(20L);
        assertThat(ShiftChanges.detect(before, after)).isEmpty();
        assertThat(ShiftChanges.detect(null, null)).isEmpty();
    }

    private static Shift shift(Long positionId, String start, String end) {
        Shift shift = new Shift();
        shift.setPositionId(positionId);
        shift.setStartTime(LocalTime.parse(start));
        shift.setEndTime(LocalTime.parse(end));
        return shift;
    }
}
```

- [ ] **Step 2: 公開済みの日の編集の失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/TestData.java` に追加する：

```java
    /** そのスタッフの未確認の変更をすべて確認済みにする */
    public void acknowledgeChanges(User user) {
        jdbc.update("UPDATE shift_changes SET acknowledged_at = now() WHERE user_id = ? AND acknowledged_at IS NULL",
                user.getId());
    }
```

`src/test/java/jp/bk/shiftmanager/controller/ShiftChangeTest.java`：

```java
package jp.bk.shiftmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.dto.ShiftDayView;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.Shift;
import jp.bk.shiftmanager.entity.ShiftChange;
import jp.bk.shiftmanager.entity.ShiftChangeType;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.ShiftChangeMapper;
import jp.bk.shiftmanager.mapper.ShiftMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 公開済みの日の編集（今日は2026-09-25） */
class ShiftChangeTest extends IntegrationTestBase {

    private static final LocalDate OCT2 = LocalDate.of(2026, 10, 2);

    @Autowired
    ShiftMapper shiftMapper;

    @Autowired
    ShiftChangeMapper shiftChangeMapper;

    LoginUser admin;
    Position kitchen;
    Position counter;
    User taro;
    User hanako;
    User jiro;

    @BeforeEach
    void setUp() {
        admin = data.login(data.user("boss", "店長", true));
        kitchen = data.position("キッチン", 1);
        counter = data.position("カウンター", 2);
        taro = data.user("taro", "山田太郎", false);
        hanako = data.user("hanako", "佐藤花子", false);
        jiro = data.user("jiro", "鈴木次郎", false);
    }

    @Test
    void 下書きの日の登録では記録しない() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");

        MockHttpServletRequestBuilder request = save("2026-10-02", false);
        row(request, 0, kitchen, taro, "10:00", "17:00");
        row(request, 1, kitchen, hanako, "10:00", "15:00");
        mvc.perform(request).andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"));

        assertThat(shiftChangeMapper.findByDate(OCT2)).isEmpty();
    }

    @Test
    void 初回の公開は変更として記録しない() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");

        mvc.perform(post("/admin/shifts/publish").with(user(admin)).with(csrf()).param("date", "2026-10-02"))
                .andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"));

        assertThat(shiftChangeMapper.findByDate(OCT2)).isEmpty();
    }

    @Test
    void 公開済みの日の追加_時刻変更_ポジション変更_取り消しを記録する() throws Exception {
        User saburo = data.user("saburo", "高橋三郎", false);
        User shiro = data.user("shiro", "伊藤四郎", false);
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.shift(hanako, counter, OCT2, "10:00", "15:00");
        data.shift(jiro, kitchen, OCT2, "12:00", "18:00");
        data.shift(shiro, counter, OCT2, "08:00", "12:00");
        data.publish(OCT2);

        MockHttpServletRequestBuilder request = save("2026-10-02", true);
        row(request, 0, kitchen, taro, "10:00", "17:00");     // 時刻変更
        row(request, 1, kitchen, hanako, "10:00", "15:00");   // ポジションだけ変更
        row(request, 2, counter, saburo, "12:00", "20:00");   // 追加
        row(request, 3, counter, shiro, "08:00", "12:00");    // 変更なし
        // 鈴木次郎の行は送らない（取り消し）
        mvc.perform(request).andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"));

        Map<Long, ShiftChangeType> changes = shiftChangeMapper.findByDate(OCT2).stream()
                .collect(Collectors.toMap(ShiftChange::getUserId, ShiftChange::getChangeType));
        assertThat(changes).containsOnly(
                Map.entry(taro.getId(), ShiftChangeType.UPDATED),
                Map.entry(hanako.getId(), ShiftChangeType.UPDATED),
                Map.entry(saburo.getId(), ShiftChangeType.ADDED),
                Map.entry(jiro.getId(), ShiftChangeType.CANCELLED));
    }

    @Test
    void 未確認の変更は1人1日1件で新しい種別に置き換える() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.publish(OCT2);

        MockHttpServletRequestBuilder change = save("2026-10-02", true);
        row(change, 0, kitchen, taro, "10:00", "17:00");
        mvc.perform(change).andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"));

        MockHttpServletRequestBuilder cancel = save("2026-10-02", true);
        row(cancel, 0, kitchen, null, "", "");
        mvc.perform(cancel).andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"));

        List<ShiftChange> changes = shiftChangeMapper.findByDate(OCT2);
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).getChangeType()).isEqualTo(ShiftChangeType.CANCELLED);
        assertThat(changes.get(0).getAcknowledgedAt()).isNull();
    }

    @Test
    void 変更なしで登録し直しても未確認の変更は残る() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.publish(OCT2);

        MockHttpServletRequestBuilder change = save("2026-10-02", true);
        row(change, 0, kitchen, taro, "10:00", "17:00");
        mvc.perform(change);
        MockHttpServletRequestBuilder same = save("2026-10-02", true);
        row(same, 0, kitchen, taro, "10:00", "17:00");
        mvc.perform(same).andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"));

        assertThat(shiftChangeMapper.findByDate(OCT2)).extracting(ShiftChange::getChangeType)
                .containsExactly(ShiftChangeType.UPDATED);
    }

    @Test
    void 確認済みの変更は残し新しい変更を追加する() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.publish(OCT2);

        MockHttpServletRequestBuilder change = save("2026-10-02", true);
        row(change, 0, kitchen, taro, "10:00", "17:00");
        mvc.perform(change);
        data.acknowledgeChanges(taro);

        MockHttpServletRequestBuilder again = save("2026-10-02", true);
        row(again, 0, kitchen, taro, "11:00", "17:00");
        mvc.perform(again).andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"));

        List<ShiftChange> changes = shiftChangeMapper.findByDate(OCT2);
        assertThat(changes).hasSize(2);
        assertThat(changes.get(0).getAcknowledgedAt()).isNotNull();
        assertThat(changes.get(1).getAcknowledgedAt()).isNull();
        assertThat(changes.get(1).getChangeType()).isEqualTo(ShiftChangeType.UPDATED);
    }

    @Test
    void 下書きで開いた画面から公開後に登録すると保存されず入力が残る() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        // 画面を開いた後に別のタブで公開された
        data.publish(OCT2);

        MockHttpServletRequestBuilder request = save("2026-10-02", false);
        row(request, 0, kitchen, taro, "12:00", "17:00");
        MvcResult result = mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(model().attribute("error",
                        "この日は画面を開いた後に公開されました。変更はすぐスタッフに表示されるため、内容を確認してもう一度登録してください"))
                .andReturn();

        List<Shift> saved = shiftMapper.findByDate(OCT2);
        assertThat(saved.get(0).getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(shiftChangeMapper.findByDate(OCT2)).isEmpty();

        // 公開済みとして表示し直すため、もう一度登録すれば保存できる
        ShiftDayView view = (ShiftDayView) result.getModelAndView().getModel().get("view");
        assertThat(view.isPublished()).isTrue();
        assertThat(view.getGroups().get(0).getRows().get(0).getStartTime()).isEqualTo("12:00");
    }

    @Test
    void 公開済みの日の画面には登録前の確認用の印と案内を出す() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.publish(OCT2);

        mvc.perform(get("/admin/shifts").param("date", "2026-10-02").with(user(admin)))
                .andExpect(content().string(Matchers.containsString("data-published=\"true\"")))
                .andExpect(content().string(Matchers.containsString("name=\"published\" value=\"true\"")))
                .andExpect(content().string(Matchers.containsString("公開済みの日です。登録するとすぐスタッフに表示されます")));
    }

    private MockHttpServletRequestBuilder save(String date, boolean published) {
        return post("/admin/shifts").with(user(admin)).with(csrf())
                .param("date", date).param("published", String.valueOf(published));
    }

    /** 1行分の入力を付ける（user が null なら名前未選択） */
    private void row(MockHttpServletRequestBuilder request, int index, Position position, User user, String start,
            String end) {
        String prefix = "rows[" + index + "].";
        request.param(prefix + "positionId", position.getId().toString())
                .param(prefix + "userId", user == null ? "" : user.getId().toString())
                .param(prefix + "startTime", start)
                .param(prefix + "endTime", end);
    }
}
```

- [ ] **Step 3: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=ShiftChangesTest,ShiftChangeTest`
Expected: FAIL（コンパイルエラー：`ShiftChanges`・`ShiftChangeType`・`ShiftChangeMapper` が存在しない）

- [ ] **Step 4: エンティティ・Mapper・Repository・種別の判定を実装する**

`entity/ShiftChangeType.java`：

```java
package jp.bk.shiftmanager.entity;

/** 公開後の変更の種別（shift_changes.change_type） */
public enum ShiftChangeType {
    /** 追加 */
    ADDED,
    /** 時刻またはポジションの変更 */
    UPDATED,
    /** 取り消し */
    CANCELLED
}
```

`entity/ShiftChange.java`：

```java
package jp.bk.shiftmanager.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Data;

/** 公開後の変更マーク（「変更あり」）。スタッフが確認済みにするまで表示する */
@Data
public class ShiftChange {
    private Long id;
    private Long userId;
    private LocalDate workDate;
    private ShiftChangeType changeType;
    /** 確認済みにした日時（未確認ならnull） */
    private OffsetDateTime acknowledgedAt;
}
```

`mapper/ShiftChangeMapper.java`：

```java
package jp.bk.shiftmanager.mapper;

import java.time.LocalDate;
import java.util.List;
import jp.bk.shiftmanager.entity.ShiftChange;
import jp.bk.shiftmanager.entity.ShiftChangeType;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 公開後の変更マーク */
@Mapper
public interface ShiftChangeMapper {

    @Delete("""
            DELETE FROM shift_changes
            WHERE user_id = #{userId} AND work_date = #{date} AND acknowledged_at IS NULL
            """)
    int deleteUnacknowledged(@Param("userId") long userId, @Param("date") LocalDate date);

    @Insert("INSERT INTO shift_changes (user_id, work_date, change_type) VALUES (#{userId}, #{date}, #{type})")
    void insert(@Param("userId") long userId, @Param("date") LocalDate date, @Param("type") ShiftChangeType type);

    /** その日の変更（確認済みを含む。記録順） */
    @Select("SELECT * FROM shift_changes WHERE work_date = #{date} ORDER BY id")
    List<ShiftChange> findByDate(@Param("date") LocalDate date);
}
```

`repository/ShiftChangeRepository.java`：

```java
package jp.bk.shiftmanager.repository;

import java.time.LocalDate;
import java.util.List;
import jp.bk.shiftmanager.entity.ShiftChange;
import jp.bk.shiftmanager.entity.ShiftChangeType;
import jp.bk.shiftmanager.mapper.ShiftChangeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ShiftChangeRepository {

    private final ShiftChangeMapper shiftChangeMapper;

    /** 未確認の変更を新しい種別で置き換える（未確認は1人1日1件。確認済みの記録は残す） */
    public void replaceUnacknowledged(long userId, LocalDate date, ShiftChangeType type) {
        shiftChangeMapper.deleteUnacknowledged(userId, date);
        shiftChangeMapper.insert(userId, date, type);
    }

    public List<ShiftChange> findByDate(LocalDate date) {
        return shiftChangeMapper.findByDate(date);
    }
}
```

`util/ShiftChanges.java`：

```java
package jp.bk.shiftmanager.util;

import java.util.Objects;
import java.util.Optional;
import jp.bk.shiftmanager.entity.Shift;
import jp.bk.shiftmanager.entity.ShiftChangeType;

/** 公開済みの日の登録前後のシフトから、変更の種別を決める */
public final class ShiftChanges {

    private ShiftChanges() {
    }

    /**
     * 前なし・後あり → ADDED、前あり・後なし → CANCELLED、
     * IN・OUT・ポジションのどれかが違う → UPDATED、差分なし → 空
     */
    public static Optional<ShiftChangeType> detect(Shift before, Shift after) {
        if (before == null && after == null) {
            return Optional.empty();
        }
        if (before == null) {
            return Optional.of(ShiftChangeType.ADDED);
        }
        if (after == null) {
            return Optional.of(ShiftChangeType.CANCELLED);
        }
        boolean same = Objects.equals(before.getPositionId(), after.getPositionId())
                && Objects.equals(before.getStartTime(), after.getStartTime())
                && Objects.equals(before.getEndTime(), after.getEndTime());
        return same ? Optional.empty() : Optional.of(ShiftChangeType.UPDATED);
    }
}
```

- [ ] **Step 5: 登録で公開状態を確認し、変更を記録する**

`form/ShiftDayForm.java` に追加：

```java
    /** 画面を開いたときに公開済みだったか（開いた後に公開された場合の確認に使う） */
    private boolean published;
```

`service/ShiftService.java` の import に `java.util.TreeSet`、`java.util.function.Function`、`jp.bk.shiftmanager.repository.ShiftChangeRepository`、`jp.bk.shiftmanager.util.ShiftChanges` を追加し、フィールドを追加する：

```java
    private final ShiftChangeRepository shiftChangeRepository;
```

`saveDay` を次に置き換え、`recordChanges` を追加する：

```java
    /**
     * 1日分のシフトを登録し直す。空欄の行は捨て、1行でも不正があれば何も保存しない。
     * 並び順は保存せず、表示時にポジションの表示順 → INの早い順に並べる。
     * 公開済みの日は、登録前後の差分を「変更あり」として記録する
     */
    @Transactional
    public void saveDay(ShiftDayForm form) {
        LocalDate date = parseDate(form.getDate());
        boolean published = publishedDateRepository.isPublished(date);
        // 下書きとして開いた画面から、確認なしにスタッフへ反映させない
        if (published && !form.isPublished()) {
            throw new BusinessException(
                    "この日は画面を開いた後に公開されました。変更はすぐスタッフに表示されるため、内容を確認してもう一度登録してください");
        }
        List<Shift> before = shiftRepository.findByDate(date);
        List<Shift> after = parseRows(date, form, before);
        shiftRepository.deleteByDate(date);
        after.forEach(shiftRepository::insert);
        if (published) {
            recordChanges(date, before, after);
        }
    }

    /** 公開済みの日の変更を記録する。差分のないスタッフは記録しない */
    private void recordChanges(LocalDate date, List<Shift> before, List<Shift> after) {
        Map<Long, Shift> beforeByUser = before.stream()
                .collect(Collectors.toMap(Shift::getUserId, Function.identity()));
        Map<Long, Shift> afterByUser = after.stream()
                .collect(Collectors.toMap(Shift::getUserId, Function.identity()));
        Set<Long> userIds = new TreeSet<>(beforeByUser.keySet());
        userIds.addAll(afterByUser.keySet());
        for (Long userId : userIds) {
            ShiftChanges.detect(beforeByUser.get(userId), afterByUser.get(userId))
                    .ifPresent(type -> shiftChangeRepository.replaceUnacknowledged(userId, date, type));
        }
    }
```

- [ ] **Step 6: 画面に公開済みの印・案内と登録前の確認を追加する**

`src/main/resources/templates/admin/shifts/day.html` の登録フォームを変更する。`<form id="shift-form" ...>` の開始タグに `th:data-published="${view.published}"` を追加し、`<input type="hidden" name="date" ...>` の直後に追加する：

```html
    <input type="hidden" name="published" th:value="${view.published}">
```

画面下部の `<button class="btn-primary px-8">登録する</button>` の直前に追加する：

```html
        <span th:if="${view.published}" class="text-sm text-red-700">公開済みの日です。登録するとすぐスタッフに表示されます</span>
```

`src/main/resources/static/js/shifts.js` の次の行を：

```js
  form.addEventListener('submit', () => { dirty = false; });
```

次に置き換える：

```js
  form.addEventListener('submit', (event) => {
    // 公開済みの日は登録前に確認する
    if (form.dataset.published === 'true'
      && !window.confirm('公開済みの日です。変更はすぐスタッフに表示されます。登録しますか？')) {
      event.preventDefault();
      return;
    }
    dirty = false;
  });
```

- [ ] **Step 7: テストが通ることを確認する**

Run: `./mvnw test -Dtest=ShiftChangesTest,ShiftChangeTest,ShiftSaveTest,ShiftPublishTest,ShiftDayTest`
Expected: PASS（`ShiftSaveTest` は `published` を送らないため false として扱われ、下書きの日への登録として従来どおり通る）

- [ ] **Step 8: ブラウザで動作を確認する**

`npm run build` の後、`./mvnw spring-boot:run` で起動し、管理者で公開済みの日の `/admin/shifts?date=` を開いて次を確認する（確認できない場合はユーザーに報告して確認を依頼する）：
- 「登録する」を押すと「公開済みの日です。変更はすぐスタッフに表示されます。登録しますか？」が出て、キャンセルすると保存されず入力も残る
- 下書きの日では確認が出ない
- 下書きの日を2つのタブで開き、片方で公開してからもう片方で「登録する」とエラーになり、入力が残る。もう一度「登録する」と確認が出て保存できる

- [ ] **Step 9: 全テストを実行してコミットする**

Run: `./mvnw test`
Expected: PASS

```bash
git add -A
git commit -m "feat: 公開済みの日の編集の確認と「変更あり」の記録

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 10: この計画ファイルのTask 4のチェックボックスをすべて `[x]` にしてコミットし、停止してユーザーに報告する**

```bash
git add docs/superpowers/plans/2026-09-27-plan3-shifts.md
git commit -m "docs: Plan 3 Task 4 完了

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## 後続Plan（Plan 3完了後に、実装済みコードを前提に詳細化する）

| Plan | 内容 |
|---|---|
| Plan 4 閲覧 | スタッフのトップ画面（`shift_changes` の未確認分を「変更あり」として表示し「確認済み」で `acknowledged_at` を設定、取り消しは「この日のシフトは取り消されました」、次回出勤、直近の出勤、締切案内（提出済み判定は Plan 2 と同じ規則）、今月・来月の予定時間（公開済みのみ））、日別シフト一覧（公開済みの日のみ、ポジション別・IN順、過去1週間まで。管理者は過去分も閲覧可） |
