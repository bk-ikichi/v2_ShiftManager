# Plan 4 閲覧 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **セッション運用：** 1セッション1 Task。Taskの最後のステップ（コミットとチェックボックス更新）が終わったら停止してユーザーに報告する。次のTaskは `/clear` 後の新しいセッションで行う。新しいセッションではこの冒頭（File Structureまで）と、未完了の最初のTaskの範囲だけを読む（`CLAUDE.md` 参照）。

**Goal:** スタッフ（管理者を含む）が、公開済みの確定シフトを日別一覧とトップ画面（変更あり・次回の出勤・月カレンダー・締切案内・予定時間）で確認できるようにする。

**Architecture:** Plan 1〜3 と同じく controller → service → repository → mapper の一方向。公開状態は `published_dates` の有無で決まるため、スタッフに見せるシフトの取得はすべて `published_dates` で絞る。日別一覧は `DailyShiftService`、トップ画面は `HomeService` が担当し、どちらも既存の Repository だけを呼ぶ（Service同士は呼ばない）。表示用の文字列（日付ラベル・時間帯・合計時間）はServiceでDTOに詰め、Thymeleafでは組み立てない。JavaScriptは使わない。

**Tech Stack:** Java 17、Spring Boot 4.1.1、MyBatis（アノテーションSQL）、Spring Security 7、Thymeleaf 3.1、Tailwind CSS 4、PostgreSQL 17、JUnit 5 + MockMvc + Testcontainers 2

**Spec:** `documents/2026-09-25-shift-manager-v2-spec.md`（「シフトの閲覧（スタッフ）」「提出済みの判定」）、DB設計：`documents/2026-09-25-db-design.md`（shifts・published_dates・shift_changes）。テーブルは Plan 1 の `V1__init.sql` で作成済みのため、マイグレーションは追加しない

## Global Constraints

- 画面の文言・コードのコメントはすべて日本語
- パッケージは層ごと。controllerはserviceのみ、serviceはrepositoryのみを呼ぶ（serviceから別のserviceを呼ばない）。`util` はどの層からも使ってよい
- 「今日」は必ず `Clock` Bean から得る（`LocalDate.now(clock)`）。引数なしの `LocalDate.now()` を使わない
- スタッフに見せるシフトは公開済みの日（`published_dates` にある日）だけ。下書きの日のシフトは、日別一覧・トップ画面のどこにも出さない
- 日別一覧：ポジションの表示順でグループ分けし、各グループ内はINの早い順。全員分（名前・IN/OUT）を表示する。非表示のポジション・無効化されたスタッフのシフトも表示する（過去のシフトは保持する）
- 過ぎた日は前月1日より前をスタッフの画面から非表示にする：スタッフは `前月1日`（`ViewRange.staffOldest`）以降の日だけ閲覧できる。管理者は過去分も閲覧できる（2026-09-28 ユーザー要望で「1週間」から変更。Task 1・Task 3 の本文のコード・テストは変更前のまま残している）
- 「変更あり」は本人が「確認済み」を押すまで表示する。取り消しは「この日のシフトは取り消されました」と表示する。確認済みにできるのは本人の未確認の変更だけ
- 提出済みの判定は Plan 2 と同じ：「そのサイクル内に1日以上の申請がある」または「この期間は出勤できない」にチェックがある
- 勤務予定時間：今月・来月それぞれの公開済みシフトの OUT − IN の合計（休憩は管理しないため拘束時間）。表示は `13時間30分`・`4時間`・`0時間`
- 時間帯の表示は `09:00〜17:00`（`TimeSlots.formatRange`）、日付の表示は `9/25（金）`（`DateLabels.monthDayWeek`）
- Thymeleafから `T(...)` で static メソッドを呼ばない。DTOは record ではなく Lombok の `@Data` クラスにする（既存DTOと同じ）
- スタッフの画面はスマートフォン優先（幅375pxで横スクロールにならない）
- コミットメッセージの末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` を付ける

### 仕様の解釈（仕様書に明記がないため、この計画で決めたこと）

- 「直近の出勤一覧」は **月カレンダーに置き換える**（2026-09-28 ユーザー要望。試行として導入し、使い勝手を見て見直す）
  - 日曜始まりの月単位。`/?month=yyyy-MM` で前後の月へ移動する（指定がない・不正なら今月）
  - マスには日と本人のIN（例：`11:00`）だけを出し、曜日は見出し行に出す（幅375pxで7列を収めるため）。本人のシフトがある日は日別一覧の自分の行と同じ色で強調する
  - 公開済みの日は押すとその日の日別一覧（`/shifts?date=`）へ移動する。未公開の日は灰色で押せない。本人の未確認の変更がある日には印を付ける
  - スタッフは前月1日より前の日を押せず、INも出さない（日別一覧と同じ制限）。管理者は押せる
  - 表示できる月は、スタッフは先月〜2か月後、管理者は過去を制限せず2か月後まで。範囲外の月を指定されたら今月を表示する（2026-09-28 ユーザー要望）
- 「次回の出勤」は **今日以降で最も早い公開済みシフト**（今日のシフトは時刻が過ぎていても次回として表示し「今日」の印を付ける）。探す範囲は来月末まで
- 「申請締切の案内」は **締切前（今日が締切日以前）で最も近いサイクル1つ** を表示する
- 日別一覧は管理者にも公開済みの日だけを表示する（下書きは転記画面で見る）。管理者には転記画面へのリンクを出す

## Review Focus

1. 下書き（未公開）の日のシフト：日別一覧・次回の出勤・カレンダー・予定時間のどこにも出ない → Task 1・Task 3でテスト
2. スタッフが日別一覧のURLに前月1日より前の日付・不正な日付を直接入れる：前月1日より前は表示せず案内を出す、不正な日付は今日を表示する（500エラーにしない）。管理者は前月1日より前も表示できる → Task 1でテスト
3. 公開後に無効化したスタッフ・非表示にしたポジションのシフト：日別一覧から消えずに表示される → Task 1でテスト
4. 「確認済み」に他人の変更ID・確認済みの変更ID・数値でないID・存在しないID・IDなしを送る：何も変わらずトップへ戻る（500エラーにしない） → Task 2でテスト
5. 境界の日付：今日が締切日当日なら同じサイクルを案内し、翌日は次のサイクルを案内する。予定時間は月末（9/30・10/31）を含み、前月末・翌々月1日を含まない。カレンダーはスタッフに先月を表示し先々月を表示しない。2か月後を表示し3か月後を表示しない。カレンダーの月に不正な値・範囲外の値を入れても今月を表示する（500エラーにしない） → Task 3でテスト

## File Structure

```
src/main/java/jp/bk/shiftmanager/
  mapper/      ShiftMapper に findPublishedByUser、ShiftChangeMapper に findUnacknowledgedByUser・acknowledge、
               PublishedDateMapper に findBetween を追加
  repository/  ShiftRepository・ShiftChangeRepository に同名メソッド、PublishedDateRepository に findDates を追加
  service/     DailyShiftService（日別一覧）, HomeService（トップ画面・確認済み）
  controller/  DailyShiftController（/shifts）, HomeController（/ と /changes/acknowledge に置き換え）
  dto/         DailyShiftView, DailyShiftGroup, DailyShiftRow,
               HomeView, ChangeNotice, ShiftChangeRow, MyShiftRow, MyShiftView, CalendarDay, CalendarView, DeadlineNotice
  util/        TimeSlots に formatRange を追加
src/main/resources/templates/
  layout.html      ヘッダーに「シフト」を追加
  shifts/day.html  日別シフト一覧
  home.html        トップ画面（置き換え）
src/test/java/jp/bk/shiftmanager/
  TestData.java                  change（変更ありの記録）を追加
  util/TimeSlotsTest             formatRange のテストを追加
  controller/DailyShiftTest, HomeChangeTest, HomeTest
```

---

### Task 1: 日別シフト一覧（公開済みの日のみ・過去1週間まで）

**Files:**
- Modify: `src/main/java/jp/bk/shiftmanager/util/TimeSlots.java`（`formatRange`）
- Create: `dto/DailyShiftView.java`、`dto/DailyShiftGroup.java`、`dto/DailyShiftRow.java`、`service/DailyShiftService.java`、`controller/DailyShiftController.java`
- Create: `src/main/resources/templates/shifts/day.html`
- Modify: `src/main/resources/templates/layout.html`（ヘッダーに「シフト」）
- Test: `util/TimeSlotsTest.java`（追記）、`controller/DailyShiftTest.java`

**Interfaces:**
- Consumes: `ShiftRepository#findByDate(LocalDate): List<Shift>`（INの早い順）、`PublishedDateRepository#isPublished(LocalDate)`、`PositionRepository#findAll()`（表示順。非表示を含む）、`UserRepository#findAll()`（無効を含む）、`DateLabels.monthDayWeek`、`TestData#shift`・`#publish`・`#hide`・`#disable`・`#login`
- Produces:
  - `util.TimeSlots.formatRange(LocalTime start, LocalTime end): String`（例 `09:00〜17:00`。Task 2・3でも使う）
  - `DailyShiftService#resolveDate(String): LocalDate`、`#getDay(LocalDate date, long viewerId, boolean admin): DailyShiftView`
  - `GET /shifts?date=yyyy-MM-dd`（モデル属性 `view`）。Task 2・3のトップ画面から日付ごとにリンクする

- [x] **Step 1: 時間帯の表示の失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/util/TimeSlotsTest.java` のクラス末尾に追加する：

```java
    @Test
    void 時間帯を表示用の文字列にする() {
        assertThat(TimeSlots.formatRange(LocalTime.of(9, 0), LocalTime.of(17, 30))).isEqualTo("09:00〜17:30");
    }
```

- [x] **Step 2: 日別一覧の失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/controller/DailyShiftTest.java`：

```java
package jp.bk.shiftmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.dto.DailyShiftGroup;
import jp.bk.shiftmanager.dto.DailyShiftRow;
import jp.bk.shiftmanager.dto.DailyShiftView;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.User;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 日別シフト一覧（今日は2026-09-25（金）） */
class DailyShiftTest extends IntegrationTestBase {

    private static final LocalDate OCT2 = LocalDate.of(2026, 10, 2);
    /** 今日の7日前（スタッフが閲覧できる最も古い日） */
    private static final LocalDate SEP18 = LocalDate.of(2026, 9, 18);
    private static final LocalDate SEP17 = LocalDate.of(2026, 9, 17);

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
        hanako = data.user("hanako", "佐藤花子", false);
        jiro = data.user("jiro", "鈴木次郎", false);
    }

    @Test
    void 公開済みの日はポジションの表示順とINの早い順で全員分を表示する() throws Exception {
        // 出勤者のいないポジションは表示しない
        data.position("社員", 0);
        data.shift(hanako, counter, OCT2, "12:00", "18:00");
        data.shift(jiro, kitchen, OCT2, "10:00", "15:00");
        data.shift(taro, kitchen, OCT2, "08:00", "17:00");
        data.publish(OCT2);

        DailyShiftView view = view(data.login(taro), "2026-10-02");

        assertThat(view.getDateLabel()).isEqualTo("10/2（金）");
        assertThat(view.getMessage()).isNull();
        assertThat(view.isAdmin()).isFalse();
        assertThat(view.getGroups()).extracting(DailyShiftGroup::getPositionName)
                .containsExactly("キッチン", "カウンター");
        assertThat(view.getGroups().get(0).getRows())
                .extracting(DailyShiftRow::getName, DailyShiftRow::getTimeLabel, DailyShiftRow::isMine)
                .containsExactly(tuple("山田太郎", "08:00〜17:00", true), tuple("鈴木次郎", "10:00〜15:00", false));
        assertThat(view.getGroups().get(1).getRows()).extracting(DailyShiftRow::getName)
                .containsExactly("佐藤花子");
    }

    @Test
    void 公開後に無効化したスタッフと非表示にしたポジションのシフトも表示する() throws Exception {
        data.shift(jiro, counter, OCT2, "10:00", "15:00");
        data.publish(OCT2);
        data.disable(jiro);
        data.hide(counter);

        DailyShiftView view = view(data.login(taro), "2026-10-02");

        assertThat(view.getGroups()).extracting(DailyShiftGroup::getPositionName).containsExactly("カウンター");
        assertThat(view.getGroups().get(0).getRows()).extracting(DailyShiftRow::getName).containsExactly("鈴木次郎");
    }

    @Test
    void 未公開の日のシフトは表示しない() throws Exception {
        data.shift(taro, kitchen, OCT2, "08:00", "17:00");

        MvcResult result = mvc.perform(get("/shifts").param("date", "2026-10-02").with(user(data.login(hanako))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("この日のシフトはまだ公開されていません")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("山田太郎"))))
                .andReturn();

        DailyShiftView view = (DailyShiftView) result.getModelAndView().getModel().get("view");
        assertThat(view.getGroups()).isEmpty();
    }

    @Test
    void 公開後に全員を取り消した日は出勤者がいない旨を表示する() throws Exception {
        data.publish(OCT2);

        DailyShiftView view = view(data.login(taro), "2026-10-02");

        assertThat(view.getGroups()).isEmpty();
        assertThat(view.getMessage()).isEqualTo("この日の出勤者はいません");
    }

    @Test
    void スタッフは7日前まで表示でき_それより前の日へは移動できない() throws Exception {
        data.shift(taro, kitchen, SEP18, "08:00", "17:00");
        data.publish(SEP18);

        DailyShiftView view = view(data.login(taro), "2026-09-18");

        assertThat(view.getGroups()).hasSize(1);
        assertThat(view.isPreviousVisible()).isFalse();
        assertThat(view(data.login(taro), "2026-09-19").isPreviousVisible()).isTrue();
    }

    @Test
    void スタッフには8日以上前のシフトを表示しない() throws Exception {
        data.shift(taro, kitchen, SEP17, "08:00", "17:00");
        data.publish(SEP17);

        DailyShiftView view = view(data.login(taro), "2026-09-17");

        assertThat(view.getGroups()).isEmpty();
        assertThat(view.getMessage()).isEqualTo("1週間より前のシフトは表示できません");
        assertThat(view.isPreviousVisible()).isFalse();
    }

    @Test
    void 管理者は8日以上前のシフトも表示でき転記画面へのリンクが出る() throws Exception {
        data.shift(taro, kitchen, SEP17, "08:00", "17:00");
        data.publish(SEP17);

        MvcResult result = mvc.perform(get("/shifts").param("date", "2026-09-17").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("href=\"/admin/shifts?date=2026-09-17\"")))
                .andReturn();

        DailyShiftView view = (DailyShiftView) result.getModelAndView().getModel().get("view");
        assertThat(view.getGroups()).hasSize(1);
        assertThat(view.isPreviousVisible()).isTrue();
    }

    @Test
    void 日付の指定がない_または不正なときは今日を表示する() throws Exception {
        LoginUser me = data.login(taro);

        assertThat(view(me, null).getDate()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(view(me, "2026-13-40").getDate()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(view(me, "abc").getDateLabel()).isEqualTo("9/25（金）");
    }

    @Test
    void ヘッダーから日別シフト一覧へ移動できる() throws Exception {
        mvc.perform(get("/shifts").with(user(data.login(taro))))
                .andExpect(content().string(Matchers.containsString("href=\"/shifts\"")));
    }

    private DailyShiftView view(LoginUser me, String date) throws Exception {
        MockHttpServletRequestBuilder request = get("/shifts").with(user(me));
        if (date != null) {
            request.param("date", date);
        }
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return (DailyShiftView) result.getModelAndView().getModel().get("view");
    }
}
```

- [x] **Step 3: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=TimeSlotsTest,DailyShiftTest`
Expected: FAIL（コンパイルエラー：`formatRange`・`DailyShiftView` 等が存在しない）

- [x] **Step 4: `TimeSlots.formatRange` を追加する**

`src/main/java/jp/bk/shiftmanager/util/TimeSlots.java` の `format` の下に追加する：

```java
    /** 例：09:00〜17:00 */
    public static String formatRange(LocalTime start, LocalTime end) {
        return format(start) + "〜" + format(end);
    }
```

- [x] **Step 5: DTOを作る**

`src/main/java/jp/bk/shiftmanager/dto/DailyShiftView.java`：

```java
package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 日別シフト一覧の1日分 */
@Data
public class DailyShiftView {
    private LocalDate date;
    /** 例：10/2（金） */
    private String dateLabel;
    private LocalDate previousDate;
    private String previousLabel;
    /** 前の日へ移動できるか（スタッフは1週間より前の日へ移動できない） */
    private boolean previousVisible;
    private LocalDate nextDate;
    private String nextLabel;
    /** 一覧を表示できない理由、または出勤者がいない旨（一覧を表示するときはnull） */
    private String message;
    /** ポジションの表示順。出勤者のいないポジションは含めない */
    private List<DailyShiftGroup> groups = new ArrayList<>();
    /** 管理者なら転記画面へのリンクを出す */
    private boolean admin;
}
```

`src/main/java/jp/bk/shiftmanager/dto/DailyShiftGroup.java`：

```java
package jp.bk.shiftmanager.dto;

import java.util.List;
import lombok.Data;

/** 日別シフト一覧のポジション1つ分 */
@Data
public class DailyShiftGroup {
    private String positionName;
    /** INの早い順 */
    private List<DailyShiftRow> rows;
}
```

`src/main/java/jp/bk/shiftmanager/dto/DailyShiftRow.java`：

```java
package jp.bk.shiftmanager.dto;

import lombok.Data;

/** 日別シフト一覧の1人分 */
@Data
public class DailyShiftRow {
    private String name;
    /** 例：09:00〜17:00 */
    private String timeLabel;
    /** 閲覧している本人のシフトか */
    private boolean mine;
}
```

- [x] **Step 6: `DailyShiftService` を作る**

`src/main/java/jp/bk/shiftmanager/service/DailyShiftService.java`：

```java
package jp.bk.shiftmanager.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jp.bk.shiftmanager.dto.DailyShiftGroup;
import jp.bk.shiftmanager.dto.DailyShiftRow;
import jp.bk.shiftmanager.dto.DailyShiftView;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.Shift;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.repository.PositionRepository;
import jp.bk.shiftmanager.repository.PublishedDateRepository;
import jp.bk.shiftmanager.repository.ShiftRepository;
import jp.bk.shiftmanager.repository.UserRepository;
import jp.bk.shiftmanager.util.DateLabels;
import jp.bk.shiftmanager.util.TimeSlots;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 公開済みシフトの日別一覧 */
@Service
@RequiredArgsConstructor
public class DailyShiftService {

    /** スタッフが閲覧できる過ぎた日の日数（過ぎた日は1週間後に非表示にする） */
    private static final int STAFF_PAST_DAYS = 7;

    private final Clock clock;
    private final ShiftRepository shiftRepository;
    private final PublishedDateRepository publishedDateRepository;
    private final PositionRepository positionRepository;
    private final UserRepository userRepository;

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

    /**
     * 1日分の一覧。公開済みの日だけ全員分を表示する。
     * スタッフは今日の7日前より前の日を閲覧できない（管理者は閲覧できる）
     */
    public DailyShiftView getDay(LocalDate date, long viewerId, boolean admin) {
        LocalDate oldest = LocalDate.now(clock).minusDays(STAFF_PAST_DAYS);
        DailyShiftView view = new DailyShiftView();
        view.setDate(date);
        view.setDateLabel(DateLabels.monthDayWeek(date));
        view.setPreviousDate(date.minusDays(1));
        view.setPreviousLabel(DateLabels.monthDayWeek(date.minusDays(1)));
        view.setPreviousVisible(admin || !date.minusDays(1).isBefore(oldest));
        view.setNextDate(date.plusDays(1));
        view.setNextLabel(DateLabels.monthDayWeek(date.plusDays(1)));
        view.setAdmin(admin);

        if (!admin && date.isBefore(oldest)) {
            view.setMessage("1週間より前のシフトは表示できません");
            return view;
        }
        if (!publishedDateRepository.isPublished(date)) {
            view.setMessage("この日のシフトはまだ公開されていません");
            return view;
        }

        // 公開後に無効化されたスタッフのシフトも表示するため、無効を含む全員から名前を引く
        Map<Long, String> names = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getId, User::getName));
        // INの早い順
        List<Shift> shifts = shiftRepository.findByDate(date);
        // 非表示にしたポジションのシフトも表示するため、非表示を含む全ポジションで分ける
        for (Position position : positionRepository.findAll()) {
            List<DailyShiftRow> rows = shifts.stream()
                    .filter(shift -> shift.getPositionId().equals(position.getId()))
                    .map(shift -> toRow(shift, names, viewerId))
                    .toList();
            if (!rows.isEmpty()) {
                DailyShiftGroup group = new DailyShiftGroup();
                group.setPositionName(position.getName());
                group.setRows(rows);
                view.getGroups().add(group);
            }
        }
        if (view.getGroups().isEmpty()) {
            view.setMessage("この日の出勤者はいません");
        }
        return view;
    }

    private DailyShiftRow toRow(Shift shift, Map<Long, String> names, long viewerId) {
        DailyShiftRow row = new DailyShiftRow();
        row.setName(names.get(shift.getUserId()));
        row.setTimeLabel(TimeSlots.formatRange(shift.getStartTime(), shift.getEndTime()));
        row.setMine(shift.getUserId() == viewerId);
        return row;
    }
}
```

- [x] **Step 7: `DailyShiftController` を作る**

`src/main/java/jp/bk/shiftmanager/controller/DailyShiftController.java`：

```java
package jp.bk.shiftmanager.controller;

import java.time.LocalDate;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.service.DailyShiftService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 公開済みシフトの日別一覧（スタッフ・管理者） */
@Controller
@RequestMapping("/shifts")
@RequiredArgsConstructor
public class DailyShiftController {

    private final DailyShiftService dailyShiftService;

    @GetMapping
    public String show(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String date,
            Model model) {
        LocalDate target = dailyShiftService.resolveDate(date);
        model.addAttribute("view", dailyShiftService.getDay(target, me.getId(), me.isAdmin()));
        return "shifts/day";
    }
}
```

- [x] **Step 8: 画面を作り、ヘッダーに「シフト」を追加する**

`src/main/resources/templates/shifts/day.html`：

```html
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout :: head('シフト')}"></head>
<body class="min-h-screen bg-stone-50 text-stone-900">
<header th:replace="~{layout :: header}"></header>
<main class="mx-auto max-w-5xl px-4 py-6">
  <div class="mb-4 flex items-center justify-between gap-2">
    <a th:if="${view.previousVisible}" th:href="@{/shifts(date=${view.previousDate})}"
       class="btn-secondary px-3 py-1 text-sm">&lt; <span th:text="${view.previousLabel}">10/1（木）</span></a>
    <span th:unless="${view.previousVisible}" class="w-20"></span>
    <h1 class="text-lg font-bold" th:text="${view.dateLabel}">10/2（金）</h1>
    <a th:href="@{/shifts(date=${view.nextDate})}"
       class="btn-secondary px-3 py-1 text-sm"><span th:text="${view.nextLabel}">10/3（土）</span> &gt;</a>
  </div>
  <div th:replace="~{layout :: flash}"></div>
  <p th:if="${view.message}" th:text="${view.message}" class="card text-sm text-stone-600">
    この日のシフトはまだ公開されていません</p>

  <section th:each="g : ${view.groups}" class="mb-4">
    <h2 class="mb-2 font-bold" th:text="${g.positionName}">キッチン</h2>
    <ul class="divide-y divide-stone-200 rounded-lg border border-stone-200 bg-white">
      <li th:each="r : ${g.rows}" class="flex items-center justify-between gap-2 px-4 py-3"
          th:classappend="${r.mine} ? 'bg-amber-50 font-bold'">
        <span th:text="${r.name}">山田太郎</span>
        <span class="tabular-nums" th:text="${r.timeLabel}">09:00〜17:00</span>
      </li>
    </ul>
  </section>

  <p th:if="${view.admin}" class="mt-4 text-sm">
    <a th:href="@{/admin/shifts(date=${view.date})}" class="underline">この日を転記画面で開く</a>
  </p>
</main>
</body>
</html>
```

`src/main/resources/templates/layout.html` のヘッダーで、「申請」のリンクの直前に追加する：

```html
    <a th:href="@{/shifts}" class="hover:underline">シフト</a>
```

- [x] **Step 9: テストが通ることを確認する**

Run: `./mvnw test -Dtest=TimeSlotsTest,DailyShiftTest`
Expected: PASS

- [x] **Step 10: ブラウザで動作を確認する**

`npm run build` の後、`./mvnw spring-boot:run` で起動し、次を確認する（確認できない場合はユーザーに報告して確認を依頼する）：
- ヘッダーの「シフト」から今日の一覧が開き、前後の日へ移動できる
- 公開済みの日はポジションごとにINの早い順で並び、自分の行が強調される。下書きの日は「まだ公開されていません」になる
- スマートフォン幅（375px）で横スクロールが出ない
- スタッフは7日前より前へ移動するリンクが出ない。管理者は出て、「この日を転記画面で開く」が表示される

- [x] **Step 11: 全テストを実行してコミットする**

Run: `./mvnw test`
Expected: PASS

```bash
git add -A
git commit -m "feat: 公開済みシフトの日別一覧

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [x] **Step 12: この計画ファイルのTask 1のチェックボックスをすべて `[x]` にしてコミットし、停止してユーザーに報告する**

```bash
git add docs/superpowers/plans/2026-09-28-plan4-viewing.md
git commit -m "docs: Plan 4 Task 1 完了

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: トップ画面の「変更あり」と確認済み

**Files:**
- Create: `dto/ShiftChangeRow.java`、`dto/ChangeNotice.java`、`dto/HomeView.java`、`service/HomeService.java`
- Modify: `mapper/ShiftChangeMapper.java`（`findUnacknowledgedByUser`・`acknowledge`）、`repository/ShiftChangeRepository.java`（同名メソッド）
- Modify: `controller/HomeController.java`（置き換え）、`src/main/resources/templates/home.html`（置き換え）
- Modify: `src/test/java/jp/bk/shiftmanager/TestData.java`（`change`）
- Test: `controller/HomeChangeTest.java`

**Interfaces:**
- Consumes: Task 1 の `TimeSlots.formatRange`・`GET /shifts?date=`、Plan 3 の `entity.ShiftChange`・`entity.ShiftChangeType`・`ShiftChangeMapper#findByDate`・`TestData#shift`・`#publish`・`#acknowledgeChanges`、`DateLabels.monthDayWeek`
- Produces:
  - `ShiftChangeMapper#findUnacknowledgedByUser(long userId): List<ShiftChangeRow>`・`#acknowledge(long userId, long id): int`
  - `ShiftChangeRepository#findUnacknowledgedByUser(long userId)`・`#acknowledge(long userId, long id)`
  - `HomeService#getHome(long userId): HomeView`・`#acknowledge(long userId, String changeId)`（Task 3 で `getHome` に項目を追加する）
  - `HomeView#changes: List<ChangeNotice>`（Task 3 で項目を追加する）
  - `POST /changes/acknowledge`（パラメータ `id`）→ `redirect:/`
  - `TestData#change(User, LocalDate, ShiftChangeType): long`（作成した記録のID）

- [x] **Step 1: テストデータに「変更あり」の記録を追加する**

`src/test/java/jp/bk/shiftmanager/TestData.java` に `import jp.bk.shiftmanager.entity.ShiftChangeType;` を追加し、クラス末尾に追加する：

```java
    /** 未確認の「変更あり」を記録する。記録のIDを返す */
    public long change(User user, LocalDate date, ShiftChangeType type) {
        return jdbc.queryForObject(
                "INSERT INTO shift_changes (user_id, work_date, change_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, user.getId(), date, type.name());
    }
```

- [x] **Step 2: 失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/controller/HomeChangeTest.java`：

```java
package jp.bk.shiftmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.dto.ChangeNotice;
import jp.bk.shiftmanager.dto.HomeView;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.ShiftChange;
import jp.bk.shiftmanager.entity.ShiftChangeType;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.ShiftChangeMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** トップ画面の「変更あり」（今日は2026-09-25（金）） */
class HomeChangeTest extends IntegrationTestBase {

    private static final LocalDate OCT2 = LocalDate.of(2026, 10, 2);
    private static final LocalDate OCT3 = LocalDate.of(2026, 10, 3);

    @Autowired
    ShiftChangeMapper shiftChangeMapper;

    Position kitchen;
    User taro;
    User hanako;
    LoginUser taroLogin;

    @BeforeEach
    void setUp() {
        kitchen = data.position("キッチン", 1);
        taro = data.user("taro", "山田太郎", false);
        hanako = data.user("hanako", "佐藤花子", false);
        taroLogin = data.login(taro);
    }

    @Test
    void 未確認の変更を日付順に表示し_取り消しは専用の文言を出す() throws Exception {
        data.shift(taro, kitchen, OCT3, "10:00", "17:00");
        data.publish(OCT2);
        data.publish(OCT3);
        data.change(taro, OCT3, ShiftChangeType.UPDATED);
        data.change(taro, OCT2, ShiftChangeType.CANCELLED);

        MvcResult result = mvc.perform(get("/").with(user(taroLogin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("この日のシフトは取り消されました")))
                .andExpect(content().string(Matchers.containsString("href=\"/shifts?date=2026-10-03\"")))
                .andReturn();

        HomeView view = (HomeView) result.getModelAndView().getModel().get("view");
        assertThat(view.getChanges())
                .extracting(ChangeNotice::getDateLabel, ChangeNotice::getTypeLabel, ChangeNotice::isCancelled,
                        ChangeNotice::getTimeLabel, ChangeNotice::getPositionName)
                .containsExactly(
                        tuple("10/2（金）", "取り消し", true, null, null),
                        tuple("10/3（土）", "変更", false, "10:00〜17:00", "キッチン"));
    }

    @Test
    void 追加の変更は追加として現在の時刻を表示する() throws Exception {
        data.shift(taro, kitchen, OCT2, "08:00", "12:00");
        data.publish(OCT2);
        data.change(taro, OCT2, ShiftChangeType.ADDED);

        assertThat(view().getChanges())
                .extracting(ChangeNotice::getTypeLabel, ChangeNotice::getTimeLabel)
                .containsExactly(tuple("追加", "08:00〜12:00"));
    }

    @Test
    void 確認済みの変更と他のスタッフの変更は表示しない() throws Exception {
        data.shift(taro, kitchen, OCT2, "08:00", "12:00");
        data.publish(OCT2);
        data.change(taro, OCT2, ShiftChangeType.ADDED);
        data.acknowledgeChanges(taro);
        data.change(hanako, OCT2, ShiftChangeType.CANCELLED);

        assertThat(view().getChanges()).isEmpty();
    }

    @Test
    void 確認済みを押すと表示されなくなり_記録は残る() throws Exception {
        data.shift(taro, kitchen, OCT2, "08:00", "12:00");
        data.publish(OCT2);
        long id = data.change(taro, OCT2, ShiftChangeType.ADDED);

        mvc.perform(post("/changes/acknowledge").with(user(taroLogin)).with(csrf()).param("id", String.valueOf(id)))
                .andExpect(redirectedUrl("/"));

        List<ShiftChange> changes = shiftChangeMapper.findByDate(OCT2);
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).getAcknowledgedAt()).isNotNull();
        assertThat(view().getChanges()).isEmpty();
    }

    @Test
    void 他のスタッフの変更や不正なIDを送っても何も変わらない() throws Exception {
        long hanakoChange = data.change(hanako, OCT2, ShiftChangeType.ADDED);
        data.change(taro, OCT2, ShiftChangeType.ADDED);

        for (String id : List.of(String.valueOf(hanakoChange), "abc", "", "999999")) {
            mvc.perform(post("/changes/acknowledge").with(user(taroLogin)).with(csrf()).param("id", id))
                    .andExpect(redirectedUrl("/"));
        }
        mvc.perform(post("/changes/acknowledge").with(user(taroLogin)).with(csrf()))
                .andExpect(redirectedUrl("/"));

        assertThat(shiftChangeMapper.findByDate(OCT2))
                .allSatisfy(change -> assertThat(change.getAcknowledgedAt()).isNull());
    }

    private HomeView view() throws Exception {
        MvcResult result = mvc.perform(get("/").with(user(taroLogin))).andExpect(status().isOk()).andReturn();
        return (HomeView) result.getModelAndView().getModel().get("view");
    }
}
```

- [x] **Step 3: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=HomeChangeTest`
Expected: FAIL（コンパイルエラー：`HomeView`・`ChangeNotice` が存在しない）

- [x] **Step 4: DTOを作る**

`src/main/java/jp/bk/shiftmanager/dto/ShiftChangeRow.java`：

```java
package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import jp.bk.shiftmanager.entity.ShiftChangeType;
import lombok.Data;

/** 未確認の変更と、その日の現在のシフト（シフトがなければ時刻・ポジションはnull） */
@Data
public class ShiftChangeRow {
    private Long id;
    private LocalDate workDate;
    private ShiftChangeType changeType;
    private LocalTime startTime;
    private LocalTime endTime;
    private String positionName;
}
```

`src/main/java/jp/bk/shiftmanager/dto/ChangeNotice.java`：

```java
package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import lombok.Data;

/** トップ画面の「変更あり」1件 */
@Data
public class ChangeNotice {
    private Long id;
    private LocalDate date;
    /** 例：10/2（金） */
    private String dateLabel;
    /** 追加・変更・取り消し */
    private String typeLabel;
    /** その日のシフトが取り消されたか */
    private boolean cancelled;
    /** 例：09:00〜17:00（取り消しならnull） */
    private String timeLabel;
    /** 取り消しならnull */
    private String positionName;
}
```

`src/main/java/jp/bk/shiftmanager/dto/HomeView.java`：

```java
package jp.bk.shiftmanager.dto;

import java.util.List;
import lombok.Data;

/** スタッフのトップ画面 */
@Data
public class HomeView {
    /** 未確認の「変更あり」（日付順） */
    private List<ChangeNotice> changes;
}
```

- [x] **Step 5: Mapper・Repositoryに取得と確認済みを追加する**

`src/main/java/jp/bk/shiftmanager/mapper/ShiftChangeMapper.java` に `import jp.bk.shiftmanager.dto.ShiftChangeRow;` と `import org.apache.ibatis.annotations.Update;` を追加し、クラス末尾に追加する：

```java
    /** 本人の未確認の変更（日付順）。その日の現在のシフトを付ける */
    @Select("""
            SELECT c.id, c.work_date, c.change_type, s.start_time, s.end_time, p.name AS position_name
            FROM shift_changes c
            LEFT JOIN shifts s ON s.user_id = c.user_id AND s.work_date = c.work_date
            LEFT JOIN positions p ON p.id = s.position_id
            WHERE c.user_id = #{userId} AND c.acknowledged_at IS NULL
            ORDER BY c.work_date, c.id
            """)
    List<ShiftChangeRow> findUnacknowledgedByUser(@Param("userId") long userId);

    /** 本人の未確認の変更を確認済みにする（他人の変更・確認済みの変更は更新しない） */
    @Update("""
            UPDATE shift_changes SET acknowledged_at = now()
            WHERE id = #{id} AND user_id = #{userId} AND acknowledged_at IS NULL
            """)
    int acknowledge(@Param("userId") long userId, @Param("id") long id);
```

`src/main/java/jp/bk/shiftmanager/repository/ShiftChangeRepository.java` に `import jp.bk.shiftmanager.dto.ShiftChangeRow;` を追加し、クラス末尾に追加する：

```java
    /** 本人の未確認の変更（日付順）。その日の現在のシフトを付ける */
    public List<ShiftChangeRow> findUnacknowledgedByUser(long userId) {
        return shiftChangeMapper.findUnacknowledgedByUser(userId);
    }

    /** 本人の未確認の変更を確認済みにする */
    public void acknowledge(long userId, long id) {
        shiftChangeMapper.acknowledge(userId, id);
    }
```

- [x] **Step 6: `HomeService` を作る**

`src/main/java/jp/bk/shiftmanager/service/HomeService.java`：

```java
package jp.bk.shiftmanager.service;

import jp.bk.shiftmanager.dto.ChangeNotice;
import jp.bk.shiftmanager.dto.HomeView;
import jp.bk.shiftmanager.dto.ShiftChangeRow;
import jp.bk.shiftmanager.entity.ShiftChangeType;
import jp.bk.shiftmanager.repository.ShiftChangeRepository;
import jp.bk.shiftmanager.util.DateLabels;
import jp.bk.shiftmanager.util.TimeSlots;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** スタッフのトップ画面 */
@Service
@RequiredArgsConstructor
public class HomeService {

    private final ShiftChangeRepository shiftChangeRepository;

    public HomeView getHome(long userId) {
        HomeView view = new HomeView();
        view.setChanges(shiftChangeRepository.findUnacknowledgedByUser(userId).stream()
                .map(this::toNotice)
                .toList());
        return view;
    }

    /** 本人の未確認の変更を確認済みにする。他人の変更・確認済みの変更・不正なIDなら何もしない */
    @Transactional
    public void acknowledge(long userId, String changeId) {
        long id;
        try {
            id = Long.parseLong(changeId == null ? "" : changeId.strip());
        } catch (NumberFormatException e) {
            return;
        }
        shiftChangeRepository.acknowledge(userId, id);
    }

    private ChangeNotice toNotice(ShiftChangeRow row) {
        ChangeNotice notice = new ChangeNotice();
        notice.setId(row.getId());
        notice.setDate(row.getWorkDate());
        notice.setDateLabel(DateLabels.monthDayWeek(row.getWorkDate()));
        notice.setTypeLabel(typeLabel(row.getChangeType()));
        // その日の現在のシフトがなければ取り消し（取り消しの記録は必ずシフトがない状態で作られる）
        notice.setCancelled(row.getStartTime() == null);
        if (!notice.isCancelled()) {
            notice.setTimeLabel(TimeSlots.formatRange(row.getStartTime(), row.getEndTime()));
            notice.setPositionName(row.getPositionName());
        }
        return notice;
    }

    private String typeLabel(ShiftChangeType type) {
        return switch (type) {
            case ADDED -> "追加";
            case UPDATED -> "変更";
            case CANCELLED -> "取り消し";
        };
    }
}
```

- [x] **Step 7: `HomeController` を置き換える**

`src/main/java/jp/bk/shiftmanager/controller/HomeController.java`：

```java
package jp.bk.shiftmanager.controller;

import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** スタッフのトップ画面（管理者も出勤者として同じ画面を使う） */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @GetMapping("/")
    public String home(@AuthenticationPrincipal LoginUser me, Model model) {
        model.addAttribute("view", homeService.getHome(me.getId()));
        return "home";
    }

    /** 「変更あり」を確認済みにする */
    @PostMapping("/changes/acknowledge")
    public String acknowledge(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String id) {
        homeService.acknowledge(me.getId(), id);
        return "redirect:/";
    }
}
```

- [x] **Step 8: `home.html` を置き換える**

`src/main/resources/templates/home.html`：

```html
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout :: head('トップ')}"></head>
<body class="min-h-screen bg-stone-50 text-stone-900">
<header th:replace="~{layout :: header}"></header>
<main class="mx-auto max-w-5xl space-y-6 px-4 py-6">
  <div th:replace="~{layout :: flash}"></div>

  <section th:unless="${#lists.isEmpty(view.changes)}">
    <h2 class="mb-2 font-bold text-red-700">変更あり</h2>
    <ul class="space-y-2">
      <li th:each="c : ${view.changes}" class="card flex items-center justify-between gap-3 border-red-300">
        <a th:href="@{/shifts(date=${c.date})}" class="min-w-0">
          <span class="font-bold" th:text="${c.dateLabel}">10/2（金）</span>
          <span class="ml-1 rounded bg-red-100 px-2 text-xs text-red-800" th:text="${c.typeLabel}">変更</span>
          <span th:if="${c.cancelled}" class="block text-sm">この日のシフトは取り消されました</span>
          <span th:unless="${c.cancelled}" class="block text-sm tabular-nums"
                th:text="|${c.timeLabel} ${c.positionName}|">09:00〜17:00 キッチン</span>
        </a>
        <form th:action="@{/changes/acknowledge}" method="post" class="shrink-0">
          <input type="hidden" name="id" th:value="${c.id}">
          <button class="btn-secondary px-3 py-1 text-sm">確認済み</button>
        </form>
      </li>
    </ul>
  </section>
</main>
</body>
</html>
```

- [x] **Step 9: テストが通ることを確認する**

Run: `./mvnw test -Dtest=HomeChangeTest,UserStateCheckFilterTest,LoginTest`
Expected: PASS（`UserStateCheckFilterTest`・`LoginTest` は `/` を開くため、`@AuthenticationPrincipal LoginUser` が取れることの確認を兼ねる）

- [x] **Step 10: ブラウザで動作を確認する**

`npm run build` の後、`./mvnw spring-boot:run` で起動し、次を確認する（確認できない場合はユーザーに報告して確認を依頼する）：
- 管理者で公開済みの日のスタッフの時刻を変更・取り消しして登録し、そのスタッフでログインするとトップに「変更あり」が出る。取り消しは「この日のシフトは取り消されました」
- 「確認済み」を押すと消える。カードを押すとその日の日別一覧が開く
- スマートフォン幅（375px）で「確認済み」ボタンが折り返さず押せる

- [x] **Step 11: 全テストを実行してコミットする**

Run: `./mvnw test`
Expected: PASS

```bash
git add -A
git commit -m "feat: トップ画面の「変更あり」と確認済み

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [x] **Step 12: この計画ファイルのTask 2のチェックボックスをすべて `[x]` にしてコミットし、停止してユーザーに報告する**

```bash
git add docs/superpowers/plans/2026-09-28-plan4-viewing.md
git commit -m "docs: Plan 4 Task 2 完了

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: トップ画面の次回の出勤・カレンダー・締切案内・予定時間

> 2026-09-28 ユーザー要望により「直近の出勤一覧」を月カレンダーに置き換えた（仕様の解釈を参照）

**Files:**
- Create: `dto/MyShiftRow.java`、`dto/MyShiftView.java`、`dto/CalendarDay.java`、`dto/CalendarView.java`、`dto/DeadlineNotice.java`
- Modify: `mapper/ShiftMapper.java`（`findPublishedByUser`）、`repository/ShiftRepository.java`（同名メソッド）
- Modify: `mapper/PublishedDateMapper.java`（`findBetween`）、`repository/PublishedDateRepository.java`（`findDates`）
- Modify: `dto/HomeView.java`（項目追加）、`service/HomeService.java`（`getHome` の引数と項目を追加）、`controller/HomeController.java`（`month` パラメータ）、`src/main/resources/templates/home.html`（セクション追加）
- Test: `controller/HomeTest.java`

**Interfaces:**
- Consumes: Task 2 の `HomeService#getHome`・`HomeView#changes`・`ShiftChangeRepository#findUnacknowledgedByUser`・`home.html`、Task 1 の `TimeSlots.formatRange`・`GET /shifts?date=`、`TimeSlots.format`、`util.Cycle`（`of`・`next`・`isOpen`・`deadline`・`label`）、`AppSettingRepository#getDeadlineDaysBefore()`、`ShiftRequestRepository#findByUserAndPeriod(long, LocalDate, LocalDate)`、`CycleUnavailableRepository#findStarts(long, LocalDate, LocalDate)`、`GET /requests?month=yyyy-MM`、`TestData#shift`・`#publish`・`#request`・`#unavailable`・`#today`・`#change`
- Produces:
  - `ShiftMapper#findPublishedByUser(long userId, LocalDate from, LocalDate to): List<MyShiftRow>`、`ShiftRepository#findPublishedByUser`（同じ）
  - `PublishedDateMapper#findBetween(LocalDate from, LocalDate to): List<LocalDate>`、`PublishedDateRepository#findDates`（同じ）
  - `HomeService#getHome(long userId, boolean admin, String month): HomeView`（Task 2 の `getHome(long)` を置き換える）
  - `GET /?month=yyyy-MM`（指定がない・不正なら今月）
  - `HomeView` に `nextShift: MyShiftView`（なければnull）・`calendar: CalendarView`・`deadline: DeadlineNotice`・`thisMonthLabel`・`thisMonthHours`・`nextMonthLabel`・`nextMonthHours`

- [x] **Step 1: 失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/controller/HomeTest.java`：

```java
package jp.bk.shiftmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.dto.CalendarDay;
import jp.bk.shiftmanager.dto.CalendarView;
import jp.bk.shiftmanager.dto.DeadlineNotice;
import jp.bk.shiftmanager.dto.HomeView;
import jp.bk.shiftmanager.dto.MyShiftView;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.ShiftChangeType;
import jp.bk.shiftmanager.entity.User;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** トップ画面の次回の出勤・カレンダー・締切案内・予定時間（今日は2026-09-25（金）、締切は5日前） */
class HomeTest extends IntegrationTestBase {

    Position kitchen;
    User taro;
    User hanako;
    User boss;
    LoginUser taroLogin;
    LoginUser bossLogin;

    @BeforeEach
    void setUp() {
        kitchen = data.position("キッチン", 1);
        taro = data.user("taro", "山田太郎", false);
        hanako = data.user("hanako", "佐藤花子", false);
        boss = data.user("boss", "店長", true);
        taroLogin = data.login(taro);
        bossLogin = data.login(boss);
    }

    // ---- 次回の出勤 ----

    @Test
    void 次回の出勤は今日以降で最も早い公開済みシフト() throws Exception {
        published(taro, "2026-09-24", "09:00", "17:00");   // 過ぎた日
        published(taro, "2026-09-25", "17:00", "22:00");   // 今日
        data.shift(taro, kitchen, LocalDate.parse("2026-09-27"), "09:00", "17:00"); // 下書き
        published(taro, "2026-09-28", "09:00", "13:00");
        published(hanako, "2026-09-26", "09:00", "17:00"); // 他のスタッフ

        MyShiftView next = view().getNextShift();

        assertThat(next.getDateLabel()).isEqualTo("9/25（金）");
        assertThat(next.isToday()).isTrue();
        assertThat(next.getTimeLabel()).isEqualTo("17:00〜22:00");
        assertThat(next.getPositionName()).isEqualTo("キッチン");
    }

    @Test
    void 次回の出勤は来月のシフトからも探す() throws Exception {
        published(taro, "2026-10-20", "10:00", "15:00");

        MyShiftView next = view().getNextShift();

        assertThat(next.getDateLabel()).isEqualTo("10/20（火）");
        assertThat(next.isToday()).isFalse();
    }

    @Test
    void 公開済みの次回の出勤がなければその旨を表示する() throws Exception {
        data.shift(taro, kitchen, LocalDate.parse("2026-09-26"), "09:00", "17:00"); // 下書き

        MvcResult result = mvc.perform(get("/").with(user(taroLogin)))
                .andExpect(content().string(Matchers.containsString("公開されている次回の出勤はありません")))
                .andReturn();

        assertThat(((HomeView) result.getModelAndView().getModel().get("view")).getNextShift()).isNull();
    }

    // ---- カレンダー ----

    @Test
    void カレンダーは日曜始まりで今月を表示し_前後の月の日は月外とする() throws Exception {
        CalendarView calendar = view().getCalendar();

        assertThat(calendar.getMonthLabel()).isEqualTo("2026年9月");
        // 8/30（日）〜10/3（土）の5週
        assertThat(calendar.getWeeks()).hasSize(5).allSatisfy(week -> assertThat(week).hasSize(7));
        CalendarDay first = calendar.getWeeks().get(0).get(0);
        assertThat(first.getDate()).isEqualTo(LocalDate.parse("2026-08-30"));
        assertThat(first.isInMonth()).isFalse();
        assertThat(calendar.getWeeks().get(0).get(2).getDate()).isEqualTo(LocalDate.parse("2026-09-01"));
        assertThat(calendar.getWeeks().get(4).get(6).getDate()).isEqualTo(LocalDate.parse("2026-10-03"));
        assertThat(day(calendar, "2026-09-25").isToday()).isTrue();
        assertThat(day(calendar, "2026-09-25").getDay()).isEqualTo(25);
    }

    @Test
    void 自分の公開済みシフトの日はINを表示し_下書きの日は押せない() throws Exception {
        published(taro, "2026-09-28", "11:00", "17:00");
        data.shift(taro, kitchen, LocalDate.parse("2026-09-27"), "09:00", "17:00"); // 下書き
        published(hanako, "2026-09-26", "09:00", "17:00"); // 他のスタッフだけの公開済みの日

        MvcResult result = mvc.perform(get("/").with(user(taroLogin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("href=\"/shifts?date=2026-09-28\"")))
                .andExpect(content().string(Matchers.containsString("href=\"/shifts?date=2026-09-26\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("/shifts?date=2026-09-27"))))
                .andReturn();

        CalendarView calendar = ((HomeView) result.getModelAndView().getModel().get("view")).getCalendar();
        assertThat(day(calendar, "2026-09-28").getStartLabel()).isEqualTo("11:00");
        assertThat(day(calendar, "2026-09-28").isLinkable()).isTrue();
        assertThat(day(calendar, "2026-09-27").getStartLabel()).isNull();
        assertThat(day(calendar, "2026-09-27").isLinkable()).isFalse();
        assertThat(day(calendar, "2026-09-26").getStartLabel()).isNull();
        assertThat(day(calendar, "2026-09-26").isLinkable()).isTrue();
    }

    @Test
    void スタッフは7日前より前の日を押せず_INも表示しない() throws Exception {
        published(taro, "2026-09-17", "09:00", "17:00");   // 8日前
        published(taro, "2026-09-18", "10:00", "17:00");   // 7日前

        CalendarView calendar = view().getCalendar();

        assertThat(day(calendar, "2026-09-17").isLinkable()).isFalse();
        assertThat(day(calendar, "2026-09-17").getStartLabel()).isNull();
        assertThat(day(calendar, "2026-09-18").isLinkable()).isTrue();
        assertThat(day(calendar, "2026-09-18").getStartLabel()).isEqualTo("10:00");
        // 先月（8月）は全日が7日前より前のため、前の月へは移動できない
        assertThat(calendar.isPreviousVisible()).isFalse();
    }

    @Test
    void 管理者は過去の日も押せて前の月へ移動できる() throws Exception {
        LocalDate sep1 = LocalDate.parse("2026-09-01");
        data.shift(boss, kitchen, sep1, "08:00", "12:00");
        data.publish(sep1);

        CalendarView calendar = view(bossLogin, "/").getCalendar();

        assertThat(day(calendar, "2026-09-01").isLinkable()).isTrue();
        assertThat(day(calendar, "2026-09-01").getStartLabel()).isEqualTo("08:00");
        assertThat(calendar.isPreviousVisible()).isTrue();
        assertThat(calendar.getPreviousMonth()).isEqualTo(YearMonth.of(2026, 8));
    }

    @Test
    void 月を指定して表示でき_不正な指定は今月を表示する() throws Exception {
        published(taro, "2026-10-31", "08:00", "12:00");

        CalendarView october = view(taroLogin, "/?month=2026-10").getCalendar();
        assertThat(october.getMonthLabel()).isEqualTo("2026年10月");
        assertThat(day(october, "2026-10-31").getStartLabel()).isEqualTo("08:00");
        assertThat(october.isPreviousVisible()).isTrue();
        assertThat(october.getPreviousMonth()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(october.getNextMonth()).isEqualTo(YearMonth.of(2026, 11));

        for (String month : List.of("abc", "2026-13", "")) {
            assertThat(view(taroLogin, "/?month=" + month).getCalendar().getMonthLabel()).isEqualTo("2026年9月");
        }
    }

    @Test
    void 未確認の変更がある日に印を付ける() throws Exception {
        published(taro, "2026-09-28", "09:00", "13:00");
        data.publish(LocalDate.parse("2026-09-29"));
        data.change(taro, LocalDate.parse("2026-09-28"), ShiftChangeType.UPDATED);
        data.change(taro, LocalDate.parse("2026-09-29"), ShiftChangeType.CANCELLED);

        CalendarView calendar = view().getCalendar();

        assertThat(day(calendar, "2026-09-28").isChanged()).isTrue();
        assertThat(day(calendar, "2026-09-29").isChanged()).isTrue();
        assertThat(day(calendar, "2026-09-29").getStartLabel()).isNull();
        assertThat(day(calendar, "2026-09-30").isChanged()).isFalse();
    }

    // ---- 予定時間 ----

    @Test
    void 予定時間は今月と来月の公開済みシフトのOUTからINを引いた合計() throws Exception {
        published(taro, "2026-08-31", "08:00", "23:00");   // 先月
        published(taro, "2026-09-01", "09:00", "17:30");   // 8時間30分
        published(taro, "2026-09-30", "17:00", "22:00");   // 5時間
        data.shift(taro, kitchen, LocalDate.parse("2026-09-29"), "09:00", "23:00"); // 下書き
        published(taro, "2026-10-31", "08:00", "12:00");   // 4時間
        published(taro, "2026-11-01", "08:00", "12:00");   // 翌々月
        published(hanako, "2026-10-01", "08:00", "23:00"); // 他のスタッフ

        HomeView view = view();

        assertThat(view.getThisMonthLabel()).isEqualTo("9月");
        assertThat(view.getThisMonthHours()).isEqualTo("13時間30分");
        assertThat(view.getNextMonthLabel()).isEqualTo("10月");
        assertThat(view.getNextMonthHours()).isEqualTo("4時間");
    }

    @Test
    void シフトがない月の予定時間は0時間() throws Exception {
        HomeView view = view();

        assertThat(view.getThisMonthHours()).isEqualTo("0時間");
        assertThat(view.getNextMonthHours()).isEqualTo("0時間");
    }

    @Test
    void カレンダーの月を変えても予定時間は今月と来月のまま() throws Exception {
        assertThat(view(taroLogin, "/?month=2026-12").getThisMonthLabel()).isEqualTo("9月");
    }

    // ---- 締切案内 ----

    @Test
    void 締切前で最も近いサイクルを案内し_未提出なら強調する() throws Exception {
        // 次のサイクル（10/11〜）の申請だけでは、10/1〜10/10は未提出
        data.request(taro, LocalDate.parse("2026-10-11"), "09:00", "17:00", null);

        MvcResult result = mvc.perform(get("/").with(user(taroLogin)))
                .andExpect(content().string(Matchers.containsString("未提出です")))
                .andExpect(content().string(Matchers.containsString("href=\"/requests?month=2026-10\"")))
                .andReturn();

        DeadlineNotice deadline = ((HomeView) result.getModelAndView().getModel().get("view")).getDeadline();
        assertThat(deadline.getCycleLabel()).isEqualTo("10/1〜10/10");
        assertThat(deadline.getDeadlineLabel()).isEqualTo("9/26（土）");
        assertThat(deadline.isSubmitted()).isFalse();
        assertThat(deadline.getMonth()).isEqualTo(YearMonth.of(2026, 10));
    }

    @Test
    void サイクル内に申請が1日以上あれば提出済み() throws Exception {
        data.request(taro, LocalDate.parse("2026-10-10"), "09:00", "17:00", null);

        assertThat(view().getDeadline().isSubmitted()).isTrue();
    }

    @Test
    void 出勤できないにチェックがあれば提出済み() throws Exception {
        data.unavailable(taro, LocalDate.parse("2026-10-01"));

        assertThat(view().getDeadline().isSubmitted()).isTrue();
    }

    @Test
    void 今日が締切日ならそのサイクルを_翌日なら次のサイクルを案内する() throws Exception {
        data.today(LocalDate.parse("2026-09-26"));
        assertThat(view().getDeadline().getCycleLabel()).isEqualTo("10/1〜10/10");

        data.today(LocalDate.parse("2026-09-27"));
        DeadlineNotice deadline = view().getDeadline();
        assertThat(deadline.getCycleLabel()).isEqualTo("10/11〜10/20");
        assertThat(deadline.getDeadlineLabel()).isEqualTo("10/6（火）");
    }

    /** 公開済みのシフトを登録する（同じ日を2回公開しないよう、1日1件で使う） */
    private void published(User user, String date, String start, String end) {
        LocalDate day = LocalDate.parse(date);
        data.shift(user, kitchen, day, start, end);
        data.publish(day);
    }

    /** カレンダーからその日のマスを取り出す */
    private CalendarDay day(CalendarView calendar, String date) {
        LocalDate target = LocalDate.parse(date);
        return calendar.getWeeks().stream()
                .flatMap(List::stream)
                .filter(day -> day.getDate().equals(target))
                .findFirst()
                .orElseThrow();
    }

    private HomeView view() throws Exception {
        return view(taroLogin, "/");
    }

    private HomeView view(LoginUser login, String url) throws Exception {
        MvcResult result = mvc.perform(get(url).with(user(login))).andExpect(status().isOk()).andReturn();
        return (HomeView) result.getModelAndView().getModel().get("view");
    }
}
```

- [x] **Step 2: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=HomeTest`
Expected: FAIL（コンパイルエラー：`MyShiftView`・`CalendarView`・`CalendarDay`・`DeadlineNotice`・`HomeView#getNextShift` 等が存在しない）

- [x] **Step 3: DTOを作り、`HomeView` に項目を追加する**

`src/main/java/jp/bk/shiftmanager/dto/MyShiftRow.java`：

```java
package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;

/** 本人の公開済みシフト（トップ画面の取得用） */
@Data
public class MyShiftRow {
    private LocalDate workDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String positionName;
}
```

`src/main/java/jp/bk/shiftmanager/dto/MyShiftView.java`：

```java
package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import lombok.Data;

/** トップ画面の次回の出勤 */
@Data
public class MyShiftView {
    private LocalDate date;
    /** 例：9/25（金） */
    private String dateLabel;
    /** 例：09:00〜17:00 */
    private String timeLabel;
    private String positionName;
    /** 今日のシフトか */
    private boolean today;
}
```

`src/main/java/jp/bk/shiftmanager/dto/CalendarDay.java`：

```java
package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import lombok.Data;

/** トップ画面のカレンダーの1マス */
@Data
public class CalendarDay {
    private LocalDate date;
    /** 日（例：25） */
    private int day;
    /** 表示している月の日か（前後の月の日は空欄にする） */
    private boolean inMonth;
    private boolean today;
    /** 日別一覧へ移動できるか（公開済みの日。スタッフは7日前以降のみ） */
    private boolean linkable;
    /** 本人のIN（例：11:00）。本人のシフトがない・移動できない日はnull */
    private String startLabel;
    /** 本人の未確認の変更がある日か */
    private boolean changed;
}
```

`src/main/java/jp/bk/shiftmanager/dto/CalendarView.java`：

```java
package jp.bk.shiftmanager.dto;

import java.time.YearMonth;
import java.util.List;
import lombok.Data;

/** トップ画面の月カレンダー（日曜始まり） */
@Data
public class CalendarView {
    /** 例：2026年9月 */
    private String monthLabel;
    /** 週ごとの7マス */
    private List<List<CalendarDay>> weeks;
    private YearMonth previousMonth;
    /** 前の月へ移動できるか（スタッフは前の月がすべて7日前より前なら移動できない） */
    private boolean previousVisible;
    private YearMonth nextMonth;
}
```

`src/main/java/jp/bk/shiftmanager/dto/DeadlineNotice.java`：

```java
package jp.bk.shiftmanager.dto;

import java.time.YearMonth;
import lombok.Data;

/** トップ画面の申請締切の案内 */
@Data
public class DeadlineNotice {
    /** 例：10/1〜10/10 */
    private String cycleLabel;
    /** 例：9/26（土） */
    private String deadlineLabel;
    private boolean submitted;
    /** 申請画面で開く月（サイクルの開始日の月） */
    private YearMonth month;
}
```

`src/main/java/jp/bk/shiftmanager/dto/HomeView.java` を置き換える：

```java
package jp.bk.shiftmanager.dto;

import java.util.List;
import lombok.Data;

/** スタッフのトップ画面 */
@Data
public class HomeView {
    /** 未確認の「変更あり」（日付順） */
    private List<ChangeNotice> changes;
    /** 今日以降で最も早い公開済みシフト（来月末まで。なければnull） */
    private MyShiftView nextShift;
    /** 月カレンダー */
    private CalendarView calendar;
    /** 締切前で最も近いサイクルの案内 */
    private DeadlineNotice deadline;
    /** 例：9月 */
    private String thisMonthLabel;
    /** 例：13時間30分 */
    private String thisMonthHours;
    private String nextMonthLabel;
    private String nextMonthHours;
}
```

- [x] **Step 4: Mapper・Repositoryに本人の公開済みシフトと公開日の取得を追加する**

`src/main/java/jp/bk/shiftmanager/mapper/ShiftMapper.java` に `import jp.bk.shiftmanager.dto.MyShiftRow;` を追加し、クラス末尾に追加する：

```java
    /** 本人の公開済みシフト（日付順） */
    @Select("""
            SELECT s.work_date, s.start_time, s.end_time, p.name AS position_name
            FROM shifts s
            JOIN published_dates d ON d.work_date = s.work_date
            JOIN positions p ON p.id = s.position_id
            WHERE s.user_id = #{userId} AND s.work_date BETWEEN #{from} AND #{to}
            ORDER BY s.work_date
            """)
    List<MyShiftRow> findPublishedByUser(@Param("userId") long userId, @Param("from") LocalDate from,
            @Param("to") LocalDate to);
```

`src/main/java/jp/bk/shiftmanager/repository/ShiftRepository.java` に `import jp.bk.shiftmanager.dto.MyShiftRow;` を追加し、クラス末尾に追加する：

```java
    /** 本人の公開済みシフト（日付順） */
    public List<MyShiftRow> findPublishedByUser(long userId, LocalDate from, LocalDate to) {
        return shiftMapper.findPublishedByUser(userId, from, to);
    }
```

`src/main/java/jp/bk/shiftmanager/mapper/PublishedDateMapper.java` に `import java.util.List;` を追加し、クラス末尾に追加する：

```java
    /** 期間内の公開済みの日（日付順） */
    @Select("SELECT work_date FROM published_dates WHERE work_date BETWEEN #{from} AND #{to} ORDER BY work_date")
    List<LocalDate> findBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
```

`src/main/java/jp/bk/shiftmanager/repository/PublishedDateRepository.java` に `import java.util.List;` を追加し、クラス末尾に追加する：

```java
    /** 期間内の公開済みの日（日付順） */
    public List<LocalDate> findDates(LocalDate from, LocalDate to) {
        return publishedDateMapper.findBetween(from, to);
    }
```

- [x] **Step 5: `HomeService` に次回の出勤・カレンダー・締切案内・予定時間を追加する**

`src/main/java/jp/bk/shiftmanager/service/HomeService.java` を置き換える（`acknowledge`・`toNotice`・`typeLabel` は Task 2 のまま）：

```java
package jp.bk.shiftmanager.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jp.bk.shiftmanager.dto.CalendarDay;
import jp.bk.shiftmanager.dto.CalendarView;
import jp.bk.shiftmanager.dto.ChangeNotice;
import jp.bk.shiftmanager.dto.DeadlineNotice;
import jp.bk.shiftmanager.dto.HomeView;
import jp.bk.shiftmanager.dto.MyShiftRow;
import jp.bk.shiftmanager.dto.MyShiftView;
import jp.bk.shiftmanager.dto.ShiftChangeRow;
import jp.bk.shiftmanager.entity.ShiftChangeType;
import jp.bk.shiftmanager.repository.AppSettingRepository;
import jp.bk.shiftmanager.repository.CycleUnavailableRepository;
import jp.bk.shiftmanager.repository.PublishedDateRepository;
import jp.bk.shiftmanager.repository.ShiftChangeRepository;
import jp.bk.shiftmanager.repository.ShiftRepository;
import jp.bk.shiftmanager.repository.ShiftRequestRepository;
import jp.bk.shiftmanager.util.Cycle;
import jp.bk.shiftmanager.util.DateLabels;
import jp.bk.shiftmanager.util.TimeSlots;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** スタッフのトップ画面 */
@Service
@RequiredArgsConstructor
public class HomeService {

    /** スタッフが閲覧できる過去の日数（日別一覧と同じ） */
    private static final int STAFF_PAST_DAYS = 7;

    private final Clock clock;
    private final ShiftChangeRepository shiftChangeRepository;
    private final ShiftRepository shiftRepository;
    private final PublishedDateRepository publishedDateRepository;
    private final AppSettingRepository appSettingRepository;
    private final ShiftRequestRepository shiftRequestRepository;
    private final CycleUnavailableRepository cycleUnavailableRepository;

    /**
     * トップ画面。
     * @param month カレンダーに表示する月（yyyy-MM）。指定がない・不正なら今月
     */
    public HomeView getHome(long userId, boolean admin, String month) {
        LocalDate today = LocalDate.now(clock);
        YearMonth thisMonth = YearMonth.from(today);
        YearMonth nextMonth = thisMonth.plusMonths(1);
        // 今月1日〜来月末の公開済みシフト（日付順）。次回の出勤と予定時間に使う
        List<MyShiftRow> shifts = shiftRepository.findPublishedByUser(
                userId, thisMonth.atDay(1), nextMonth.atEndOfMonth());
        List<ShiftChangeRow> changes = shiftChangeRepository.findUnacknowledgedByUser(userId);

        HomeView view = new HomeView();
        view.setChanges(changes.stream().map(this::toNotice).toList());
        view.setNextShift(shifts.stream()
                .filter(shift -> !shift.getWorkDate().isBefore(today))
                .findFirst()
                .map(shift -> toMyShift(shift, today))
                .orElse(null));
        view.setCalendar(calendar(userId, admin, resolveMonth(month, thisMonth), today, changes));
        view.setDeadline(deadline(userId, today));
        view.setThisMonthLabel(thisMonth.getMonthValue() + "月");
        view.setThisMonthHours(hoursLabel(minutes(shifts, thisMonth)));
        view.setNextMonthLabel(nextMonth.getMonthValue() + "月");
        view.setNextMonthHours(hoursLabel(minutes(shifts, nextMonth)));
        return view;
    }

    /** 本人の未確認の変更を確認済みにする。他人の変更・確認済みの変更・不正なIDなら何もしない */
    @Transactional
    public void acknowledge(long userId, String changeId) {
        long id;
        try {
            id = Long.parseLong(changeId == null ? "" : changeId.strip());
        } catch (NumberFormatException e) {
            return;
        }
        shiftChangeRepository.acknowledge(userId, id);
    }

    /** 画面から指定された月。指定がない・不正なら今月 */
    private YearMonth resolveMonth(String text, YearMonth thisMonth) {
        if (text != null) {
            try {
                return YearMonth.parse(text);
            } catch (DateTimeParseException e) {
                // 今月を表示する
            }
        }
        return thisMonth;
    }

    /**
     * 月カレンダー（日曜始まり）。公開済みの日は日別一覧へ移動でき、本人のシフトがあればINを表示する。
     * スタッフは今日の7日前より前の日を移動できず、INも表示しない（管理者は表示する）
     */
    private CalendarView calendar(long userId, boolean admin, YearMonth month, LocalDate today,
            List<ShiftChangeRow> changes) {
        LocalDate oldest = today.minusDays(STAFF_PAST_DAYS);
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        Set<LocalDate> published = new HashSet<>(publishedDateRepository.findDates(first, last));
        // 本人のシフトは1日1件（shifts_date_user_key）
        Map<LocalDate, LocalTime> starts = shiftRepository.findPublishedByUser(userId, first, last).stream()
                .collect(Collectors.toMap(MyShiftRow::getWorkDate, MyShiftRow::getStartTime));
        Set<LocalDate> changed = changes.stream().map(ShiftChangeRow::getWorkDate).collect(Collectors.toSet());

        List<List<CalendarDay>> weeks = new ArrayList<>();
        // 月初を含む週の日曜日から、月末を含む週の土曜日まで（getValue は月曜=1〜日曜=7）
        LocalDate sunday = first.minusDays(first.getDayOfWeek().getValue() % 7);
        for (LocalDate weekStart = sunday; !weekStart.isAfter(last); weekStart = weekStart.plusWeeks(1)) {
            List<CalendarDay> week = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                LocalDate date = weekStart.plusDays(i);
                CalendarDay day = new CalendarDay();
                day.setDate(date);
                day.setDay(date.getDayOfMonth());
                day.setInMonth(YearMonth.from(date).equals(month));
                day.setToday(date.equals(today));
                if (day.isInMonth()) {
                    day.setLinkable(published.contains(date) && (admin || !date.isBefore(oldest)));
                    if (day.isLinkable() && starts.containsKey(date)) {
                        day.setStartLabel(TimeSlots.format(starts.get(date)));
                    }
                    day.setChanged(changed.contains(date));
                }
                week.add(day);
            }
            weeks.add(week);
        }

        CalendarView calendar = new CalendarView();
        calendar.setMonthLabel(month.getYear() + "年" + month.getMonthValue() + "月");
        calendar.setWeeks(weeks);
        calendar.setPreviousMonth(month.minusMonths(1));
        calendar.setPreviousVisible(admin || !month.minusMonths(1).atEndOfMonth().isBefore(oldest));
        calendar.setNextMonth(month.plusMonths(1));
        return calendar;
    }

    /**
     * 締切前（今日が締切日以前）で最も近いサイクルの案内。
     * 提出済みの判定は申請画面と同じ（サイクル内に申請が1日以上、または「この期間は出勤できない」にチェック）
     */
    private DeadlineNotice deadline(long userId, LocalDate today) {
        int daysBefore = appSettingRepository.getDeadlineDaysBefore();
        Cycle cycle = Cycle.of(today);
        while (!cycle.isOpen(today, daysBefore)) {
            cycle = cycle.next();
        }
        boolean submitted = !shiftRequestRepository.findByUserAndPeriod(userId, cycle.start(), cycle.end()).isEmpty()
                || !cycleUnavailableRepository.findStarts(userId, cycle.start(), cycle.start()).isEmpty();

        DeadlineNotice notice = new DeadlineNotice();
        notice.setCycleLabel(cycle.label());
        notice.setDeadlineLabel(DateLabels.monthDayWeek(cycle.deadline(daysBefore)));
        notice.setSubmitted(submitted);
        notice.setMonth(YearMonth.from(cycle.start()));
        return notice;
    }

    /** その月のシフトの OUT − IN の合計（分） */
    private long minutes(List<MyShiftRow> shifts, YearMonth month) {
        return shifts.stream()
                .filter(shift -> YearMonth.from(shift.getWorkDate()).equals(month))
                .mapToLong(shift -> Duration.between(shift.getStartTime(), shift.getEndTime()).toMinutes())
                .sum();
    }

    /** 例：13時間30分、4時間、0時間 */
    private String hoursLabel(long minutes) {
        long hours = minutes / 60;
        long rest = minutes % 60;
        return rest == 0 ? hours + "時間" : hours + "時間" + rest + "分";
    }

    private MyShiftView toMyShift(MyShiftRow row, LocalDate today) {
        MyShiftView view = new MyShiftView();
        view.setDate(row.getWorkDate());
        view.setDateLabel(DateLabels.monthDayWeek(row.getWorkDate()));
        view.setTimeLabel(TimeSlots.formatRange(row.getStartTime(), row.getEndTime()));
        view.setPositionName(row.getPositionName());
        view.setToday(row.getWorkDate().equals(today));
        return view;
    }

    private ChangeNotice toNotice(ShiftChangeRow row) {
        ChangeNotice notice = new ChangeNotice();
        notice.setId(row.getId());
        notice.setDate(row.getWorkDate());
        notice.setDateLabel(DateLabels.monthDayWeek(row.getWorkDate()));
        notice.setTypeLabel(typeLabel(row.getChangeType()));
        // その日の現在のシフトがなければ取り消し（取り消しの記録は必ずシフトがない状態で作られる）
        notice.setCancelled(row.getStartTime() == null);
        if (!notice.isCancelled()) {
            notice.setTimeLabel(TimeSlots.formatRange(row.getStartTime(), row.getEndTime()));
            notice.setPositionName(row.getPositionName());
        }
        return notice;
    }

    private String typeLabel(ShiftChangeType type) {
        return switch (type) {
            case ADDED -> "追加";
            case UPDATED -> "変更";
            case CANCELLED -> "取り消し";
        };
    }
}
```

- [x] **Step 6: `HomeController` に `month` パラメータを追加する**

`src/main/java/jp/bk/shiftmanager/controller/HomeController.java` の `home` を置き換える：

```java
    /** トップ画面。month（yyyy-MM）でカレンダーの月を指定する */
    @GetMapping("/")
    public String home(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String month,
            Model model) {
        model.addAttribute("view", homeService.getHome(me.getId(), me.isAdmin(), month));
        return "home";
    }
```

- [x] **Step 7: `home.html` にセクションを追加する**

`src/main/resources/templates/home.html` の「変更あり」の `</section>` の後（`</main>` の前）に追加する。並びは 変更あり → 次回の出勤 → カレンダー → 申請の締切 → 勤務予定時間。
カレンダーは幅375pxでも7列が収まるよう、曜日は見出し行に出し、マスには日とINだけを出す。本人のシフトがある日は日別一覧の自分の行と同じ `bg-amber-50` で強調する。

```html
  <section>
    <h2 class="mb-2 font-bold">次回の出勤</h2>
    <a th:if="${view.nextShift}" th:href="@{/shifts(date=${view.nextShift.date})}" class="card block">
      <span class="font-bold" th:text="${view.nextShift.dateLabel}">9/25（金）</span>
      <span th:if="${view.nextShift.today}" class="ml-1 rounded bg-amber-100 px-2 text-xs text-amber-800">今日</span>
      <span class="block text-2xl font-bold tabular-nums" th:text="${view.nextShift.timeLabel}">09:00〜17:00</span>
      <span class="block text-sm text-stone-600" th:text="${view.nextShift.positionName}">キッチン</span>
    </a>
    <p th:unless="${view.nextShift}" class="card text-sm text-stone-600">公開されている次回の出勤はありません</p>
  </section>

  <section th:with="cal=${view.calendar}">
    <div class="mb-2 flex items-center justify-between gap-2">
      <a th:if="${cal.previousVisible}" th:href="@{/(month=${cal.previousMonth})}"
         class="btn-secondary px-3 py-1 text-sm">&lt; 前の月</a>
      <span th:unless="${cal.previousVisible}" class="w-20"></span>
      <h2 class="font-bold" th:text="${cal.monthLabel}">2026年9月</h2>
      <a th:href="@{/(month=${cal.nextMonth})}" class="btn-secondary px-3 py-1 text-sm">次の月 &gt;</a>
    </div>
    <div class="grid grid-cols-7 gap-1 text-center text-xs">
      <span class="text-red-700">日</span><span>月</span><span>火</span><span>水</span><span>木</span><span>金</span>
      <span class="text-blue-700">土</span>
    </div>
    <div th:each="week : ${cal.weeks}" class="mt-1 grid grid-cols-7 gap-1 text-center">
      <th:block th:each="d : ${week}">
        <span th:unless="${d.inMonth}" class="min-h-14"></span>
        <a th:if="${d.inMonth and d.linkable}" th:href="@{/shifts(date=${d.date})}"
           class="relative flex min-h-14 flex-col items-center justify-center rounded border"
           th:classappend="|${d.startLabel != null ? 'border-amber-400 bg-amber-50 font-bold' : 'border-stone-200 bg-white'} ${d.today ? 'ring-2 ring-stone-700' : ''}|">
          <span class="text-sm" th:text="${d.day}">11</span>
          <span class="text-xs tabular-nums" th:text="${d.startLabel}">11:00</span>
          <span th:if="${d.changed}" class="absolute top-1 right-1 h-2 w-2 rounded-full bg-red-500"></span>
        </a>
        <span th:if="${d.inMonth and !d.linkable}"
              class="relative flex min-h-14 flex-col items-center justify-center rounded border border-stone-100 bg-stone-100 text-stone-400"
              th:classappend="${d.today} ? 'ring-2 ring-stone-700'">
          <span class="text-sm" th:text="${d.day}">12</span>
          <span th:if="${d.changed}" class="absolute top-1 right-1 h-2 w-2 rounded-full bg-red-500"></span>
        </span>
      </th:block>
    </div>
    <p class="mt-1 text-xs text-stone-500">色付きの日は自分の出勤日（INの時刻）です。灰色の日はまだ公開されていません</p>
  </section>

  <section>
    <h2 class="mb-2 font-bold">申請の締切</h2>
    <a th:href="@{/requests(month=${view.deadline.month})}" class="card block"
       th:classappend="${view.deadline.submitted} ? '' : 'border-red-400 bg-red-50'">
      <span th:text="|${view.deadline.cycleLabel} の締切は ${view.deadline.deadlineLabel}|">
        10/1〜10/10 の締切は 9/26（土）</span>
      <span th:if="${view.deadline.submitted}" class="block text-sm text-green-800">提出済み</span>
      <span th:unless="${view.deadline.submitted}" class="block font-bold text-red-700">未提出です。申請してください</span>
    </a>
  </section>

  <section>
    <h2 class="mb-2 font-bold">勤務予定時間</h2>
    <div class="card grid grid-cols-2 gap-4 text-center">
      <div>
        <span class="block text-sm text-stone-600" th:text="${view.thisMonthLabel}">9月</span>
        <span class="text-xl font-bold" th:text="${view.thisMonthHours}">13時間30分</span>
      </div>
      <div>
        <span class="block text-sm text-stone-600" th:text="${view.nextMonthLabel}">10月</span>
        <span class="text-xl font-bold" th:text="${view.nextMonthHours}">4時間</span>
      </div>
    </div>
    <p class="mt-1 text-xs text-stone-500">公開済みのシフトのIN〜OUTの合計です（休憩を含みます）</p>
  </section>
```

- [x] **Step 8: テストが通ることを確認する**

Run: `./mvnw test -Dtest=HomeTest,HomeChangeTest`
Expected: PASS

- [x] **Step 9: ブラウザで動作を確認する**

`npm run build` の後、`./mvnw spring-boot:run` で起動し、スタッフでログインして次を確認する（確認できない場合はユーザーに報告して確認を依頼する）：
- 上から「変更あり」（ある場合）・次回の出勤・カレンダー・申請の締切・勤務予定時間の順に並ぶ
- カレンダーは日曜始まりで、自分の出勤日はINの時刻が出て色付きになる。公開済みの日を押すとその日の日別一覧が開く。下書きの日は灰色で押せない
- 前の月・次の月へ移動できる。スタッフは先月がすべて7日前より前なら「前の月」が出ない
- 下書きの日のシフトはどこにも出ず、公開すると出る
- 未提出のサイクルは赤く強調され、押すと申請画面のその月が開く。申請を登録すると「提出済み」になる
- スマートフォン幅（375px）でカレンダーの7列が収まり、横スクロールが出ない

- [x] **Step 10: 全テストを実行してコミットする**

Run: `./mvnw test`
Expected: PASS

```bash
git add -A
git commit -m "feat: トップ画面の次回の出勤・カレンダー・締切案内・予定時間

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [x] **Step 11: この計画ファイルのTask 3のチェックボックスをすべて `[x]` にしてコミットし、停止してユーザーに報告する**

```bash
git add docs/superpowers/plans/2026-09-28-plan4-viewing.md
git commit -m "docs: Plan 4 Task 3 完了

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
