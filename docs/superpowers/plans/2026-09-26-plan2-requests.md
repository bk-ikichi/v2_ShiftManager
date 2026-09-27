# Plan 2 申請 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **セッション運用：** 1セッション1 Task。Taskの最後のステップ（コミットとチェックボックス更新）が終わったら停止してユーザーに報告する。次のTaskは `/clear` 後の新しいセッションで行う。新しいセッションではこの冒頭（File Structureまで）と、未完了の最初のTaskの範囲だけを読む（`CLAUDE.md` 参照）。

**Goal:** スタッフがシフト希望（パターン・月ごとの一括申請・「この期間は出勤できない」）を申請でき、管理者がサイクル単位で全員の申請を閲覧・代理編集できるようにする。

**Architecture:** Plan 1 と同じく controller → service → repository → mapper の一方向。サイクル計算・時刻の選択肢・備考の正規化は状態を持たない `util` に置き、どの層からも使う。「今日」は `Clock` Bean（Asia/Tokyo）から得て、テストでは `@Primary` の `TestClock` に差し替える。申請画面は月単位のフォームを一括送信し、エラー時は入力を残したまま画面を表示し直す。

**Tech Stack:** Java 17、Spring Boot 4.1.1、MyBatis（アノテーションSQL）、Spring Security 7、Thymeleaf 3.1（`#temporals` 使用可）、Tailwind CSS 4、PostgreSQL 17、JUnit 5 + MockMvc + Testcontainers 2、素のJavaScript（申請画面のみ）

**Spec:** `documents/2026-09-25-shift-manager-v2-spec.md`、DB設計：`documents/2026-09-25-db-design.md`

## Global Constraints

- 画面の文言・コードのコメントはすべて日本語
- パッケージは層ごと。controllerはserviceのみ、serviceはrepositoryのみを呼ぶ（serviceから別のserviceを呼ばない）。`util` はどの層からも使ってよい
- 「今日」は必ず `Clock` Bean から得る（`LocalDate.now(clock)`）。引数なしの `LocalDate.now()` / `YearMonth.now()` を使わない
- 時刻は 8:00〜23:00、30分刻み、IN < OUT。日またぎなし。画面の値は `HH:mm`（例 `08:00`）
- サイクルは毎月 1日〜10日・11日〜20日・21日〜月末
- 締切日＝サイクル開始日 − `app_settings.deadline_days_before`（初期値5）。**締切日の当日まで**スタッフが編集できる
- スタッフが申請できるのは今月〜翌々月
- 申請は1人1日1件。備考は200文字以内（前後の空白を除去し、空ならnull）。時刻なしで備考だけの申請は不可
- 「この期間は出勤できない」にしたサイクルには申請を持たない
- 提出済み＝サイクル内に1日以上の申請がある、または「この期間は出勤できない」
- 管理者は締切後も代理編集できる。代理入力も提出済みとして扱う
- Thymeleafから `T(...)` で static メソッドを呼ばない。表示用の文字列（日付ラベル等）はDTOで作る
- スタッフ画面はスマートフォン優先（入力欄は `text-base` 以上でiOSの自動ズームを避ける）
- コミットメッセージの末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` を付ける

## Review Focus

1. 締切をまたいだ送信（締切日に開いた画面から、翌日に「登録する」）：そのサイクルは保存されずエラーになり、他のサイクルの入力は画面に残る → Task 3でテスト
2. 一括登録の中に1日でも入力エラー：何も保存されず、どの日か分かるメッセージ（例 `10/1：…`）が出て、入力済みの内容は消えない → Task 3でテスト
3. 選択肢にない時刻（`07:30`・`08:15`・`23:30`・文字列）、OUT ≦ IN、片方だけの入力：500エラーにならず入力エラー → Task 1・2・3・6でテスト
4. JavaScriptが効かない状態で「出勤できない」にチェックしたまま時刻を入力：保存されずエラー。締切済みサイクルの「出勤できない」は、他のサイクルを保存しても消えない → Task 4でテスト
5. 月末サイクルの長さ（2月は8日間・うるう年は9日間・31日の月は11日間）と年またぎ（12/21〜 の次が 1/1〜）：日付範囲が正しい → Task 1でテスト

## File Structure

```
src/main/java/jp/bk/shiftmanager/
  config/      ClockConfig                           Clock Bean（Asia/Tokyo）
  util/        Cycle, DateLabels, TimeSlots, TimeRange, RequestNote
  entity/      ShiftPattern, ShiftRequest
  mapper/      ShiftPatternMapper, ShiftRequestMapper, CycleUnavailableMapper
  repository/  ShiftPatternRepository, ShiftRequestRepository, CycleUnavailableRepository
  service/     PatternService, RequestService（スタッフ）, AdminRequestService（管理者）
  controller/  MyPageController, PatternController, RequestController, AdminRequestController
  form/        PatternForm, RequestMonthForm, RequestDayForm, RequestEditForm
  dto/         RequestMonthView, RequestCycleView, RequestDayView,
               RequestTableView, RequestTableRow, RequestCell, RequestEditView
src/main/resources/
  templates/layout.html                     ヘッダーに「申請」「マイページ」「申請一覧」を追加
  templates/mypage/index.html, mypage/patterns.html
  templates/requests/month.html
  templates/admin/requests/table.html, admin/requests/edit.html
  static/js/requests.js                     パターンの自動入力・出勤できない・未保存の確認
src/test/java/jp/bk/shiftmanager/
  TestClock.java, TestClockTest.java        （IntegrationTestBase・TestDataを変更）
  util/CycleTest, DateLabelsTest, TimeSlotsTest, TimeRangeTest, RequestNoteTest
  controller/PatternTest, RequestTest, RequestUnavailableTest, AdminRequestTest, AdminRequestEditTest
```

---

### Task 1: サイクル計算・時刻の選択肢・テスト用の時計

**Files:**
- Create: `util/Cycle.java`、`util/DateLabels.java`、`util/TimeSlots.java`、`util/TimeRange.java`、`config/ClockConfig.java`
- Create（テスト）: `src/test/java/jp/bk/shiftmanager/TestClock.java`
- Modify（テスト）: `src/test/java/jp/bk/shiftmanager/IntegrationTestBase.java`、`src/test/java/jp/bk/shiftmanager/TestData.java`
- Test: `util/CycleTest.java`、`util/DateLabelsTest.java`、`util/TimeSlotsTest.java`、`util/TimeRangeTest.java`、`TestClockTest.java`

（Javaのパスは `src/main/java/jp/bk/shiftmanager/`、テストは `src/test/java/jp/bk/shiftmanager/` からの相対）

**Interfaces:**
- Consumes: `exception.BusinessException`（Plan 1）
- Produces:
  - `util.Cycle`（record `Cycle(LocalDate start, LocalDate end)`）：`static of(LocalDate)`、`static ofMonth(YearMonth): List<Cycle>`、`next()`、`previous()`、`deadline(int daysBefore): LocalDate`、`isOpen(LocalDate today, int daysBefore): boolean`、`dates(): List<LocalDate>`、`label(): String`（例 `10/11〜10/20`）
  - `util.DateLabels`：`monthDay(LocalDate)`（`10/3`）、`monthDayWeek(LocalDate)`（`10/3（土）`）、`dayWeek(LocalDate)`（`3（土）`）
  - `util.TimeSlots`：`ALL: List<LocalTime>`、`OPTIONS: List<String>`、`parse(String): LocalTime`（空ならnull、選択肢外は `BusinessException`）、`format(LocalTime): String`（nullならnull）
  - `util.TimeRange`（record `TimeRange(LocalTime start, LocalTime end)`）：`static parse(String start, String end)`
  - `config.ClockConfig.ZONE`（Asia/Tokyo）と `Clock` Bean
  - テスト：`TestClock.DEFAULT_TODAY`（2026-09-25）、`TestClock#setToday(LocalDate)`、`TestData#today(LocalDate)`

- [x] **Step 1: 失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/util/CycleTest.java`：

```java
package jp.bk.shiftmanager.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class CycleTest {

    private static LocalDate d(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    @Test
    void 日付からサイクルを求める() {
        assertThat(Cycle.of(d(2026, 10, 1))).isEqualTo(new Cycle(d(2026, 10, 1), d(2026, 10, 10)));
        assertThat(Cycle.of(d(2026, 10, 10))).isEqualTo(new Cycle(d(2026, 10, 1), d(2026, 10, 10)));
        assertThat(Cycle.of(d(2026, 10, 11))).isEqualTo(new Cycle(d(2026, 10, 11), d(2026, 10, 20)));
        assertThat(Cycle.of(d(2026, 10, 20))).isEqualTo(new Cycle(d(2026, 10, 11), d(2026, 10, 20)));
        assertThat(Cycle.of(d(2026, 10, 21))).isEqualTo(new Cycle(d(2026, 10, 21), d(2026, 10, 31)));
        assertThat(Cycle.of(d(2026, 9, 30))).isEqualTo(new Cycle(d(2026, 9, 21), d(2026, 9, 30)));
    }

    @Test
    void 月末のサイクルは月の日数で長さが変わる() {
        assertThat(Cycle.of(d(2027, 2, 21)).dates()).hasSize(8);
        assertThat(Cycle.of(d(2028, 2, 25)).end()).isEqualTo(d(2028, 2, 29));
        assertThat(Cycle.of(d(2028, 2, 25)).dates()).hasSize(9);
        assertThat(Cycle.of(d(2026, 9, 21)).dates()).hasSize(10);
        assertThat(Cycle.of(d(2026, 10, 21)).dates()).hasSize(11)
                .startsWith(d(2026, 10, 21)).endsWith(d(2026, 10, 31));
    }

    @Test
    void 月のサイクルは3つ() {
        assertThat(Cycle.ofMonth(YearMonth.of(2027, 2))).extracting(Cycle::start)
                .containsExactly(d(2027, 2, 1), d(2027, 2, 11), d(2027, 2, 21));
        assertThat(Cycle.ofMonth(YearMonth.of(2027, 2)).get(2).end()).isEqualTo(d(2027, 2, 28));
    }

    @Test
    void 前後のサイクルは月や年をまたぐ() {
        assertThat(Cycle.of(d(2026, 9, 25)).next()).isEqualTo(new Cycle(d(2026, 10, 1), d(2026, 10, 10)));
        assertThat(Cycle.of(d(2026, 10, 1)).previous()).isEqualTo(new Cycle(d(2026, 9, 21), d(2026, 9, 30)));
        assertThat(Cycle.of(d(2026, 12, 25)).next().start()).isEqualTo(d(2027, 1, 1));
        assertThat(Cycle.of(d(2027, 1, 5)).previous().start()).isEqualTo(d(2026, 12, 21));
    }

    @Test
    void 締切日はサイクル開始日の設定日数前() {
        assertThat(Cycle.of(d(2026, 10, 11)).deadline(5)).isEqualTo(d(2026, 10, 6));
        assertThat(Cycle.of(d(2026, 10, 1)).deadline(5)).isEqualTo(d(2026, 9, 26));
        assertThat(Cycle.of(d(2026, 10, 1)).deadline(0)).isEqualTo(d(2026, 10, 1));
    }

    @Test
    void 締切日の当日までは編集できる() {
        Cycle cycle = Cycle.of(d(2026, 10, 11));
        assertThat(cycle.isOpen(d(2026, 10, 6), 5)).isTrue();
        assertThat(cycle.isOpen(d(2026, 10, 7), 5)).isFalse();
        assertThat(cycle.isOpen(d(2026, 10, 11), 0)).isTrue();
        assertThat(cycle.isOpen(d(2026, 10, 12), 0)).isFalse();
    }

    @Test
    void 表示用の期間() {
        assertThat(Cycle.of(d(2026, 10, 15)).label()).isEqualTo("10/11〜10/20");
    }
}
```

`src/test/java/jp/bk/shiftmanager/util/DateLabelsTest.java`：

```java
package jp.bk.shiftmanager.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DateLabelsTest {

    private static final LocalDate OCT3 = LocalDate.of(2026, 10, 3);

    @Test
    void 画面表示用の日付() {
        assertThat(DateLabels.monthDay(OCT3)).isEqualTo("10/3");
        assertThat(DateLabels.monthDayWeek(OCT3)).isEqualTo("10/3（土）");
        assertThat(DateLabels.dayWeek(OCT3)).isEqualTo("3（土）");
    }
}
```

`src/test/java/jp/bk/shiftmanager/util/TimeSlotsTest.java`：

```java
package jp.bk.shiftmanager.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import jp.bk.shiftmanager.exception.BusinessException;
import org.junit.jupiter.api.Test;

class TimeSlotsTest {

    @Test
    void 選択肢は8時から23時まで30分刻み() {
        assertThat(TimeSlots.OPTIONS).hasSize(31)
                .startsWith("08:00", "08:30")
                .endsWith("22:30", "23:00");
    }

    @Test
    void 選択肢の時刻を変換できる() {
        assertThat(TimeSlots.parse("08:00")).isEqualTo(LocalTime.of(8, 0));
        assertThat(TimeSlots.parse(" 23:00 ")).isEqualTo(LocalTime.of(23, 0));
        assertThat(TimeSlots.parse("")).isNull();
        assertThat(TimeSlots.parse("  ")).isNull();
        assertThat(TimeSlots.parse(null)).isNull();
        assertThat(TimeSlots.format(LocalTime.of(9, 30))).isEqualTo("09:30");
        assertThat(TimeSlots.format(null)).isNull();
    }

    @Test
    void 選択肢にない時刻は入力エラー() {
        for (String invalid : new String[] {"07:30", "23:30", "08:15", "8:00", "abc", "25:00"}) {
            assertThatThrownBy(() -> TimeSlots.parse(invalid))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("時刻は8:00〜23:00の30分刻みで選択してください");
        }
    }
}
```

`src/test/java/jp/bk/shiftmanager/util/TimeRangeTest.java`：

```java
package jp.bk.shiftmanager.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import jp.bk.shiftmanager.exception.BusinessException;
import org.junit.jupiter.api.Test;

class TimeRangeTest {

    @Test
    void INとOUTを変換できる() {
        TimeRange range = TimeRange.parse("09:00", "17:30");
        assertThat(range.start()).isEqualTo(LocalTime.of(9, 0));
        assertThat(range.end()).isEqualTo(LocalTime.of(17, 30));
    }

    @Test
    void 片方または両方が空ならエラー() {
        for (String[] pair : new String[][] {{"09:00", ""}, {"", "17:00"}, {"", ""}, {null, null}}) {
            assertThatThrownBy(() -> TimeRange.parse(pair[0], pair[1]))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("INとOUTを選択してください");
        }
    }

    @Test
    void OUTがIN以前ならエラー() {
        for (String[] pair : new String[][] {{"12:00", "12:00"}, {"13:00", "12:00"}}) {
            assertThatThrownBy(() -> TimeRange.parse(pair[0], pair[1]))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("OUTはINより後の時刻にしてください");
        }
    }

    @Test
    void 選択肢にない時刻はエラー() {
        assertThatThrownBy(() -> TimeRange.parse("07:30", "12:00"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("時刻は8:00〜23:00の30分刻みで選択してください");
    }
}
```

`src/test/java/jp/bk/shiftmanager/TestClockTest.java`：

```java
package jp.bk.shiftmanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TestClockTest extends IntegrationTestBase {

    @Autowired
    Clock clock;

    @Test
    void アプリの今日はテストから変更できる() {
        assertThat(LocalDate.now(clock)).isEqualTo(TestClock.DEFAULT_TODAY);

        data.today(LocalDate.of(2026, 10, 7));

        assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 10, 7));
    }
}
```

- [x] **Step 2: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=CycleTest,DateLabelsTest,TimeSlotsTest,TimeRangeTest,TestClockTest`
Expected: FAIL（コンパイルエラー：`Cycle` などが存在しない）

- [x] **Step 3: 日付・サイクルのユーティリティを実装する**

`src/main/java/jp/bk/shiftmanager/util/DateLabels.java`：

```java
package jp.bk.shiftmanager.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 画面表示用の日付の書式 */
public final class DateLabels {

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M/d", Locale.JAPANESE);
    private static final DateTimeFormatter MONTH_DAY_WEEK = DateTimeFormatter.ofPattern("M/d（E）", Locale.JAPANESE);
    private static final DateTimeFormatter DAY_WEEK = DateTimeFormatter.ofPattern("d（E）", Locale.JAPANESE);

    private DateLabels() {
    }

    /** 例：10/3 */
    public static String monthDay(LocalDate date) {
        return date.format(MONTH_DAY);
    }

    /** 例：10/3（土） */
    public static String monthDayWeek(LocalDate date) {
        return date.format(MONTH_DAY_WEEK);
    }

    /** 例：3（土） */
    public static String dayWeek(LocalDate date) {
        return date.format(DAY_WEEK);
    }
}
```

`src/main/java/jp/bk/shiftmanager/util/Cycle.java`：

```java
package jp.bk.shiftmanager.util;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/** 申請のサイクル（毎月1日〜10日・11日〜20日・21日〜月末） */
public record Cycle(LocalDate start, LocalDate end) {

    /** 指定日を含むサイクル */
    public static Cycle of(LocalDate date) {
        int day = date.getDayOfMonth();
        if (day <= 10) {
            return new Cycle(date.withDayOfMonth(1), date.withDayOfMonth(10));
        }
        if (day <= 20) {
            return new Cycle(date.withDayOfMonth(11), date.withDayOfMonth(20));
        }
        return new Cycle(date.withDayOfMonth(21), date.with(TemporalAdjusters.lastDayOfMonth()));
    }

    /** その月の3つのサイクル */
    public static List<Cycle> ofMonth(YearMonth month) {
        return List.of(of(month.atDay(1)), of(month.atDay(11)), of(month.atDay(21)));
    }

    public Cycle next() {
        return of(end.plusDays(1));
    }

    public Cycle previous() {
        return of(start.minusDays(1));
    }

    /** 締切日（サイクル開始日の daysBefore 日前）。この日まではスタッフが編集できる */
    public LocalDate deadline(int daysBefore) {
        return start.minusDays(daysBefore);
    }

    /** スタッフが編集できるか（今日が締切日以前か） */
    public boolean isOpen(LocalDate today, int daysBefore) {
        return !today.isAfter(deadline(daysBefore));
    }

    /** 開始日から終了日までの日付 */
    public List<LocalDate> dates() {
        return start.datesUntil(end.plusDays(1)).toList();
    }

    /** 例：10/11〜10/20 */
    public String label() {
        return DateLabels.monthDay(start) + "〜" + DateLabels.monthDay(end);
    }
}
```

- [x] **Step 4: 時刻のユーティリティを実装する**

`src/main/java/jp/bk/shiftmanager/util/TimeSlots.java`：

```java
package jp.bk.shiftmanager.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Stream;
import jp.bk.shiftmanager.exception.BusinessException;

/** シフトの時刻の選択肢（8:00〜23:00、30分刻み） */
public final class TimeSlots {

    public static final LocalTime FIRST = LocalTime.of(8, 0);
    public static final LocalTime LAST = LocalTime.of(23, 0);

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /** 選択肢の時刻（31個） */
    public static final List<LocalTime> ALL =
            Stream.iterate(FIRST, t -> !t.isAfter(LAST), t -> t.plusMinutes(30)).toList();

    /** プルダウンに表示する文字列（例：08:00） */
    public static final List<String> OPTIONS = ALL.stream().map(TimeSlots::format).toList();

    private TimeSlots() {
    }

    /** 画面入力（HH:mm）を時刻に変換する。空ならnull。選択肢にない時刻は入力エラー */
    public static LocalTime parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            LocalTime time = LocalTime.parse(text.strip(), FORMAT);
            if (ALL.contains(time)) {
                return time;
            }
        } catch (DateTimeParseException e) {
            // 下で入力エラーにする
        }
        throw new BusinessException("時刻は8:00〜23:00の30分刻みで選択してください");
    }

    /** 例：08:00。nullならnull */
    public static String format(LocalTime time) {
        return time == null ? null : time.format(FORMAT);
    }
}
```

`src/main/java/jp/bk/shiftmanager/util/TimeRange.java`：

```java
package jp.bk.shiftmanager.util;

import java.time.LocalTime;
import jp.bk.shiftmanager.exception.BusinessException;

/** IN・OUTの組（8:00〜23:00の30分刻み、IN < OUT） */
public record TimeRange(LocalTime start, LocalTime end) {

    /** 画面入力の文字列を検証して変換する */
    public static TimeRange parse(String start, String end) {
        LocalTime startTime = TimeSlots.parse(start);
        LocalTime endTime = TimeSlots.parse(end);
        if (startTime == null || endTime == null) {
            throw new BusinessException("INとOUTを選択してください");
        }
        if (!startTime.isBefore(endTime)) {
            throw new BusinessException("OUTはINより後の時刻にしてください");
        }
        return new TimeRange(startTime, endTime);
    }
}
```

- [x] **Step 5: Clock Bean とテスト用の時計を実装する**

`src/main/java/jp/bk/shiftmanager/config/ClockConfig.java`：

```java
package jp.bk.shiftmanager.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 「今日」の基準となる時計。テストでは差し替える */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    @Bean
    Clock clock() {
        return Clock.system(ZONE);
    }
}
```

`src/test/java/jp/bk/shiftmanager/TestClock.java`：

```java
package jp.bk.shiftmanager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import jp.bk.shiftmanager.config.ClockConfig;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

/** テスト用の時計。今日の日付をテストから変更できる（初期値は2026-09-25の12:00） */
@TestComponent
@Primary
public class TestClock extends Clock {

    public static final LocalDate DEFAULT_TODAY = LocalDate.of(2026, 9, 25);

    private volatile Instant instant;

    public TestClock() {
        setToday(DEFAULT_TODAY);
    }

    public void setToday(LocalDate today) {
        instant = today.atTime(12, 0).atZone(ClockConfig.ZONE).toInstant();
    }

    @Override
    public ZoneId getZone() {
        return ClockConfig.ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
```

`src/test/java/jp/bk/shiftmanager/IntegrationTestBase.java` の `@Import` を変更する：

```java
@Import({TestcontainersConfiguration.class, TestData.class, TestClock.class})
```

`src/test/java/jp/bk/shiftmanager/TestData.java` を変更する：
- import に `java.time.LocalDate` を追加
- フィールドに `private final TestClock clock;` を追加（`@RequiredArgsConstructor` で注入される）
- `reset()` の最後に次の行を追加：

```java
        clock.setToday(TestClock.DEFAULT_TODAY);
```

- メソッドを追加：

```java
    /** アプリの「今日」を変更する（テストごとに初期値へ戻る） */
    public void today(LocalDate today) {
        clock.setToday(today);
    }
```

- [x] **Step 6: テストが通ることを確認する**

Run: `./mvnw test -Dtest=CycleTest,DateLabelsTest,TimeSlotsTest,TimeRangeTest,TestClockTest`
Expected: PASS

- [x] **Step 7: 全テストを実行してコミットする**

Run: `./mvnw test`
Expected: PASS

```bash
git add -A
git commit -m "feat: サイクル計算・時刻の選択肢・Clockの導入

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [x] **Step 8: この計画ファイルのTask 1のチェックボックスをすべて `[x]` にしてコミットし、停止してユーザーに報告する**

```bash
git add docs/superpowers/plans/2026-09-26-plan2-requests.md
git commit -m "docs: Plan 2 Task 1 完了

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: 申請パターン（マイページ）

**Files:**
- Create: `entity/ShiftPattern.java`、`mapper/ShiftPatternMapper.java`、`repository/ShiftPatternRepository.java`、`service/PatternService.java`、`form/PatternForm.java`、`controller/MyPageController.java`、`controller/PatternController.java`
- Create: `src/main/resources/templates/mypage/index.html`、`src/main/resources/templates/mypage/patterns.html`
- Modify: `src/main/resources/templates/layout.html`（ヘッダーの「パスワード変更」を「マイページ」に置き換え）
- Modify（テスト）: `TestData.java`（`pattern(...)` を追加）
- Test: `controller/PatternTest.java`

**Interfaces:**
- Consumes: `util.TimeRange#parse`、`util.TimeSlots.OPTIONS`（Task 1）、`auth.LoginUser#getId()`、`exception.BusinessException`（Plan 1）
- Produces:
  - `entity.ShiftPattern`（`Long id, Long userId, String name, LocalTime startTime, LocalTime endTime`）
  - `service.PatternService#findMine(long userId): List<ShiftPattern>`（IN・OUTの早い順。Task 4の申請画面で使う）
  - 画面：`GET /mypage`、`GET/POST /mypage/patterns`、`POST /mypage/patterns/{id}`、`POST /mypage/patterns/{id}/delete`
  - テスト：`TestData#pattern(User, String name, String start, String end): ShiftPattern`

- [x] **Step 1: テストデータの作成メソッドを追加し、失敗するテストを書く**

`TestData.java` に追加する：
- import：`jp.bk.shiftmanager.entity.ShiftPattern`、`jp.bk.shiftmanager.mapper.ShiftPatternMapper`、`java.time.LocalTime`
- フィールド：`private final ShiftPatternMapper shiftPatternMapper;`
- メソッド：

```java
    public ShiftPattern pattern(User user, String name, String start, String end) {
        ShiftPattern pattern = new ShiftPattern();
        pattern.setUserId(user.getId());
        pattern.setName(name);
        pattern.setStartTime(LocalTime.parse(start));
        pattern.setEndTime(LocalTime.parse(end));
        shiftPatternMapper.insert(pattern);
        return pattern;
    }
```

`src/test/java/jp/bk/shiftmanager/controller/PatternTest.java`：

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

import java.time.LocalTime;
import java.util.List;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.entity.ShiftPattern;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.ShiftPatternMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PatternTest extends IntegrationTestBase {

    @Autowired
    ShiftPatternMapper shiftPatternMapper;

    User taro;
    LoginUser me;

    @BeforeEach
    void setUp() {
        taro = data.user("taro", "山田太郎", false);
        me = data.login(taro);
    }

    @Test
    void マイページにパターンとパスワード変更へのリンクがある() throws Exception {
        mvc.perform(get("/mypage").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("href=\"/mypage/patterns\"")))
                .andExpect(content().string(Matchers.containsString("href=\"/password\"")));
    }

    @Test
    void ヘッダーからマイページへ行ける() throws Exception {
        mvc.perform(get("/").with(user(me)))
                .andExpect(content().string(Matchers.containsString("href=\"/mypage\"")));
    }

    @Test
    void パターンを追加できる() throws Exception {
        mvc.perform(post("/mypage/patterns").with(user(me)).with(csrf())
                        .param("name", " 朝 ").param("startTime", "08:00").param("endTime", "13:00"))
                .andExpect(redirectedUrl("/mypage/patterns"));

        List<ShiftPattern> patterns = shiftPatternMapper.findByUserId(taro.getId());
        assertThat(patterns).hasSize(1);
        assertThat(patterns.get(0).getName()).isEqualTo("朝");
        assertThat(patterns.get(0).getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(patterns.get(0).getEndTime()).isEqualTo(LocalTime.of(13, 0));
    }

    @Test
    void 自分のパターンだけが一覧に表示される() throws Exception {
        User hanako = data.user("hanako", "佐藤花子", false);
        data.pattern(taro, "朝", "08:00", "13:00");
        data.pattern(hanako, "他人用", "17:00", "22:00");

        mvc.perform(get("/mypage/patterns").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("朝")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("他人用"))));
    }

    @Test
    void 時刻が不正なパターンは追加されない() throws Exception {
        String[][] cases = {{"07:30", "12:00"}, {"08:15", "12:00"}, {"22:30", "23:30"}, {"abc", "12:00"},
                {"12:00", "12:00"}, {"13:00", "12:00"}, {"", "12:00"}};
        for (String[] c : cases) {
            mvc.perform(post("/mypage/patterns").with(user(me)).with(csrf())
                            .param("name", "朝").param("startTime", c[0]).param("endTime", c[1]))
                    .andExpect(redirectedUrl("/mypage/patterns"))
                    .andExpect(flash().attributeExists("error"));
        }
        assertThat(shiftPatternMapper.findByUserId(taro.getId())).isEmpty();
    }

    @Test
    void 名前が空または21文字以上のパターンは追加されない() throws Exception {
        for (String name : new String[] {" ", "あ".repeat(21)}) {
            mvc.perform(post("/mypage/patterns").with(user(me)).with(csrf())
                            .param("name", name).param("startTime", "08:00").param("endTime", "13:00"))
                    .andExpect(flash().attributeExists("error"));
        }
        assertThat(shiftPatternMapper.findByUserId(taro.getId())).isEmpty();
    }

    @Test
    void パターンを編集できる() throws Exception {
        ShiftPattern pattern = data.pattern(taro, "朝", "08:00", "13:00");

        mvc.perform(post("/mypage/patterns/{id}", pattern.getId()).with(user(me)).with(csrf())
                        .param("name", "早朝").param("startTime", "08:00").param("endTime", "12:00"))
                .andExpect(redirectedUrl("/mypage/patterns"))
                .andExpect(flash().attribute("message", "保存しました"));

        ShiftPattern saved = shiftPatternMapper.findById(pattern.getId());
        assertThat(saved.getName()).isEqualTo("早朝");
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    void パターンを削除できる() throws Exception {
        ShiftPattern pattern = data.pattern(taro, "朝", "08:00", "13:00");

        mvc.perform(post("/mypage/patterns/{id}/delete", pattern.getId()).with(user(me)).with(csrf()))
                .andExpect(redirectedUrl("/mypage/patterns"));

        assertThat(shiftPatternMapper.findById(pattern.getId())).isNull();
    }

    @Test
    void 他人のパターンは編集も削除もできない() throws Exception {
        User hanako = data.user("hanako", "佐藤花子", false);
        ShiftPattern others = data.pattern(hanako, "夕方", "17:00", "22:00");

        mvc.perform(post("/mypage/patterns/{id}", others.getId()).with(user(me)).with(csrf())
                        .param("name", "乗っ取り").param("startTime", "08:00").param("endTime", "12:00"))
                .andExpect(flash().attribute("error", "パターンが見つかりません"));
        mvc.perform(post("/mypage/patterns/{id}/delete", others.getId()).with(user(me)).with(csrf()))
                .andExpect(flash().attribute("error", "パターンが見つかりません"));

        ShiftPattern saved = shiftPatternMapper.findById(others.getId());
        assertThat(saved.getName()).isEqualTo("夕方");
    }
}
```

- [x] **Step 2: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=PatternTest`
Expected: FAIL（コンパイルエラー：`ShiftPattern` などが存在しない）

- [x] **Step 3: Entity・Mapper・Repositoryを実装する**

`src/main/java/jp/bk/shiftmanager/entity/ShiftPattern.java`：

```java
package jp.bk.shiftmanager.entity;

import java.time.LocalTime;
import lombok.Data;

/** スタッフ個人の申請パターン（本人のみ使用） */
@Data
public class ShiftPattern {
    private Long id;
    private Long userId;
    private String name;
    private LocalTime startTime;
    private LocalTime endTime;
}
```

`src/main/java/jp/bk/shiftmanager/mapper/ShiftPatternMapper.java`：

```java
package jp.bk.shiftmanager.mapper;

import java.util.List;
import jp.bk.shiftmanager.entity.ShiftPattern;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ShiftPatternMapper {

    @Select("SELECT * FROM shift_patterns WHERE user_id = #{userId} ORDER BY start_time, end_time, id")
    List<ShiftPattern> findByUserId(@Param("userId") long userId);

    @Select("SELECT * FROM shift_patterns WHERE id = #{id}")
    ShiftPattern findById(@Param("id") long id);

    @Insert("""
            INSERT INTO shift_patterns (user_id, name, start_time, end_time)
            VALUES (#{userId}, #{name}, #{startTime}, #{endTime})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(ShiftPattern pattern);

    @Update("UPDATE shift_patterns SET name = #{name}, start_time = #{startTime}, end_time = #{endTime} WHERE id = #{id}")
    int update(ShiftPattern pattern);

    @Delete("DELETE FROM shift_patterns WHERE id = #{id}")
    int delete(@Param("id") long id);
}
```

`src/main/java/jp/bk/shiftmanager/repository/ShiftPatternRepository.java`：

```java
package jp.bk.shiftmanager.repository;

import java.util.List;
import java.util.Optional;
import jp.bk.shiftmanager.entity.ShiftPattern;
import jp.bk.shiftmanager.mapper.ShiftPatternMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ShiftPatternRepository {

    private final ShiftPatternMapper shiftPatternMapper;

    public List<ShiftPattern> findByUserId(long userId) {
        return shiftPatternMapper.findByUserId(userId);
    }

    public Optional<ShiftPattern> findById(long id) {
        return Optional.ofNullable(shiftPatternMapper.findById(id));
    }

    public void insert(ShiftPattern pattern) {
        shiftPatternMapper.insert(pattern);
    }

    public void update(ShiftPattern pattern) {
        shiftPatternMapper.update(pattern);
    }

    public void delete(long id) {
        shiftPatternMapper.delete(id);
    }
}
```

- [x] **Step 4: Form・Serviceを実装する**

`src/main/java/jp/bk/shiftmanager/form/PatternForm.java`：

```java
package jp.bk.shiftmanager.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PatternForm {

    @NotBlank(message = "名前を入力してください")
    @Size(max = 20, message = "名前は20文字以内で入力してください")
    private String name;

    /** HH:mm。検証はServiceで行う */
    private String startTime;

    private String endTime;

    public void setName(String name) {
        this.name = name == null ? null : name.strip();
    }
}
```

`src/main/java/jp/bk/shiftmanager/service/PatternService.java`：

```java
package jp.bk.shiftmanager.service;

import java.util.List;
import jp.bk.shiftmanager.entity.ShiftPattern;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.PatternForm;
import jp.bk.shiftmanager.repository.ShiftPatternRepository;
import jp.bk.shiftmanager.util.TimeRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PatternService {

    private final ShiftPatternRepository shiftPatternRepository;

    /** 本人のパターン（IN・OUTの早い順） */
    public List<ShiftPattern> findMine(long userId) {
        return shiftPatternRepository.findByUserId(userId);
    }

    @Transactional
    public void create(long userId, PatternForm form) {
        TimeRange range = TimeRange.parse(form.getStartTime(), form.getEndTime());
        ShiftPattern pattern = new ShiftPattern();
        pattern.setUserId(userId);
        apply(pattern, form, range);
        shiftPatternRepository.insert(pattern);
    }

    @Transactional
    public void update(long userId, long id, PatternForm form) {
        ShiftPattern pattern = findOwn(userId, id);
        TimeRange range = TimeRange.parse(form.getStartTime(), form.getEndTime());
        apply(pattern, form, range);
        shiftPatternRepository.update(pattern);
    }

    @Transactional
    public void delete(long userId, long id) {
        findOwn(userId, id);
        shiftPatternRepository.delete(id);
    }

    /** 本人のパターンを返す。他人のパターンは存在しないものとして扱う */
    private ShiftPattern findOwn(long userId, long id) {
        return shiftPatternRepository.findById(id)
                .filter(pattern -> pattern.getUserId() == userId)
                .orElseThrow(() -> new BusinessException("パターンが見つかりません"));
    }

    private void apply(ShiftPattern pattern, PatternForm form, TimeRange range) {
        pattern.setName(form.getName());
        pattern.setStartTime(range.start());
        pattern.setEndTime(range.end());
    }
}
```

- [x] **Step 5: コントローラーを実装する**

`src/main/java/jp/bk/shiftmanager/controller/MyPageController.java`：

```java
package jp.bk.shiftmanager.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** マイページ（本人用の設定画面への入口） */
@Controller
public class MyPageController {

    @GetMapping("/mypage")
    public String index() {
        return "mypage/index";
    }
}
```

`src/main/java/jp/bk/shiftmanager/controller/PatternController.java`：

```java
package jp.bk.shiftmanager.controller;

import jakarta.validation.Valid;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.PatternForm;
import jp.bk.shiftmanager.service.PatternService;
import jp.bk.shiftmanager.util.TimeSlots;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/mypage/patterns")
@RequiredArgsConstructor
public class PatternController {

    private static final String REDIRECT = "redirect:/mypage/patterns";

    private final PatternService patternService;

    @GetMapping
    public String list(@AuthenticationPrincipal LoginUser me, Model model) {
        model.addAttribute("patterns", patternService.findMine(me.getId()));
        model.addAttribute("timeOptions", TimeSlots.OPTIONS);
        return "mypage/patterns";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal LoginUser me, @Valid @ModelAttribute PatternForm form,
            BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return REDIRECT;
        }
        try {
            patternService.create(me.getId(), form);
            redirectAttributes.addFlashAttribute("message", "追加しました");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return REDIRECT;
    }

    @PostMapping("/{id}")
    public String update(@AuthenticationPrincipal LoginUser me, @PathVariable long id,
            @Valid @ModelAttribute PatternForm form, BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return REDIRECT;
        }
        try {
            patternService.update(me.getId(), id, form);
            redirectAttributes.addFlashAttribute("message", "保存しました");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return REDIRECT;
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal LoginUser me, @PathVariable long id,
            RedirectAttributes redirectAttributes) {
        try {
            patternService.delete(me.getId(), id);
            redirectAttributes.addFlashAttribute("message", "削除しました");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return REDIRECT;
    }
}
```

- [x] **Step 6: テンプレートを作成し、ヘッダーを変更する**

`src/main/resources/templates/mypage/index.html`：

```html
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout :: head('マイページ')}"></head>
<body class="min-h-screen bg-stone-50 text-stone-900">
<header th:replace="~{layout :: header}"></header>
<main class="mx-auto max-w-md px-4 py-6">
  <h1 class="mb-4 text-lg font-bold">マイページ</h1>
  <div class="rounded-lg border border-stone-200 bg-white divide-y divide-stone-100">
    <a th:href="@{/mypage/patterns}" class="block px-4 py-3 hover:bg-stone-50">申請パターン</a>
    <a th:href="@{/password}" class="block px-4 py-3 hover:bg-stone-50">パスワード変更</a>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/mypage/patterns.html`：

```html
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout :: head('申請パターン')}"></head>
<body class="min-h-screen bg-stone-50 text-stone-900">
<header th:replace="~{layout :: header}"></header>
<main class="mx-auto max-w-md px-4 py-6">
  <h1 class="mb-4 text-lg font-bold">申請パターン</h1>
  <div th:replace="~{layout :: flash}"></div>
  <p class="mb-4 text-sm text-stone-600">申請画面でパターンを選ぶと、IN・OUTが自動で入力されます。</p>

  <div class="card mb-6 space-y-3">
    <div th:each="p : ${patterns}" class="border-b border-stone-100 pb-3">
      <form th:action="@{/mypage/patterns/{id}(id=${p.id})}" method="post" class="grid grid-cols-3 gap-2">
        <label class="col-span-3 block">
          <span class="text-xs">名前</span>
          <input name="name" th:value="${p.name}" maxlength="20" required class="input">
        </label>
        <label class="block">
          <span class="text-xs">IN</span>
          <select name="startTime" class="input px-1">
            <option th:each="t : ${timeOptions}" th:value="${t}" th:text="${t}"
                    th:selected="${t == #temporals.format(p.startTime, 'HH:mm')}"></option>
          </select>
        </label>
        <label class="block">
          <span class="text-xs">OUT</span>
          <select name="endTime" class="input px-1">
            <option th:each="t : ${timeOptions}" th:value="${t}" th:text="${t}"
                    th:selected="${t == #temporals.format(p.endTime, 'HH:mm')}"></option>
          </select>
        </label>
        <button class="btn-secondary self-end">保存</button>
      </form>
      <form th:action="@{/mypage/patterns/{id}/delete(id=${p.id})}" method="post" class="mt-2 text-right">
        <button class="btn-danger px-3 py-1 text-sm">削除</button>
      </form>
    </div>
    <p th:if="${#lists.isEmpty(patterns)}" class="text-sm text-stone-500">パターンがまだありません。</p>
  </div>

  <h2 class="mb-2 font-bold">追加</h2>
  <form th:action="@{/mypage/patterns}" method="post" class="card grid grid-cols-3 gap-2">
    <label class="col-span-3 block">
      <span class="text-xs">名前</span>
      <input name="name" maxlength="20" required class="input" placeholder="例：朝">
    </label>
    <label class="block">
      <span class="text-xs">IN</span>
      <select name="startTime" class="input px-1">
        <option th:each="t : ${timeOptions}" th:value="${t}" th:text="${t}"></option>
      </select>
    </label>
    <label class="block">
      <span class="text-xs">OUT</span>
      <select name="endTime" class="input px-1">
        <option th:each="t : ${timeOptions}" th:value="${t}" th:text="${t}"></option>
      </select>
    </label>
    <button class="btn-primary self-end">追加する</button>
  </form>
</main>
</body>
</html>
```

`src/main/resources/templates/layout.html` のヘッダーで、次の行を

```html
    <a th:href="@{/password}" class="hover:underline">パスワード変更</a>
```

次の行に置き換える：

```html
    <a th:href="@{/mypage}" class="hover:underline">マイページ</a>
```

- [x] **Step 7: テストが通ることを確認する**

Run: `./mvnw test -Dtest=PatternTest`
Expected: PASS（9件）

- [x] **Step 8: 全テストを実行してコミットする**

Run: `./mvnw test`
Expected: PASS

```bash
git add -A
git commit -m "feat: 申請パターンの管理（マイページ）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [x] **Step 9: この計画ファイルのTask 2のチェックボックスをすべて `[x]` にしてコミットし、停止してユーザーに報告する**

```bash
git add docs/superpowers/plans/2026-09-26-plan2-requests.md
git commit -m "docs: Plan 2 Task 2 完了

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: 申請画面（表示と一括登録）

**Files:**
- Create: `entity/ShiftRequest.java`、`mapper/ShiftRequestMapper.java`、`repository/ShiftRequestRepository.java`、`util/RequestNote.java`、`form/RequestMonthForm.java`、`form/RequestDayForm.java`、`dto/RequestMonthView.java`、`dto/RequestCycleView.java`、`dto/RequestDayView.java`、`service/RequestService.java`、`controller/RequestController.java`
- Create: `src/main/resources/templates/requests/month.html`
- Modify: `src/main/resources/templates/layout.html`（ヘッダーに「申請」）
- Modify（テスト）: `TestData.java`（`request(...)` を追加）
- Test: `util/RequestNoteTest.java`、`controller/RequestTest.java`

**Interfaces:**
- Consumes: `util.Cycle`・`DateLabels`・`TimeSlots`・`TimeRange`、`java.time.Clock` Bean、`TestData#today`（Task 1）、`repository.AppSettingRepository#getDeadlineDaysBefore(): int`（Plan 1）
- Produces:
  - `entity.ShiftRequest`（`Long id, Long userId, LocalDate workDate, LocalTime startTime, LocalTime endTime, String note`）
  - `mapper.ShiftRequestMapper#findByUserAndPeriod(long userId, LocalDate from, LocalDate to)`、`#upsert(ShiftRequest)`、`#delete(long userId, LocalDate date): int`
  - `repository.ShiftRequestRepository`：同名メソッド（`findByUserAndPeriod`、`upsert`、`delete`）
  - `util.RequestNote#normalize(String): String`（前後空白除去、空ならnull、200文字超は `BusinessException`）
  - `form.RequestMonthForm`（`String month`、`List<RequestDayForm> days`）、`form.RequestDayForm`（`String date, startTime, endTime, note`）
  - `dto.RequestMonthView`（`month, months, cycles`、`applyInput(RequestMonthForm)`）、`dto.RequestCycleView`（`cycle, label, deadlineLabel, open, days`）、`dto.RequestDayView`（`date, label, index, startTime, endTime, note`）
  - `service.RequestService#requestMonths()`、`#resolveMonth(String)`、`#getMonth(long userId, YearMonth)`、`#saveMonth(long userId, RequestMonthForm)`
  - 画面：`GET /requests?month=yyyy-MM`、`POST /requests`
  - テスト：`TestData#request(User, LocalDate, String start, String end, String note)`

- [x] **Step 1: テストデータの作成メソッドを追加し、失敗するテストを書く**

`TestData.java` にメソッドを追加する：

```java
    public void request(User user, LocalDate date, String start, String end, String note) {
        jdbc.update("INSERT INTO shift_requests (user_id, work_date, start_time, end_time, note) "
                + "VALUES (?, ?, ?::time, ?::time, ?)", user.getId(), date, start, end, note);
    }
```

`src/test/java/jp/bk/shiftmanager/util/RequestNoteTest.java`：

```java
package jp.bk.shiftmanager.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jp.bk.shiftmanager.exception.BusinessException;
import org.junit.jupiter.api.Test;

class RequestNoteTest {

    @Test
    void 前後の空白を除き空ならnullにする() {
        assertThat(RequestNote.normalize(" 午前のみ ")).isEqualTo("午前のみ");
        assertThat(RequestNote.normalize("")).isNull();
        assertThat(RequestNote.normalize("  ")).isNull();
        assertThat(RequestNote.normalize(null)).isNull();
    }

    @Test
    void 備考は200文字まで() {
        assertThat(RequestNote.normalize("あ".repeat(200))).hasSize(200);
        assertThatThrownBy(() -> RequestNote.normalize("あ".repeat(201)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("備考は200文字以内で入力してください");
    }
}
```

`src/test/java/jp/bk/shiftmanager/controller/RequestTest.java`：

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
import java.time.YearMonth;
import java.util.List;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.dto.RequestDayView;
import jp.bk.shiftmanager.dto.RequestMonthView;
import jp.bk.shiftmanager.entity.ShiftRequest;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.AppSettingMapper;
import jp.bk.shiftmanager.mapper.ShiftRequestMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** 申請画面（今日は2026-09-25（金）、締切はサイクル開始日の5日前） */
class RequestTest extends IntegrationTestBase {

    private static final LocalDate OCT1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT2 = LocalDate.of(2026, 10, 2);
    private static final LocalDate OCT12 = LocalDate.of(2026, 10, 12);
    private static final String OCT1_CLOSED = "10/1〜10/10は締切を過ぎたため変更できません。変更は管理者に伝えてください";

    @Autowired
    ShiftRequestMapper shiftRequestMapper;

    @Autowired
    AppSettingMapper appSettingMapper;

    User taro;
    LoginUser me;

    @BeforeEach
    void setUp() {
        taro = data.user("taro", "山田太郎", false);
        me = data.login(taro);
    }

    @Test
    void 申請画面に月のサイクルと締切が表示される() throws Exception {
        mvc.perform(get("/requests").param("month", "2026-10").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("10/1〜10/10")))
                .andExpect(content().string(Matchers.containsString("締切 9/26（土）")))
                .andExpect(content().string(Matchers.containsString("10/21〜10/31")))
                .andExpect(content().string(Matchers.containsString("登録する")));
    }

    @Test
    void 申請できるのは今月から翌々月まで() throws Exception {
        MvcResult result = mvc.perform(get("/requests").param("month", "2026-12").with(user(me))).andReturn();
        assertThat(view(result).getMonth()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(view(result).getMonths())
                .containsExactly(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11));

        MvcResult invalid = mvc.perform(get("/requests").param("month", "abc").with(user(me))).andReturn();
        assertThat(view(invalid).getMonth()).isEqualTo(YearMonth.of(2026, 9));
    }

    @Test
    void 締切済みのサイクルは入力欄を出さず申請内容だけ表示する() throws Exception {
        data.request(taro, LocalDate.of(2026, 9, 22), "09:00", "17:00", null);

        MvcResult result = mvc.perform(get("/requests").with(user(me)))
                .andExpect(content().string(Matchers.containsString("締切済み")))
                .andReturn();

        RequestDayView day = day(view(result), LocalDate.of(2026, 9, 22));
        assertThat(day.getIndex()).isNull();
        assertThat(day.getStartTime()).isEqualTo("09:00");
    }

    @Test
    void 一括で登録できる() throws Exception {
        mvc.perform(post("/requests").with(user(me)).with(csrf())
                        .param("month", "2026-10")
                        .param("days[0].date", "2026-10-01").param("days[0].startTime", "09:00")
                        .param("days[0].endTime", "17:00").param("days[0].note", " 午前だけでも可 ")
                        .param("days[1].date", "2026-10-02").param("days[1].startTime", "")
                        .param("days[1].endTime", "").param("days[1].note", "")
                        .param("days[2].date", "2026-10-12").param("days[2].startTime", "10:00")
                        .param("days[2].endTime", "15:00").param("days[2].note", ""))
                .andExpect(redirectedUrl("/requests?month=2026-10"));

        List<ShiftRequest> saved = requests();
        assertThat(saved).extracting(ShiftRequest::getWorkDate).containsExactly(OCT1, OCT12);
        assertThat(saved.get(0).getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(saved.get(0).getEndTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(saved.get(0).getNote()).isEqualTo("午前だけでも可");
        assertThat(saved.get(1).getNote()).isNull();
    }

    @Test
    void 空欄にした日は削除され時刻は上書きされる() throws Exception {
        data.request(taro, OCT1, "09:00", "17:00", null);
        data.request(taro, OCT2, "09:00", "17:00", "メモ");

        mvc.perform(post("/requests").with(user(me)).with(csrf())
                        .param("month", "2026-10")
                        .param("days[0].date", "2026-10-01").param("days[0].startTime", "")
                        .param("days[0].endTime", "").param("days[0].note", "")
                        .param("days[1].date", "2026-10-02").param("days[1].startTime", "12:00")
                        .param("days[1].endTime", "20:00").param("days[1].note", ""))
                .andExpect(redirectedUrl("/requests?month=2026-10"));

        List<ShiftRequest> saved = requests();
        assertThat(saved).extracting(ShiftRequest::getWorkDate).containsExactly(OCT2);
        assertThat(saved.get(0).getStartTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(saved.get(0).getEndTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(saved.get(0).getNote()).isNull();
    }

    @Test
    void 送信されなかった日の申請は変わらない() throws Exception {
        data.request(taro, OCT12, "09:00", "17:00", null);

        postOneDay("2026-10", "2026-10-01", "09:00", "17:00", "")
                .andExpect(redirectedUrl("/requests?month=2026-10"));

        assertThat(requests()).extracting(ShiftRequest::getWorkDate).containsExactly(OCT1, OCT12);
    }

    @Test
    void 締切日の当日までは登録できる() throws Exception {
        data.today(LocalDate.of(2026, 9, 26));

        postOneDay("2026-10", "2026-10-01", "09:00", "17:00", "")
                .andExpect(redirectedUrl("/requests?month=2026-10"));

        assertThat(requests()).hasSize(1);
    }

    @Test
    void 締切を過ぎたサイクルは保存されず入力は画面に残る() throws Exception {
        data.today(LocalDate.of(2026, 9, 27));

        MvcResult result = mvc.perform(post("/requests").with(user(me)).with(csrf())
                        .param("month", "2026-10")
                        .param("days[0].date", "2026-10-01").param("days[0].startTime", "09:00")
                        .param("days[0].endTime", "17:00").param("days[0].note", "")
                        .param("days[1].date", "2026-10-12").param("days[1].startTime", "10:00")
                        .param("days[1].endTime", "15:00").param("days[1].note", "昼まで"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", OCT1_CLOSED))
                .andReturn();

        assertThat(requests()).isEmpty();
        RequestDayView day = day(view(result), OCT12);
        assertThat(day.getStartTime()).isEqualTo("10:00");
        assertThat(day.getEndTime()).isEqualTo("15:00");
        assertThat(day.getNote()).isEqualTo("昼まで");
        assertThat(day(view(result), OCT1).getIndex()).isNull();
    }

    @Test
    void 締切日数の変更はすぐに反映される() throws Exception {
        appSettingMapper.updateDeadlineDaysBefore(10);

        postOneDay("2026-10", "2026-10-01", "09:00", "17:00", "")
                .andExpect(model().attribute("error", OCT1_CLOSED));

        assertThat(requests()).isEmpty();
    }

    @Test
    void 不正な時刻は登録されない() throws Exception {
        String timeError = "10/1：時刻は8:00〜23:00の30分刻みで選択してください";
        String orderError = "10/1：OUTはINより後の時刻にしてください";
        String[][] cases = {
                {"07:30", "12:00", timeError}, {"08:15", "12:00", timeError}, {"22:30", "23:30", timeError},
                {"abc", "12:00", timeError}, {"12:00", "12:00", orderError}, {"13:00", "12:00", orderError},
                {"09:00", "", "10/1：INとOUTを選択してください"}};
        for (String[] c : cases) {
            postOneDay("2026-10", "2026-10-01", c[0], c[1], "")
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("error", c[2]));
        }
        assertThat(requests()).isEmpty();
    }

    @Test
    void 備考だけの申請や長すぎる備考は登録されない() throws Exception {
        postOneDay("2026-10", "2026-10-01", "", "", "午後なら可")
                .andExpect(model().attribute("error", "10/1：INとOUTを選択してください"));
        postOneDay("2026-10", "2026-10-01", "09:00", "17:00", "あ".repeat(201))
                .andExpect(model().attribute("error", "10/1：備考は200文字以内で入力してください"));

        assertThat(requests()).isEmpty();
    }

    @Test
    void 対象月以外の日付や申請できない月は登録されない() throws Exception {
        postOneDay("2026-10", "2026-11-01", "09:00", "17:00", "")
                .andExpect(model().attribute("error", "不正な日付です"));
        postOneDay("2026-10", "abc", "09:00", "17:00", "")
                .andExpect(model().attribute("error", "不正な日付です"));
        postOneDay("2027-01", "2027-01-05", "09:00", "17:00", "")
                .andExpect(model().attribute("error", "申請できない月です"));

        assertThat(shiftRequestMapper.findByUserAndPeriod(taro.getId(), OCT1, LocalDate.of(2027, 1, 31))).isEmpty();
    }

    @Test
    void 他のスタッフの申請は表示も変更もされない() throws Exception {
        User hanako = data.user("hanako", "佐藤花子", false);
        data.request(hanako, OCT1, "09:00", "17:00", null);

        MvcResult result = mvc.perform(get("/requests").param("month", "2026-10").with(user(me))).andReturn();
        assertThat(day(view(result), OCT1).getStartTime()).isNull();

        postOneDay("2026-10", "2026-10-01", "", "", "");

        assertThat(shiftRequestMapper.findByUserAndPeriod(hanako.getId(), OCT1, OCT1)).hasSize(1);
    }

    private ResultActions postOneDay(String month, String date, String start, String end, String note)
            throws Exception {
        return mvc.perform(post("/requests").with(user(me)).with(csrf())
                .param("month", month)
                .param("days[0].date", date).param("days[0].startTime", start)
                .param("days[0].endTime", end).param("days[0].note", note));
    }

    private List<ShiftRequest> requests() {
        return shiftRequestMapper.findByUserAndPeriod(taro.getId(), LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 11, 30));
    }

    private static RequestMonthView view(MvcResult result) {
        return (RequestMonthView) result.getModelAndView().getModel().get("view");
    }

    private static RequestDayView day(RequestMonthView view, LocalDate date) {
        return view.getCycles().stream()
                .flatMap(cycle -> cycle.getDays().stream())
                .filter(day -> day.getDate().equals(date))
                .findFirst().orElseThrow();
    }
}
```

- [x] **Step 2: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=RequestNoteTest,RequestTest`
Expected: FAIL（コンパイルエラー：`RequestNote` などが存在しない）

- [x] **Step 3: Entity・Mapper・Repository・備考の正規化を実装する**

`src/main/java/jp/bk/shiftmanager/entity/ShiftRequest.java`：

```java
package jp.bk.shiftmanager.entity;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;

/** シフト希望の申請（1人1日1件） */
@Data
public class ShiftRequest {
    private Long id;
    private Long userId;
    private LocalDate workDate;
    private LocalTime startTime;
    private LocalTime endTime;
    /** 備考（なければnull） */
    private String note;
}
```

`src/main/java/jp/bk/shiftmanager/mapper/ShiftRequestMapper.java`：

```java
package jp.bk.shiftmanager.mapper;

import java.time.LocalDate;
import java.util.List;
import jp.bk.shiftmanager.entity.ShiftRequest;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ShiftRequestMapper {

    @Select("""
            SELECT * FROM shift_requests
            WHERE user_id = #{userId} AND work_date BETWEEN #{from} AND #{to}
            ORDER BY work_date
            """)
    List<ShiftRequest> findByUserAndPeriod(@Param("userId") long userId, @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** 同じ日の申請があれば上書きする */
    @Insert("""
            INSERT INTO shift_requests (user_id, work_date, start_time, end_time, note)
            VALUES (#{userId}, #{workDate}, #{startTime}, #{endTime}, #{note})
            ON CONFLICT (user_id, work_date) DO UPDATE
            SET start_time = EXCLUDED.start_time, end_time = EXCLUDED.end_time,
                note = EXCLUDED.note, updated_at = now()
            """)
    void upsert(ShiftRequest request);

    @Delete("DELETE FROM shift_requests WHERE user_id = #{userId} AND work_date = #{date}")
    int delete(@Param("userId") long userId, @Param("date") LocalDate date);
}
```

`src/main/java/jp/bk/shiftmanager/repository/ShiftRequestRepository.java`：

```java
package jp.bk.shiftmanager.repository;

import java.time.LocalDate;
import java.util.List;
import jp.bk.shiftmanager.entity.ShiftRequest;
import jp.bk.shiftmanager.mapper.ShiftRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ShiftRequestRepository {

    private final ShiftRequestMapper shiftRequestMapper;

    public List<ShiftRequest> findByUserAndPeriod(long userId, LocalDate from, LocalDate to) {
        return shiftRequestMapper.findByUserAndPeriod(userId, from, to);
    }

    public void upsert(ShiftRequest request) {
        shiftRequestMapper.upsert(request);
    }

    public void delete(long userId, LocalDate date) {
        shiftRequestMapper.delete(userId, date);
    }
}
```

`src/main/java/jp/bk/shiftmanager/util/RequestNote.java`：

```java
package jp.bk.shiftmanager.util;

import jp.bk.shiftmanager.exception.BusinessException;

/** 申請の備考 */
public final class RequestNote {

    public static final int MAX_LENGTH = 200;

    private RequestNote() {
    }

    /** 前後の空白を除き、空ならnull。200文字（DBの文字数）を超えたら入力エラー */
    public static String normalize(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String stripped = note.strip();
        if (stripped.codePointCount(0, stripped.length()) > MAX_LENGTH) {
            throw new BusinessException("備考は200文字以内で入力してください");
        }
        return stripped;
    }
}
```

- [x] **Step 4: Form・DTOを実装する**

`src/main/java/jp/bk/shiftmanager/form/RequestDayForm.java`：

```java
package jp.bk.shiftmanager.form;

import lombok.Data;

/** 申請画面の1日分の入力。検証はServiceで行う */
@Data
public class RequestDayForm {
    /** yyyy-MM-dd */
    private String date;
    /** HH:mm。空なら申請なし */
    private String startTime;
    private String endTime;
    private String note;
}
```

`src/main/java/jp/bk/shiftmanager/form/RequestMonthForm.java`：

```java
package jp.bk.shiftmanager.form;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 申請画面の1か月分の入力（締切前の日だけが送信される） */
@Data
public class RequestMonthForm {
    /** yyyy-MM */
    private String month;
    private List<RequestDayForm> days = new ArrayList<>();
}
```

`src/main/java/jp/bk/shiftmanager/dto/RequestDayView.java`：

```java
package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import lombok.Data;

/** 申請画面の1日（1行） */
@Data
public class RequestDayView {
    private LocalDate date;
    /** 例：3（土） */
    private String label;
    /** フォームの添字。締切済みの日はnull（入力欄を出さず、送信もしない） */
    private Integer index;
    /** HH:mm。申請がなければnull */
    private String startTime;
    private String endTime;
    private String note;
}
```

`src/main/java/jp/bk/shiftmanager/dto/RequestCycleView.java`：

```java
package jp.bk.shiftmanager.dto;

import java.util.List;
import jp.bk.shiftmanager.util.Cycle;
import lombok.Data;

/** 申請画面の1サイクル分 */
@Data
public class RequestCycleView {
    private Cycle cycle;
    /** 例：10/11〜10/20 */
    private String label;
    /** 例：10/6（火） */
    private String deadlineLabel;
    /** 締切前でスタッフが編集できるか */
    private boolean open;
    private List<RequestDayView> days;
}
```

`src/main/java/jp/bk/shiftmanager/dto/RequestMonthView.java`：

```java
package jp.bk.shiftmanager.dto;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jp.bk.shiftmanager.form.RequestDayForm;
import jp.bk.shiftmanager.form.RequestMonthForm;
import lombok.Data;

/** 申請画面の1か月分 */
@Data
public class RequestMonthView {
    private YearMonth month;
    /** 申請できる月（切り替えタブ） */
    private List<YearMonth> months;
    private List<RequestCycleView> cycles;

    /** 保存できなかった入力を画面に戻す（締切済みの日には戻さない） */
    public void applyInput(RequestMonthForm form) {
        Map<String, RequestDayForm> inputs = new HashMap<>();
        for (RequestDayForm input : form.getDays()) {
            if (input != null && input.getDate() != null) {
                inputs.put(input.getDate(), input);
            }
        }
        for (RequestCycleView cycle : cycles) {
            for (RequestDayView day : cycle.getDays()) {
                RequestDayForm input = inputs.get(day.getDate().toString());
                if (day.getIndex() != null && input != null) {
                    day.setStartTime(input.getStartTime());
                    day.setEndTime(input.getEndTime());
                    day.setNote(input.getNote());
                }
            }
        }
    }
}
```

- [x] **Step 5: Serviceを実装する**

`src/main/java/jp/bk/shiftmanager/service/RequestService.java`：

```java
package jp.bk.shiftmanager.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import jp.bk.shiftmanager.dto.RequestCycleView;
import jp.bk.shiftmanager.dto.RequestDayView;
import jp.bk.shiftmanager.dto.RequestMonthView;
import jp.bk.shiftmanager.entity.ShiftRequest;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.RequestDayForm;
import jp.bk.shiftmanager.form.RequestMonthForm;
import jp.bk.shiftmanager.repository.AppSettingRepository;
import jp.bk.shiftmanager.repository.ShiftRequestRepository;
import jp.bk.shiftmanager.util.Cycle;
import jp.bk.shiftmanager.util.DateLabels;
import jp.bk.shiftmanager.util.RequestNote;
import jp.bk.shiftmanager.util.TimeRange;
import jp.bk.shiftmanager.util.TimeSlots;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** スタッフ本人によるシフト希望の申請 */
@Service
@RequiredArgsConstructor
public class RequestService {

    private static final String INVALID_DATE = "不正な日付です";

    private final Clock clock;
    private final AppSettingRepository appSettingRepository;
    private final ShiftRequestRepository shiftRequestRepository;

    /** スタッフが申請できる月（今月〜翌々月） */
    public List<YearMonth> requestMonths() {
        YearMonth now = YearMonth.now(clock);
        return List.of(now, now.plusMonths(1), now.plusMonths(2));
    }

    /** 画面から指定された月。不正・範囲外なら今月 */
    public YearMonth resolveMonth(String text) {
        List<YearMonth> months = requestMonths();
        if (text != null) {
            try {
                YearMonth month = YearMonth.parse(text);
                if (months.contains(month)) {
                    return month;
                }
            } catch (DateTimeParseException e) {
                // 今月を表示する
            }
        }
        return months.get(0);
    }

    /** 申請画面の1か月分 */
    public RequestMonthView getMonth(long userId, YearMonth month) {
        LocalDate today = LocalDate.now(clock);
        int daysBefore = appSettingRepository.getDeadlineDaysBefore();
        Map<LocalDate, ShiftRequest> requests = shiftRequestRepository
                .findByUserAndPeriod(userId, month.atDay(1), month.atEndOfMonth()).stream()
                .collect(Collectors.toMap(ShiftRequest::getWorkDate, request -> request));

        int index = 0;
        List<RequestCycleView> cycles = new ArrayList<>();
        for (Cycle cycle : Cycle.ofMonth(month)) {
            RequestCycleView cycleView = new RequestCycleView();
            cycleView.setCycle(cycle);
            cycleView.setLabel(cycle.label());
            cycleView.setDeadlineLabel(DateLabels.monthDayWeek(cycle.deadline(daysBefore)));
            cycleView.setOpen(cycle.isOpen(today, daysBefore));
            List<RequestDayView> days = new ArrayList<>();
            for (LocalDate date : cycle.dates()) {
                RequestDayView day = new RequestDayView();
                day.setDate(date);
                day.setLabel(DateLabels.dayWeek(date));
                if (cycleView.isOpen()) {
                    day.setIndex(index++);
                }
                ShiftRequest request = requests.get(date);
                if (request != null) {
                    day.setStartTime(TimeSlots.format(request.getStartTime()));
                    day.setEndTime(TimeSlots.format(request.getEndTime()));
                    day.setNote(request.getNote());
                }
                days.add(day);
            }
            cycleView.setDays(days);
            cycles.add(cycleView);
        }

        RequestMonthView view = new RequestMonthView();
        view.setMonth(month);
        view.setMonths(requestMonths());
        view.setCycles(cycles);
        return view;
    }

    /**
     * 1か月分の申請を一括保存する。1件でも不正があれば何も保存しない。
     * 時刻も備考も空の日は申請を削除する。送信されなかった日は変更しない
     */
    @Transactional
    public void saveMonth(long userId, RequestMonthForm form) {
        YearMonth month = parseRequestMonth(form.getMonth());
        LocalDate today = LocalDate.now(clock);
        int daysBefore = appSettingRepository.getDeadlineDaysBefore();

        Map<LocalDate, RequestDayForm> inputs = parseDays(form, month);
        for (LocalDate date : inputs.keySet()) {
            checkOpen(Cycle.of(date), today, daysBefore);
        }

        // 先に全件を検証し、すべて正しい場合だけ保存する
        List<ShiftRequest> saves = new ArrayList<>();
        List<LocalDate> deletes = new ArrayList<>();
        for (Map.Entry<LocalDate, RequestDayForm> entry : inputs.entrySet()) {
            LocalDate date = entry.getKey();
            RequestDayForm input = entry.getValue();
            try {
                if (!hasInput(input)) {
                    deletes.add(date);
                } else {
                    saves.add(toRequest(userId, date, input));
                }
            } catch (BusinessException e) {
                throw new BusinessException(DateLabels.monthDay(date) + "：" + e.getMessage());
            }
        }
        deletes.forEach(date -> shiftRequestRepository.delete(userId, date));
        saves.forEach(shiftRequestRepository::upsert);
    }

    private YearMonth parseRequestMonth(String text) {
        try {
            YearMonth month = YearMonth.parse(text == null ? "" : text);
            if (requestMonths().contains(month)) {
                return month;
            }
        } catch (DateTimeParseException e) {
            // 下で入力エラーにする
        }
        throw new BusinessException("申請できない月です");
    }

    /** 日付の昇順に並べる（エラーは早い日付から報告する） */
    private Map<LocalDate, RequestDayForm> parseDays(RequestMonthForm form, YearMonth month) {
        Map<LocalDate, RequestDayForm> inputs = new TreeMap<>();
        for (RequestDayForm input : form.getDays()) {
            if (input == null) {
                continue;
            }
            LocalDate date = parseDate(input.getDate());
            if (!YearMonth.from(date).equals(month) || inputs.containsKey(date)) {
                throw new BusinessException(INVALID_DATE);
            }
            inputs.put(date, input);
        }
        return inputs;
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text == null ? "" : text);
        } catch (DateTimeParseException e) {
            throw new BusinessException(INVALID_DATE);
        }
    }

    private void checkOpen(Cycle cycle, LocalDate today, int daysBefore) {
        if (!cycle.isOpen(today, daysBefore)) {
            throw new BusinessException(cycle.label() + "は締切を過ぎたため変更できません。変更は管理者に伝えてください");
        }
    }

    /** 時刻か備考のどれかが入力されているか */
    private boolean hasInput(RequestDayForm input) {
        return !isBlank(input.getStartTime()) || !isBlank(input.getEndTime()) || !isBlank(input.getNote());
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private ShiftRequest toRequest(long userId, LocalDate date, RequestDayForm input) {
        TimeRange range = TimeRange.parse(input.getStartTime(), input.getEndTime());
        ShiftRequest request = new ShiftRequest();
        request.setUserId(userId);
        request.setWorkDate(date);
        request.setStartTime(range.start());
        request.setEndTime(range.end());
        request.setNote(RequestNote.normalize(input.getNote()));
        return request;
    }
}
```

- [x] **Step 6: コントローラーを実装する**

`src/main/java/jp/bk/shiftmanager/controller/RequestController.java`：

```java
package jp.bk.shiftmanager.controller;

import java.time.YearMonth;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.dto.RequestMonthView;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.RequestMonthForm;
import jp.bk.shiftmanager.service.RequestService;
import jp.bk.shiftmanager.util.TimeSlots;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** シフト希望の申請（スタッフ本人） */
@Controller
@RequestMapping("/requests")
@RequiredArgsConstructor
public class RequestController {

    private static final String VIEW = "requests/month";

    private final RequestService requestService;

    @GetMapping
    public String show(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String month,
            Model model) {
        YearMonth target = requestService.resolveMonth(month);
        model.addAttribute("view", requestService.getMonth(me.getId(), target));
        addOptions(model);
        return VIEW;
    }

    @PostMapping
    public String save(@AuthenticationPrincipal LoginUser me, @ModelAttribute RequestMonthForm form, Model model,
            RedirectAttributes redirectAttributes) {
        YearMonth target = requestService.resolveMonth(form.getMonth());
        try {
            requestService.saveMonth(me.getId(), form);
        } catch (BusinessException e) {
            // 1か月分の入力を消さないよう、リダイレクトせずに表示し直す
            RequestMonthView view = requestService.getMonth(me.getId(), target);
            view.applyInput(form);
            model.addAttribute("view", view);
            model.addAttribute("error", e.getMessage());
            addOptions(model);
            return VIEW;
        }
        redirectAttributes.addFlashAttribute("message", "登録しました");
        redirectAttributes.addAttribute("month", target.toString());
        return "redirect:/requests";
    }

    private void addOptions(Model model) {
        model.addAttribute("timeOptions", TimeSlots.OPTIONS);
    }
}
```

- [x] **Step 7: テンプレートを作成し、ヘッダーに「申請」を追加する**

`src/main/resources/templates/requests/month.html`：

```html
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout :: head('シフト希望の申請')}"></head>
<body class="min-h-screen bg-stone-50 text-stone-900">
<header th:replace="~{layout :: header}"></header>
<main class="mx-auto max-w-md px-4 pb-28 pt-4">
  <h1 class="mb-3 text-lg font-bold">シフト希望の申請</h1>
  <nav class="mb-4 flex gap-2">
    <a th:each="m : ${view.months}" th:href="@{/requests(month=${m})}" th:text="|${m.monthValue}月|"
       class="flex-1 rounded border px-3 py-2 text-center"
       th:classappend="${m == view.month} ? 'border-amber-700 bg-amber-700 font-bold text-white' : 'border-stone-300 bg-white'">10月</a>
  </nav>
  <div th:replace="~{layout :: flash}"></div>

  <form id="request-form" th:action="@{/requests}" method="post">
    <input type="hidden" name="month" th:value="${view.month}">
    <section th:each="c : ${view.cycles}" class="mb-6">
      <div class="mb-2 flex items-baseline justify-between">
        <h2 class="font-bold" th:text="${c.label}">10/11〜10/20</h2>
        <p class="text-sm" th:classappend="${c.open} ? 'text-stone-600' : 'text-stone-400'"
           th:text="${c.open} ? |締切 ${c.deadlineLabel}| : '締切済み'">締切 10/6（火）</p>
      </div>
      <div class="rounded-lg border border-stone-200 bg-white divide-y divide-stone-100">
        <div th:each="d : ${c.days}" class="px-2 py-2" th:classappend="${c.open} ? '' : 'bg-stone-200 text-stone-500'">
          <th:block th:if="${d.index != null}">
            <input type="hidden" th:name="|days[${d.index}].date|" th:value="${d.date}">
            <div class="grid grid-cols-[3.5rem_1fr_1fr] items-center gap-1">
              <span class="text-sm" th:text="${d.label}">3（土）</span>
              <select th:name="|days[${d.index}].startTime|" class="input mt-0 px-1" aria-label="IN">
                <option value="">IN</option>
                <option th:each="t : ${timeOptions}" th:value="${t}" th:text="${t}"
                        th:selected="${t == d.startTime}"></option>
              </select>
              <select th:name="|days[${d.index}].endTime|" class="input mt-0 px-1" aria-label="OUT">
                <option value="">OUT</option>
                <option th:each="t : ${timeOptions}" th:value="${t}" th:text="${t}"
                        th:selected="${t == d.endTime}"></option>
              </select>
            </div>
            <input th:name="|days[${d.index}].note|" th:value="${d.note}" maxlength="200" placeholder="備考"
                   class="input py-1">
          </th:block>
          <th:block th:if="${d.index == null}">
            <div class="grid grid-cols-[3.5rem_1fr_1fr] items-center gap-1 text-sm">
              <span th:text="${d.label}">3（土）</span>
              <span th:text="${d.startTime} ?: '--:--'">09:00</span>
              <span th:text="${d.endTime} ?: '--:--'">17:00</span>
            </div>
            <p th:if="${d.note != null}" class="mt-1 text-sm" th:text="${d.note}"></p>
          </th:block>
        </div>
      </div>
    </section>

    <div class="fixed inset-x-0 bottom-0 border-t border-stone-200 bg-white p-3">
      <div class="mx-auto max-w-md">
        <button class="btn-primary w-full">登録する</button>
      </div>
    </div>
  </form>
</main>
</body>
</html>
```

`src/main/resources/templates/layout.html` のヘッダーで、次の行の直後に

```html
    <a th:href="@{/}" class="font-bold">シフト管理</a>
```

次の行を追加する：

```html
    <a th:href="@{/requests}" class="hover:underline">申請</a>
```

- [x] **Step 8: テストが通ることを確認する**

Run: `./mvnw test -Dtest=RequestNoteTest,RequestTest`
Expected: PASS

- [x] **Step 9: 全テストを実行してコミットする**

Run: `./mvnw test`
Expected: PASS

```bash
git add -A
git commit -m "feat: シフト希望の申請画面（月ごとの一括登録と締切）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [x] **Step 10: この計画ファイルのTask 3のチェックボックスをすべて `[x]` にしてコミットし、停止してユーザーに報告する**

```bash
git add docs/superpowers/plans/2026-09-26-plan2-requests.md
git commit -m "docs: Plan 2 Task 3 完了

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: 「この期間は出勤できない」・パターン選択・未保存の確認

**Files:**
- Create: `mapper/CycleUnavailableMapper.java`、`repository/CycleUnavailableRepository.java`、`src/main/resources/static/js/requests.js`
- Modify: `mapper/ShiftRequestMapper.java`・`repository/ShiftRequestRepository.java`（期間削除を追加）、`form/RequestMonthForm.java`、`dto/RequestCycleView.java`、`dto/RequestMonthView.java`
- Modify（全体を置き換え）: `service/RequestService.java`、`controller/RequestController.java`、`src/main/resources/templates/requests/month.html`
- Modify（テスト）: `TestData.java`（`unavailable(...)` を追加）
- Test: `controller/RequestUnavailableTest.java`

**Interfaces:**
- Consumes: `service.PatternService#findMine(long)`（Task 2）、Task 3 の `RequestService`・DTO・Form、`TestData#pattern`・`#request`
- Produces:
  - `mapper.CycleUnavailableMapper#findStarts(long userId, LocalDate from, LocalDate to): List<LocalDate>`、`#insert(long userId, LocalDate cycleStart)`、`#delete(long userId, LocalDate cycleStart): int`
  - `repository.CycleUnavailableRepository`：`findStarts`、`insert`、`delete(...): boolean`（削除した行があればtrue。Task 6で使う）
  - `mapper.ShiftRequestMapper#deleteByUserAndPeriod(long userId, LocalDate from, LocalDate to): int`、`ShiftRequestRepository#deleteByUserAndPeriod`
  - `form.RequestMonthForm#unavailableCycles: List<String>`（チェックしたサイクルの開始日 yyyy-MM-dd）
  - `dto.RequestCycleView#unavailable: boolean`
  - テスト：`TestData#unavailable(User, LocalDate cycleStart)`

- [x] **Step 1: テストデータの作成メソッドを追加し、失敗するテストを書く**

`TestData.java` にメソッドを追加する：

```java
    /** 「この期間は出勤できない」にする */
    public void unavailable(User user, LocalDate cycleStart) {
        jdbc.update("INSERT INTO cycle_unavailable (user_id, cycle_start) VALUES (?, ?)", user.getId(), cycleStart);
    }
```

`src/test/java/jp/bk/shiftmanager/controller/RequestUnavailableTest.java`：

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
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.dto.RequestCycleView;
import jp.bk.shiftmanager.dto.RequestMonthView;
import jp.bk.shiftmanager.entity.ShiftRequest;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.CycleUnavailableMapper;
import jp.bk.shiftmanager.mapper.ShiftRequestMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 「この期間は出勤できない」とパターン選択（今日は2026-09-25、締切はサイクル開始日の5日前） */
class RequestUnavailableTest extends IntegrationTestBase {

    private static final LocalDate OCT1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT11 = LocalDate.of(2026, 10, 11);
    private static final LocalDate OCT12 = LocalDate.of(2026, 10, 12);
    private static final LocalDate OCT31 = LocalDate.of(2026, 10, 31);

    @Autowired
    CycleUnavailableMapper cycleUnavailableMapper;

    @Autowired
    ShiftRequestMapper shiftRequestMapper;

    User taro;
    LoginUser me;

    @BeforeEach
    void setUp() {
        taro = data.user("taro", "山田太郎", false);
        me = data.login(taro);
    }

    @Test
    void チェックすると登録されその期間の申請は削除される() throws Exception {
        data.request(taro, OCT12, "09:00", "17:00", null);

        mvc.perform(post("/requests").with(user(me)).with(csrf())
                        .param("month", "2026-10")
                        .param("unavailableCycles", "2026-10-11")
                        .param("days[0].date", "2026-10-01").param("days[0].startTime", "09:00")
                        .param("days[0].endTime", "17:00").param("days[0].note", ""))
                .andExpect(redirectedUrl("/requests?month=2026-10"));

        assertThat(cycleUnavailableMapper.findStarts(taro.getId(), OCT1, OCT31)).containsExactly(OCT11);
        assertThat(shiftRequestMapper.findByUserAndPeriod(taro.getId(), OCT1, OCT31))
                .extracting(ShiftRequest::getWorkDate).containsExactly(OCT1);
    }

    @Test
    void チェックを外すと解除される() throws Exception {
        data.unavailable(taro, OCT11);

        mvc.perform(post("/requests").with(user(me)).with(csrf()).param("month", "2026-10"))
                .andExpect(redirectedUrl("/requests?month=2026-10"));

        assertThat(cycleUnavailableMapper.findStarts(taro.getId(), OCT1, OCT31)).isEmpty();
    }

    @Test
    void チェックした期間に入力があると何も保存されない() throws Exception {
        mvc.perform(post("/requests").with(user(me)).with(csrf())
                        .param("month", "2026-10")
                        .param("unavailableCycles", "2026-10-11")
                        .param("days[0].date", "2026-10-12").param("days[0].startTime", "09:00")
                        .param("days[0].endTime", "17:00").param("days[0].note", ""))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error",
                        "10/11〜10/20は「この期間は出勤できない」にチェックがあるため、申請を入力できません"));

        assertThat(cycleUnavailableMapper.findStarts(taro.getId(), OCT1, OCT31)).isEmpty();
        assertThat(shiftRequestMapper.findByUserAndPeriod(taro.getId(), OCT1, OCT31)).isEmpty();
    }

    @Test
    void エラーで表示し直すときもチェックが残る() throws Exception {
        MvcResult result = mvc.perform(post("/requests").with(user(me)).with(csrf())
                        .param("month", "2026-10")
                        .param("unavailableCycles", "2026-10-21")
                        .param("days[0].date", "2026-10-01").param("days[0].startTime", "07:00")
                        .param("days[0].endTime", "17:00").param("days[0].note", ""))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(view(result).getCycles().get(2).isUnavailable()).isTrue();
    }

    @Test
    void 締切済みの期間のチェックは他の保存で消えない() throws Exception {
        data.today(LocalDate.of(2026, 9, 27));
        data.unavailable(taro, OCT1);

        mvc.perform(post("/requests").with(user(me)).with(csrf())
                        .param("month", "2026-10")
                        .param("days[0].date", "2026-10-12").param("days[0].startTime", "09:00")
                        .param("days[0].endTime", "17:00").param("days[0].note", ""))
                .andExpect(redirectedUrl("/requests?month=2026-10"));

        assertThat(cycleUnavailableMapper.findStarts(taro.getId(), OCT1, OCT31)).containsExactly(OCT1);
    }

    @Test
    void 締切済みや不正な期間はチェックできない() throws Exception {
        data.today(LocalDate.of(2026, 9, 27));

        mvc.perform(post("/requests").with(user(me)).with(csrf())
                        .param("month", "2026-10").param("unavailableCycles", "2026-10-01"))
                .andExpect(model().attribute("error",
                        "10/1〜10/10は締切を過ぎたため変更できません。変更は管理者に伝えてください"));
        mvc.perform(post("/requests").with(user(me)).with(csrf())
                        .param("month", "2026-10").param("unavailableCycles", "2026-10-05"))
                .andExpect(model().attribute("error", "不正な日付です"));
        mvc.perform(post("/requests").with(user(me)).with(csrf())
                        .param("month", "2026-10").param("unavailableCycles", "2026-11-01"))
                .andExpect(model().attribute("error", "不正な日付です"));

        assertThat(cycleUnavailableMapper.findStarts(taro.getId(), OCT1, LocalDate.of(2026, 11, 30))).isEmpty();
    }

    @Test
    void 画面にチェック状態と自分のパターンが表示される() throws Exception {
        User hanako = data.user("hanako", "佐藤花子", false);
        data.unavailable(taro, OCT11);
        data.pattern(taro, "朝", "08:00", "13:00");
        data.pattern(hanako, "他人用", "17:00", "22:00");

        MvcResult result = mvc.perform(get("/requests").param("month", "2026-10").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("data-start=\"08:00\"")))
                .andExpect(content().string(Matchers.containsString("この期間は出勤できない")))
                .andExpect(content().string(Matchers.containsString("/js/requests.js")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("他人用"))))
                .andReturn();

        assertThat(view(result).getCycles()).extracting(RequestCycleView::isUnavailable)
                .containsExactly(false, true, false);
    }

    private static RequestMonthView view(MvcResult result) {
        return (RequestMonthView) result.getModelAndView().getModel().get("view");
    }
}
```

- [x] **Step 2: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=RequestUnavailableTest`
Expected: FAIL（コンパイルエラー：`CycleUnavailableMapper` が存在しない）

- [x] **Step 3: Mapper・Repositoryを実装する**

`src/main/java/jp/bk/shiftmanager/mapper/CycleUnavailableMapper.java`：

```java
package jp.bk.shiftmanager.mapper;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 「この期間は出勤できない」チェック */
@Mapper
public interface CycleUnavailableMapper {

    /** 開始日が期間内にある「出勤できない」サイクルの開始日 */
    @Select("""
            SELECT cycle_start FROM cycle_unavailable
            WHERE user_id = #{userId} AND cycle_start BETWEEN #{from} AND #{to}
            ORDER BY cycle_start
            """)
    List<LocalDate> findStarts(@Param("userId") long userId, @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Insert("""
            INSERT INTO cycle_unavailable (user_id, cycle_start) VALUES (#{userId}, #{cycleStart})
            ON CONFLICT DO NOTHING
            """)
    void insert(@Param("userId") long userId, @Param("cycleStart") LocalDate cycleStart);

    @Delete("DELETE FROM cycle_unavailable WHERE user_id = #{userId} AND cycle_start = #{cycleStart}")
    int delete(@Param("userId") long userId, @Param("cycleStart") LocalDate cycleStart);
}
```

`src/main/java/jp/bk/shiftmanager/repository/CycleUnavailableRepository.java`：

```java
package jp.bk.shiftmanager.repository;

import java.time.LocalDate;
import java.util.List;
import jp.bk.shiftmanager.mapper.CycleUnavailableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CycleUnavailableRepository {

    private final CycleUnavailableMapper cycleUnavailableMapper;

    public List<LocalDate> findStarts(long userId, LocalDate from, LocalDate to) {
        return cycleUnavailableMapper.findStarts(userId, from, to);
    }

    public void insert(long userId, LocalDate cycleStart) {
        cycleUnavailableMapper.insert(userId, cycleStart);
    }

    /** 解除した行があればtrue */
    public boolean delete(long userId, LocalDate cycleStart) {
        return cycleUnavailableMapper.delete(userId, cycleStart) > 0;
    }
}
```

`mapper/ShiftRequestMapper.java` にメソッドを追加する：

```java
    @Delete("DELETE FROM shift_requests WHERE user_id = #{userId} AND work_date BETWEEN #{from} AND #{to}")
    int deleteByUserAndPeriod(@Param("userId") long userId, @Param("from") LocalDate from,
            @Param("to") LocalDate to);
```

`repository/ShiftRequestRepository.java` にメソッドを追加する：

```java
    public void deleteByUserAndPeriod(long userId, LocalDate from, LocalDate to) {
        shiftRequestMapper.deleteByUserAndPeriod(userId, from, to);
    }
```

- [x] **Step 4: Form・DTOにチェック状態を追加する**

`form/RequestMonthForm.java` にフィールドを追加する：

```java
    /** 「この期間は出勤できない」にチェックしたサイクルの開始日（yyyy-MM-dd）。締切前のサイクルだけが送信される */
    private List<String> unavailableCycles = new ArrayList<>();
```

`dto/RequestCycleView.java` の `open` の下にフィールドを追加する：

```java
    /** 「この期間は出勤できない」にチェックしているか */
    private boolean unavailable;
```

`dto/RequestMonthView.java` の `applyInput` を次の内容に置き換える（import に `java.util.HashSet`・`java.util.Set` を追加）：

```java
    /** 保存できなかった入力を画面に戻す（締切済みのサイクルには戻さない） */
    public void applyInput(RequestMonthForm form) {
        Map<String, RequestDayForm> inputs = new HashMap<>();
        for (RequestDayForm input : form.getDays()) {
            if (input != null && input.getDate() != null) {
                inputs.put(input.getDate(), input);
            }
        }
        Set<String> checked = new HashSet<>(form.getUnavailableCycles());
        for (RequestCycleView cycle : cycles) {
            if (!cycle.isOpen()) {
                continue;
            }
            cycle.setUnavailable(checked.contains(cycle.getCycle().start().toString()));
            for (RequestDayView day : cycle.getDays()) {
                RequestDayForm input = inputs.get(day.getDate().toString());
                if (input != null) {
                    day.setStartTime(input.getStartTime());
                    day.setEndTime(input.getEndTime());
                    day.setNote(input.getNote());
                }
            }
        }
    }
```

- [x] **Step 5: Serviceを置き換える**

`src/main/java/jp/bk/shiftmanager/service/RequestService.java` を次の内容に置き換える：

```java
package jp.bk.shiftmanager.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import jp.bk.shiftmanager.dto.RequestCycleView;
import jp.bk.shiftmanager.dto.RequestDayView;
import jp.bk.shiftmanager.dto.RequestMonthView;
import jp.bk.shiftmanager.entity.ShiftRequest;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.RequestDayForm;
import jp.bk.shiftmanager.form.RequestMonthForm;
import jp.bk.shiftmanager.repository.AppSettingRepository;
import jp.bk.shiftmanager.repository.CycleUnavailableRepository;
import jp.bk.shiftmanager.repository.ShiftRequestRepository;
import jp.bk.shiftmanager.util.Cycle;
import jp.bk.shiftmanager.util.DateLabels;
import jp.bk.shiftmanager.util.RequestNote;
import jp.bk.shiftmanager.util.TimeRange;
import jp.bk.shiftmanager.util.TimeSlots;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** スタッフ本人によるシフト希望の申請 */
@Service
@RequiredArgsConstructor
public class RequestService {

    private static final String INVALID_DATE = "不正な日付です";

    private final Clock clock;
    private final AppSettingRepository appSettingRepository;
    private final ShiftRequestRepository shiftRequestRepository;
    private final CycleUnavailableRepository cycleUnavailableRepository;

    /** スタッフが申請できる月（今月〜翌々月） */
    public List<YearMonth> requestMonths() {
        YearMonth now = YearMonth.now(clock);
        return List.of(now, now.plusMonths(1), now.plusMonths(2));
    }

    /** 画面から指定された月。不正・範囲外なら今月 */
    public YearMonth resolveMonth(String text) {
        List<YearMonth> months = requestMonths();
        if (text != null) {
            try {
                YearMonth month = YearMonth.parse(text);
                if (months.contains(month)) {
                    return month;
                }
            } catch (DateTimeParseException e) {
                // 今月を表示する
            }
        }
        return months.get(0);
    }

    /** 申請画面の1か月分 */
    public RequestMonthView getMonth(long userId, YearMonth month) {
        LocalDate today = LocalDate.now(clock);
        int daysBefore = appSettingRepository.getDeadlineDaysBefore();
        Map<LocalDate, ShiftRequest> requests = shiftRequestRepository
                .findByUserAndPeriod(userId, month.atDay(1), month.atEndOfMonth()).stream()
                .collect(Collectors.toMap(ShiftRequest::getWorkDate, request -> request));
        Set<LocalDate> unavailable = new HashSet<>(
                cycleUnavailableRepository.findStarts(userId, month.atDay(1), month.atEndOfMonth()));

        int index = 0;
        List<RequestCycleView> cycles = new ArrayList<>();
        for (Cycle cycle : Cycle.ofMonth(month)) {
            RequestCycleView cycleView = new RequestCycleView();
            cycleView.setCycle(cycle);
            cycleView.setLabel(cycle.label());
            cycleView.setDeadlineLabel(DateLabels.monthDayWeek(cycle.deadline(daysBefore)));
            cycleView.setOpen(cycle.isOpen(today, daysBefore));
            cycleView.setUnavailable(unavailable.contains(cycle.start()));
            List<RequestDayView> days = new ArrayList<>();
            for (LocalDate date : cycle.dates()) {
                RequestDayView day = new RequestDayView();
                day.setDate(date);
                day.setLabel(DateLabels.dayWeek(date));
                if (cycleView.isOpen()) {
                    day.setIndex(index++);
                }
                ShiftRequest request = requests.get(date);
                if (request != null) {
                    day.setStartTime(TimeSlots.format(request.getStartTime()));
                    day.setEndTime(TimeSlots.format(request.getEndTime()));
                    day.setNote(request.getNote());
                }
                days.add(day);
            }
            cycleView.setDays(days);
            cycles.add(cycleView);
        }

        RequestMonthView view = new RequestMonthView();
        view.setMonth(month);
        view.setMonths(requestMonths());
        view.setCycles(cycles);
        return view;
    }

    /**
     * 1か月分の申請を一括保存する。1件でも不正があれば何も保存しない。
     * 時刻も備考も空の日は申請を削除する。送信されなかった日は変更しない。
     * 「この期間は出勤できない」は締切前のサイクルだけ反映する
     */
    @Transactional
    public void saveMonth(long userId, RequestMonthForm form) {
        YearMonth month = parseRequestMonth(form.getMonth());
        LocalDate today = LocalDate.now(clock);
        int daysBefore = appSettingRepository.getDeadlineDaysBefore();

        Map<LocalDate, RequestDayForm> inputs = parseDays(form, month);
        for (LocalDate date : inputs.keySet()) {
            checkOpen(Cycle.of(date), today, daysBefore);
        }
        Set<LocalDate> unavailableStarts = parseUnavailable(form, month, today, daysBefore);

        // 先に全件を検証し、すべて正しい場合だけ保存する
        List<ShiftRequest> saves = new ArrayList<>();
        List<LocalDate> deletes = new ArrayList<>();
        for (Map.Entry<LocalDate, RequestDayForm> entry : inputs.entrySet()) {
            LocalDate date = entry.getKey();
            RequestDayForm input = entry.getValue();
            Cycle cycle = Cycle.of(date);
            if (unavailableStarts.contains(cycle.start()) && hasInput(input)) {
                throw new BusinessException(
                        cycle.label() + "は「この期間は出勤できない」にチェックがあるため、申請を入力できません");
            }
            try {
                if (!hasInput(input)) {
                    deletes.add(date);
                } else {
                    saves.add(toRequest(userId, date, input));
                }
            } catch (BusinessException e) {
                throw new BusinessException(DateLabels.monthDay(date) + "：" + e.getMessage());
            }
        }

        for (Cycle cycle : Cycle.ofMonth(month)) {
            if (!cycle.isOpen(today, daysBefore)) {
                continue;
            }
            if (unavailableStarts.contains(cycle.start())) {
                // 出勤できない期間には申請を持たない
                cycleUnavailableRepository.insert(userId, cycle.start());
                shiftRequestRepository.deleteByUserAndPeriod(userId, cycle.start(), cycle.end());
            } else {
                cycleUnavailableRepository.delete(userId, cycle.start());
            }
        }
        deletes.forEach(date -> shiftRequestRepository.delete(userId, date));
        saves.forEach(shiftRequestRepository::upsert);
    }

    private YearMonth parseRequestMonth(String text) {
        try {
            YearMonth month = YearMonth.parse(text == null ? "" : text);
            if (requestMonths().contains(month)) {
                return month;
            }
        } catch (DateTimeParseException e) {
            // 下で入力エラーにする
        }
        throw new BusinessException("申請できない月です");
    }

    /** 日付の昇順に並べる（エラーは早い日付から報告する） */
    private Map<LocalDate, RequestDayForm> parseDays(RequestMonthForm form, YearMonth month) {
        Map<LocalDate, RequestDayForm> inputs = new TreeMap<>();
        for (RequestDayForm input : form.getDays()) {
            if (input == null) {
                continue;
            }
            LocalDate date = parseDate(input.getDate());
            if (!YearMonth.from(date).equals(month) || inputs.containsKey(date)) {
                throw new BusinessException(INVALID_DATE);
            }
            inputs.put(date, input);
        }
        return inputs;
    }

    /** チェックされたサイクルの開始日。対象月の締切前のサイクルの開始日だけを受け付ける */
    private Set<LocalDate> parseUnavailable(RequestMonthForm form, YearMonth month, LocalDate today,
            int daysBefore) {
        Set<LocalDate> starts = new HashSet<>();
        for (String text : form.getUnavailableCycles()) {
            LocalDate start = parseDate(text);
            Cycle cycle = Cycle.of(start);
            if (!cycle.start().equals(start) || !YearMonth.from(start).equals(month)) {
                throw new BusinessException(INVALID_DATE);
            }
            checkOpen(cycle, today, daysBefore);
            starts.add(start);
        }
        return starts;
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text == null ? "" : text);
        } catch (DateTimeParseException e) {
            throw new BusinessException(INVALID_DATE);
        }
    }

    private void checkOpen(Cycle cycle, LocalDate today, int daysBefore) {
        if (!cycle.isOpen(today, daysBefore)) {
            throw new BusinessException(cycle.label() + "は締切を過ぎたため変更できません。変更は管理者に伝えてください");
        }
    }

    /** 時刻か備考のどれかが入力されているか */
    private boolean hasInput(RequestDayForm input) {
        return !isBlank(input.getStartTime()) || !isBlank(input.getEndTime()) || !isBlank(input.getNote());
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private ShiftRequest toRequest(long userId, LocalDate date, RequestDayForm input) {
        TimeRange range = TimeRange.parse(input.getStartTime(), input.getEndTime());
        ShiftRequest request = new ShiftRequest();
        request.setUserId(userId);
        request.setWorkDate(date);
        request.setStartTime(range.start());
        request.setEndTime(range.end());
        request.setNote(RequestNote.normalize(input.getNote()));
        return request;
    }
}
```

- [x] **Step 6: コントローラーを置き換える（パターンを画面に渡す）**

`src/main/java/jp/bk/shiftmanager/controller/RequestController.java` を次の内容に置き換える：

```java
package jp.bk.shiftmanager.controller;

import java.time.YearMonth;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.dto.RequestMonthView;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.RequestMonthForm;
import jp.bk.shiftmanager.service.PatternService;
import jp.bk.shiftmanager.service.RequestService;
import jp.bk.shiftmanager.util.TimeSlots;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** シフト希望の申請（スタッフ本人） */
@Controller
@RequestMapping("/requests")
@RequiredArgsConstructor
public class RequestController {

    private static final String VIEW = "requests/month";

    private final RequestService requestService;
    private final PatternService patternService;

    @GetMapping
    public String show(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String month,
            Model model) {
        YearMonth target = requestService.resolveMonth(month);
        model.addAttribute("view", requestService.getMonth(me.getId(), target));
        addOptions(model, me);
        return VIEW;
    }

    @PostMapping
    public String save(@AuthenticationPrincipal LoginUser me, @ModelAttribute RequestMonthForm form, Model model,
            RedirectAttributes redirectAttributes) {
        YearMonth target = requestService.resolveMonth(form.getMonth());
        try {
            requestService.saveMonth(me.getId(), form);
        } catch (BusinessException e) {
            // 1か月分の入力を消さないよう、リダイレクトせずに表示し直す
            RequestMonthView view = requestService.getMonth(me.getId(), target);
            view.applyInput(form);
            model.addAttribute("view", view);
            model.addAttribute("error", e.getMessage());
            addOptions(model, me);
            return VIEW;
        }
        redirectAttributes.addFlashAttribute("message", "登録しました");
        redirectAttributes.addAttribute("month", target.toString());
        return "redirect:/requests";
    }

    private void addOptions(Model model, LoginUser me) {
        model.addAttribute("timeOptions", TimeSlots.OPTIONS);
        model.addAttribute("patterns", patternService.findMine(me.getId()));
    }
}
```

- [x] **Step 7: テンプレートを置き換え、JavaScriptを作成する**

`src/main/resources/templates/requests/month.html` を次の内容に置き換える：

```html
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout :: head('シフト希望の申請')}"></head>
<body class="min-h-screen bg-stone-50 text-stone-900">
<header th:replace="~{layout :: header}"></header>
<main class="mx-auto max-w-md px-4 pb-28 pt-4">
  <h1 class="mb-3 text-lg font-bold">シフト希望の申請</h1>
  <nav class="mb-4 flex gap-2">
    <a th:each="m : ${view.months}" th:href="@{/requests(month=${m})}" th:text="|${m.monthValue}月|" data-month-link
       class="flex-1 rounded border px-3 py-2 text-center"
       th:classappend="${m == view.month} ? 'border-amber-700 bg-amber-700 font-bold text-white' : 'border-stone-300 bg-white'">10月</a>
  </nav>
  <div th:replace="~{layout :: flash}"></div>
  <p th:if="${#lists.isEmpty(patterns)}" class="mb-4 text-sm text-stone-600">
    よく使う時間帯は <a th:href="@{/mypage/patterns}" class="underline">申請パターン</a> に登録すると選ぶだけで入力できます。
  </p>

  <form id="request-form" th:action="@{/requests}" method="post">
    <input type="hidden" name="month" th:value="${view.month}">
    <section th:each="c : ${view.cycles}" class="mb-6" data-cycle>
      <div class="mb-2 flex items-baseline justify-between">
        <h2 class="font-bold" th:text="${c.label}">10/11〜10/20</h2>
        <p class="text-sm" th:classappend="${c.open} ? 'text-stone-600' : 'text-stone-400'"
           th:text="${c.open} ? |締切 ${c.deadlineLabel}| : '締切済み'">締切 10/6（火）</p>
      </div>
      <label th:if="${c.open}" class="mb-2 flex items-center gap-2">
        <input type="checkbox" name="unavailableCycles" th:value="${c.cycle.start()}" th:checked="${c.unavailable}"
               data-unavailable class="h-5 w-5">
        この期間は出勤できない
      </label>
      <p th:if="${!c.open and c.unavailable}" class="mb-2 text-sm text-stone-500">この期間は出勤できない</p>
      <div class="rounded-lg border border-stone-200 bg-white divide-y divide-stone-100">
        <div th:each="d : ${c.days}" data-row class="px-2 py-2"
             th:classappend="${c.open} ? '' : 'bg-stone-200 text-stone-500'">
          <th:block th:if="${d.index != null}">
            <input type="hidden" th:name="|days[${d.index}].date|" th:value="${d.date}">
            <div class="grid grid-cols-[3rem_1fr_1fr_1fr] items-center gap-1">
              <span class="text-sm" th:text="${d.label}">3（土）</span>
              <select data-pattern class="input mt-0 px-1" aria-label="パターン">
                <option value="">パターン</option>
                <option th:each="p : ${patterns}" th:text="${p.name}"
                        th:data-start="${#temporals.format(p.startTime, 'HH:mm')}"
                        th:data-end="${#temporals.format(p.endTime, 'HH:mm')}">朝</option>
              </select>
              <select data-in th:name="|days[${d.index}].startTime|" class="input mt-0 px-1" aria-label="IN">
                <option value="">IN</option>
                <option th:each="t : ${timeOptions}" th:value="${t}" th:text="${t}"
                        th:selected="${t == d.startTime}"></option>
              </select>
              <select data-out th:name="|days[${d.index}].endTime|" class="input mt-0 px-1" aria-label="OUT">
                <option value="">OUT</option>
                <option th:each="t : ${timeOptions}" th:value="${t}" th:text="${t}"
                        th:selected="${t == d.endTime}"></option>
              </select>
            </div>
            <input th:name="|days[${d.index}].note|" th:value="${d.note}" maxlength="200" placeholder="備考"
                   class="input py-1">
          </th:block>
          <th:block th:if="${d.index == null}">
            <div class="grid grid-cols-[3rem_1fr_1fr_1fr] items-center gap-1 text-sm">
              <span th:text="${d.label}">3（土）</span>
              <span></span>
              <span th:text="${d.startTime} ?: '--:--'">09:00</span>
              <span th:text="${d.endTime} ?: '--:--'">17:00</span>
            </div>
            <p th:if="${d.note != null}" class="mt-1 text-sm" th:text="${d.note}"></p>
          </th:block>
        </div>
      </div>
    </section>

    <div class="fixed inset-x-0 bottom-0 border-t border-stone-200 bg-white p-3">
      <div class="mx-auto max-w-md">
        <button class="btn-primary w-full">登録する</button>
      </div>
    </div>
  </form>
</main>
<script th:src="@{/js/requests.js}" defer></script>
</body>
</html>
```

`src/main/resources/static/js/requests.js`：

```js
// 申請画面：パターンの自動入力・「この期間は出勤できない」・未保存の確認
(() => {
  const form = document.getElementById('request-form');
  if (!form) {
    return;
  }

  let dirty = false;
  form.addEventListener('input', () => { dirty = true; });
  form.addEventListener('change', () => { dirty = true; });
  form.addEventListener('submit', () => { dirty = false; });

  // パターンを選ぶと同じ行のIN・OUTを入力する（その後の手修正は自由）
  form.querySelectorAll('select[data-pattern]').forEach((select) => {
    select.addEventListener('change', () => {
      const option = select.selectedOptions[0];
      if (!option || !option.dataset.start) {
        return;
      }
      const row = select.closest('[data-row]');
      row.querySelector('select[data-in]').value = option.dataset.start;
      row.querySelector('select[data-out]').value = option.dataset.end;
    });
  });

  // 「この期間は出勤できない」にチェックした期間は入力欄を無効にする（無効な欄は送信されない）
  const applyUnavailable = (checkbox) => {
    const section = checkbox.closest('[data-cycle]');
    section.querySelectorAll('[data-row] select, [data-row] input').forEach((element) => {
      element.disabled = checkbox.checked;
    });
  };
  form.querySelectorAll('input[data-unavailable]').forEach((checkbox) => {
    applyUnavailable(checkbox);
    checkbox.addEventListener('change', () => applyUnavailable(checkbox));
  });

  // 未保存の入力がある状態で月を切り替えるときは確認する
  document.querySelectorAll('a[data-month-link]').forEach((link) => {
    link.addEventListener('click', (event) => {
      if (dirty && !window.confirm('保存していない入力があります。移動しますか？')) {
        event.preventDefault();
      }
    });
  });
})();
```

- [x] **Step 8: テストが通ることを確認する**

Run: `./mvnw test -Dtest=RequestUnavailableTest,RequestTest`
Expected: PASS

- [x] **Step 9: ブラウザで動作を確認する**

`npm run build` の後、`./mvnw spring-boot:run` で起動し、スタッフでログインして `/requests` を開き、次を確認する（確認できない場合はユーザーに報告して確認を依頼する）：
- パターンを選ぶとIN・OUTが入る
- 「この期間は出勤できない」にチェックするとその期間の入力欄が無効になり、外すと戻る
- 入力を変更してから月のタブを押すと確認が出る。「登録する」では確認が出ない
- スマートフォン幅（375px）で1行が横にはみ出さない

- [x] **Step 10: 全テストを実行してコミットする**

Run: `./mvnw test`
Expected: PASS

```bash
git add -A
git commit -m "feat: 「この期間は出勤できない」とパターン選択・未保存の確認

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [x] **Step 11: この計画ファイルのTask 4のチェックボックスをすべて `[x]` にしてコミットし、停止してユーザーに報告する**

```bash
git add docs/superpowers/plans/2026-09-26-plan2-requests.md
git commit -m "docs: Plan 2 Task 4 完了

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: 申請一覧（管理者）

**Files:**
- Create: `dto/RequestTableView.java`、`dto/RequestTableRow.java`、`dto/RequestCell.java`、`service/AdminRequestService.java`、`controller/AdminRequestController.java`
- Create: `src/main/resources/templates/admin/requests/table.html`
- Modify: `mapper/ShiftRequestMapper.java`・`repository/ShiftRequestRepository.java`（期間内の全員分）、`mapper/CycleUnavailableMapper.java`・`repository/CycleUnavailableRepository.java`（サイクルの全員分）、`src/main/resources/templates/layout.html`（ヘッダーに「申請一覧」）
- Test: `controller/AdminRequestTest.java`

**Interfaces:**
- Consumes: `util.Cycle`・`DateLabels`・`TimeSlots`（Task 1）、`ShiftRequestRepository`（Task 3）、`CycleUnavailableRepository`（Task 4）、`repository.UserRepository#findStaffRows(): List<StaffRow>`・`dto.StaffRow`（Plan 1）、`TestData#request`・`#unavailable`
- Produces:
  - `mapper.ShiftRequestMapper#findByPeriod(LocalDate from, LocalDate to)`、`ShiftRequestRepository#findByPeriod`
  - `mapper.CycleUnavailableMapper#findUserIds(LocalDate cycleStart): List<Long>`、`CycleUnavailableRepository#findUserIds`
  - `dto.RequestTableView`（`cycle, label, deadlineLabel, previousStart, nextStart, dateLabels, rows`）、`dto.RequestTableRow`（`userId, name, positionName, submitted, unavailable, cells`）、`dto.RequestCell`（`date, startTime, endTime, note`）
  - `service.AdminRequestService#resolveCycle(String date): Cycle`、`#getTable(Cycle): RequestTableView`
  - 画面：`GET /admin/requests?date=yyyy-MM-dd`（その日を含むサイクル。省略時は今日の次のサイクル）

- [ ] **Step 1: 失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/controller/AdminRequestTest.java`：

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
import jp.bk.shiftmanager.dto.RequestTableRow;
import jp.bk.shiftmanager.dto.RequestTableView;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.util.Cycle;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 管理者の申請一覧（今日は2026-09-25） */
class AdminRequestTest extends IntegrationTestBase {

    private static final LocalDate OCT1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT2 = LocalDate.of(2026, 10, 2);

    User boss;
    LoginUser admin;
    User taro;

    @BeforeEach
    void setUp() {
        boss = data.user("boss", "店長", true);
        admin = data.login(boss);
        taro = data.user("taro", "山田太郎", false);
        Position kitchen = data.position("キッチン", 1);
        data.assignPosition(taro, kitchen);
    }

    @Test
    void サイクルの全員の申請が表で表示される() throws Exception {
        data.request(taro, OCT2, "09:00", "17:00", "遅れるかも");

        MvcResult result = mvc.perform(get("/admin/requests").param("date", "2026-10-01").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("山田太郎")))
                .andExpect(content().string(Matchers.containsString("09:00")))
                .andExpect(content().string(Matchers.containsString("遅れるかも")))
                .andReturn();

        RequestTableView view = view(result);
        assertThat(view.getLabel()).isEqualTo("10/1〜10/10");
        assertThat(view.getDateLabels()).hasSize(10).startsWith("1（木）");
        RequestTableRow row = row(view, "山田太郎");
        assertThat(row.getPositionName()).isEqualTo("キッチン");
        assertThat(row.getCells()).hasSize(10);
        assertThat(row.getCells().get(1).getStartTime()).isEqualTo("09:00");
        assertThat(row.getCells().get(1).getEndTime()).isEqualTo("17:00");
        assertThat(row.getCells().get(0).getStartTime()).isNull();
    }

    @Test
    void 申請か出勤できないがあれば提出済み() throws Exception {
        User hanako = data.user("hanako", "佐藤花子", false);
        User jiro = data.user("jiro", "鈴木次郎", false);
        data.request(taro, OCT1, "09:00", "17:00", null);
        data.unavailable(hanako, OCT1);
        // 別のサイクルの申請は数えない
        data.request(jiro, LocalDate.of(2026, 9, 30), "09:00", "17:00", null);

        RequestTableView view = view(mvc.perform(get("/admin/requests").param("date", "2026-10-01")
                .with(user(admin))).andReturn());

        assertThat(row(view, "山田太郎").isSubmitted()).isTrue();
        assertThat(row(view, "佐藤花子").isSubmitted()).isTrue();
        assertThat(row(view, "佐藤花子").isUnavailable()).isTrue();
        assertThat(row(view, "鈴木次郎").isSubmitted()).isFalse();
        // 管理者も出勤者なので表に載る
        assertThat(row(view, "店長").isSubmitted()).isFalse();
    }

    @Test
    void 無効化したスタッフは表示されない() throws Exception {
        User retired = data.user("retired", "退職者", false);
        data.disable(retired);

        RequestTableView view = view(mvc.perform(get("/admin/requests").with(user(admin))).andReturn());

        assertThat(view.getRows()).extracting(RequestTableRow::getName).doesNotContain("退職者");
    }

    @Test
    void 日付の指定がない又は不正なら今日の次のサイクルを表示する() throws Exception {
        Cycle expected = Cycle.of(OCT1);

        assertThat(view(mvc.perform(get("/admin/requests").with(user(admin))).andReturn()).getCycle())
                .isEqualTo(expected);
        assertThat(view(mvc.perform(get("/admin/requests").param("date", "abc").with(user(admin))).andReturn())
                .getCycle()).isEqualTo(expected);
        assertThat(view(mvc.perform(get("/admin/requests").param("date", "2026-10-15").with(user(admin)))
                .andReturn()).getCycle()).isEqualTo(Cycle.of(LocalDate.of(2026, 10, 11)));
    }

    @Test
    void 前後のサイクルへ移動できる() throws Exception {
        RequestTableView view = view(mvc.perform(get("/admin/requests").param("date", "2026-10-01")
                .with(user(admin))).andReturn());

        assertThat(view.getPreviousStart()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(view.getNextStart()).isEqualTo(LocalDate.of(2026, 10, 11));
    }

    @Test
    void 一般スタッフは申請一覧を表示できない() throws Exception {
        mvc.perform(get("/admin/requests").with(user(data.login(taro))))
                .andExpect(status().isForbidden());
    }

    private static RequestTableView view(MvcResult result) {
        return (RequestTableView) result.getModelAndView().getModel().get("view");
    }

    private static RequestTableRow row(RequestTableView view, String name) {
        return view.getRows().stream().filter(row -> row.getName().equals(name)).findFirst().orElseThrow();
    }
}
```

- [ ] **Step 2: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=AdminRequestTest`
Expected: FAIL（コンパイルエラー：`RequestTableView` などが存在しない）

- [ ] **Step 3: Mapper・Repositoryにメソッドを追加する**

`mapper/ShiftRequestMapper.java` に追加する：

```java
    @Select("SELECT * FROM shift_requests WHERE work_date BETWEEN #{from} AND #{to} ORDER BY user_id, work_date")
    List<ShiftRequest> findByPeriod(@Param("from") LocalDate from, @Param("to") LocalDate to);
```

`repository/ShiftRequestRepository.java` に追加する：

```java
    public List<ShiftRequest> findByPeriod(LocalDate from, LocalDate to) {
        return shiftRequestMapper.findByPeriod(from, to);
    }
```

`mapper/CycleUnavailableMapper.java` に追加する：

```java
    /** そのサイクルを「出勤できない」にしたスタッフ */
    @Select("SELECT user_id FROM cycle_unavailable WHERE cycle_start = #{cycleStart}")
    List<Long> findUserIds(@Param("cycleStart") LocalDate cycleStart);
```

`repository/CycleUnavailableRepository.java` に追加する：

```java
    public List<Long> findUserIds(LocalDate cycleStart) {
        return cycleUnavailableMapper.findUserIds(cycleStart);
    }
```

- [ ] **Step 4: DTOを実装する**

`src/main/java/jp/bk/shiftmanager/dto/RequestCell.java`：

```java
package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 申請一覧の1マス（申請がなければ時刻・備考はnull） */
@Data
@AllArgsConstructor
public class RequestCell {
    private LocalDate date;
    private String startTime;
    private String endTime;
    private String note;
}
```

`src/main/java/jp/bk/shiftmanager/dto/RequestTableRow.java`：

```java
package jp.bk.shiftmanager.dto;

import java.util.List;
import lombok.Data;

/** 申請一覧の1スタッフ分 */
@Data
public class RequestTableRow {
    private long userId;
    private String name;
    private String positionName;
    /** サイクル内に1日以上の申請がある、または「この期間は出勤できない」 */
    private boolean submitted;
    private boolean unavailable;
    private List<RequestCell> cells;
}
```

`src/main/java/jp/bk/shiftmanager/dto/RequestTableView.java`：

```java
package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import java.util.List;
import jp.bk.shiftmanager.util.Cycle;
import lombok.Data;

/** 申請一覧（1サイクル分の日付 × スタッフ） */
@Data
public class RequestTableView {
    private Cycle cycle;
    /** 例：10/1〜10/10 */
    private String label;
    /** 例：9/26（土） */
    private String deadlineLabel;
    private LocalDate previousStart;
    private LocalDate nextStart;
    /** 列見出し（例：1（木）） */
    private List<String> dateLabels;
    private List<RequestTableRow> rows;
}
```

- [ ] **Step 5: Service・コントローラーを実装する**

`src/main/java/jp/bk/shiftmanager/service/AdminRequestService.java`：

```java
package jp.bk.shiftmanager.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jp.bk.shiftmanager.dto.RequestCell;
import jp.bk.shiftmanager.dto.RequestTableRow;
import jp.bk.shiftmanager.dto.RequestTableView;
import jp.bk.shiftmanager.dto.StaffRow;
import jp.bk.shiftmanager.entity.ShiftRequest;
import jp.bk.shiftmanager.repository.AppSettingRepository;
import jp.bk.shiftmanager.repository.CycleUnavailableRepository;
import jp.bk.shiftmanager.repository.ShiftRequestRepository;
import jp.bk.shiftmanager.repository.UserRepository;
import jp.bk.shiftmanager.util.Cycle;
import jp.bk.shiftmanager.util.DateLabels;
import jp.bk.shiftmanager.util.TimeSlots;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 管理者による申請の閲覧・代理編集 */
@Service
@RequiredArgsConstructor
public class AdminRequestService {

    private final Clock clock;
    private final AppSettingRepository appSettingRepository;
    private final UserRepository userRepository;
    private final ShiftRequestRepository shiftRequestRepository;
    private final CycleUnavailableRepository cycleUnavailableRepository;

    /** 指定日を含むサイクル。指定がない・不正なら今日の次のサイクル */
    public Cycle resolveCycle(String date) {
        if (date != null) {
            try {
                return Cycle.of(LocalDate.parse(date));
            } catch (DateTimeParseException e) {
                // 次のサイクルを表示する
            }
        }
        return Cycle.of(LocalDate.now(clock)).next();
    }

    /** サイクル内の有効なスタッフ全員の申請表 */
    public RequestTableView getTable(Cycle cycle) {
        Map<Long, Map<LocalDate, ShiftRequest>> requests = shiftRequestRepository
                .findByPeriod(cycle.start(), cycle.end()).stream()
                .collect(Collectors.groupingBy(ShiftRequest::getUserId,
                        Collectors.toMap(ShiftRequest::getWorkDate, request -> request)));
        Set<Long> unavailable = new HashSet<>(cycleUnavailableRepository.findUserIds(cycle.start()));
        List<LocalDate> dates = cycle.dates();

        List<RequestTableRow> rows = new ArrayList<>();
        for (StaffRow staff : userRepository.findStaffRows()) {
            if (!staff.isEnabled()) {
                continue;
            }
            Map<LocalDate, ShiftRequest> byDate = requests.getOrDefault(staff.getId(), Map.of());
            RequestTableRow row = new RequestTableRow();
            row.setUserId(staff.getId());
            row.setName(staff.getName());
            row.setPositionName(staff.getPositionName());
            row.setUnavailable(unavailable.contains(staff.getId()));
            row.setSubmitted(row.isUnavailable() || !byDate.isEmpty());
            row.setCells(dates.stream().map(date -> toCell(date, byDate.get(date))).toList());
            rows.add(row);
        }

        RequestTableView view = new RequestTableView();
        view.setCycle(cycle);
        view.setLabel(cycle.label());
        view.setDeadlineLabel(DateLabels.monthDayWeek(cycle.deadline(appSettingRepository.getDeadlineDaysBefore())));
        view.setPreviousStart(cycle.previous().start());
        view.setNextStart(cycle.next().start());
        view.setDateLabels(dates.stream().map(DateLabels::dayWeek).toList());
        view.setRows(rows);
        return view;
    }

    private RequestCell toCell(LocalDate date, ShiftRequest request) {
        if (request == null) {
            return new RequestCell(date, null, null, null);
        }
        return new RequestCell(date, TimeSlots.format(request.getStartTime()),
                TimeSlots.format(request.getEndTime()), request.getNote());
    }
}
```

`src/main/java/jp/bk/shiftmanager/controller/AdminRequestController.java`：

```java
package jp.bk.shiftmanager.controller;

import jp.bk.shiftmanager.service.AdminRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 申請の閲覧・代理編集（管理者） */
@Controller
@RequestMapping("/admin/requests")
@RequiredArgsConstructor
public class AdminRequestController {

    private final AdminRequestService adminRequestService;

    @GetMapping
    public String table(@RequestParam(required = false) String date, Model model) {
        model.addAttribute("view", adminRequestService.getTable(adminRequestService.resolveCycle(date)));
        return "admin/requests/table";
    }
}
```

- [ ] **Step 6: テンプレートを作成し、ヘッダーに「申請一覧」を追加する**

`src/main/resources/templates/admin/requests/table.html`：

```html
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout :: head('申請一覧')}"></head>
<body class="min-h-screen bg-stone-50 text-stone-900">
<header th:replace="~{layout :: header}"></header>
<main class="px-4 py-6">
  <div class="mb-4 flex flex-wrap items-center gap-3">
    <h1 class="text-lg font-bold">申請一覧</h1>
    <a th:href="@{/admin/requests(date=${view.previousStart})}" class="btn-secondary px-3 py-1">&lt; 前</a>
    <span class="font-bold" th:text="${view.label}">10/1〜10/10</span>
    <a th:href="@{/admin/requests(date=${view.nextStart})}" class="btn-secondary px-3 py-1">次 &gt;</a>
    <span class="text-sm text-stone-600" th:text="|締切 ${view.deadlineLabel}|">締切 9/26（土）</span>
    <span class="ml-auto flex items-center gap-1 text-sm">
      <span class="inline-block h-3 w-3 rounded border border-red-200 bg-red-100"></span>未提出
    </span>
  </div>
  <div th:replace="~{layout :: flash}"></div>

  <div class="overflow-x-auto rounded-lg border border-stone-200 bg-white">
    <table class="min-w-full border-collapse text-sm">
      <thead>
      <tr class="bg-stone-100">
        <th class="sticky left-0 z-10 bg-stone-100 px-3 py-2 text-left">名前</th>
        <th th:each="label : ${view.dateLabels}" class="whitespace-nowrap px-2 py-2" th:text="${label}">1（木）</th>
      </tr>
      </thead>
      <tbody>
      <tr th:each="row : ${view.rows}" class="border-t border-stone-100"
          th:classappend="${row.submitted} ? '' : 'bg-red-50'">
        <th class="sticky left-0 z-10 whitespace-nowrap px-3 py-2 text-left font-normal"
            th:classappend="${row.submitted} ? 'bg-white' : 'bg-red-50'">
          <span th:text="${row.name}">山田太郎</span>
          <span class="block text-xs text-stone-500" th:text="${row.positionName}">キッチン</span>
          <span th:if="${row.unavailable}" class="block text-xs text-red-700">この期間は出勤できない</span>
        </th>
        <td th:each="cell : ${row.cells}" class="whitespace-nowrap px-2 py-1 text-center">
          <th:block th:if="${cell.startTime != null}">
            <span th:text="${cell.startTime}">09:00</span><br>
            <span th:text="${cell.endTime}">17:00</span>
          </th:block>
          <span th:if="${cell.note != null}" th:title="${cell.note}" class="cursor-help text-amber-700">※</span>
        </td>
      </tr>
      </tbody>
    </table>
  </div>
  <p th:if="${#lists.isEmpty(view.rows)}" class="mt-4 text-sm text-stone-500">有効なスタッフがいません。</p>
  <p class="mt-2 text-xs text-stone-500">※ にカーソルを合わせると備考を表示します。</p>
</main>
</body>
</html>
```

`src/main/resources/templates/layout.html` のヘッダーで、次の行の直後に

```html
    <th:block sec:authorize="hasRole('ADMIN')">
```

次の行を追加する：

```html
      <a th:href="@{/admin/requests}" class="hover:underline">申請一覧</a>
```

- [ ] **Step 7: テストが通ることを確認する**

Run: `./mvnw test -Dtest=AdminRequestTest`
Expected: PASS（6件）

- [ ] **Step 8: 全テストを実行してコミットする**

Run: `./mvnw test`
Expected: PASS

```bash
git add -A
git commit -m "feat: 管理者の申請一覧（サイクル×スタッフ、未提出の表示）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 9: この計画ファイルのTask 5のチェックボックスをすべて `[x]` にしてコミットし、停止してユーザーに報告する**

```bash
git add docs/superpowers/plans/2026-09-26-plan2-requests.md
git commit -m "docs: Plan 2 Task 5 完了

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: 申請の代理編集（管理者）

**Files:**
- Create: `form/RequestEditForm.java`、`dto/RequestEditView.java`、`src/main/resources/templates/admin/requests/edit.html`
- Modify: `mapper/ShiftRequestMapper.java`・`repository/ShiftRequestRepository.java`（1件取得）
- Modify（全体を置き換え）: `service/AdminRequestService.java`、`controller/AdminRequestController.java`、`src/main/resources/templates/admin/requests/table.html`
- Test: `controller/AdminRequestEditTest.java`

**Interfaces:**
- Consumes: Task 5 の `AdminRequestService`・`RequestTableView` 等、`util.TimeRange`・`RequestNote`・`Cycle`・`DateLabels`・`TimeSlots`、`CycleUnavailableRepository#delete(long, LocalDate): boolean`（Task 4）、`UserRepository#findById(long): Optional<User>`（Plan 1）
- Produces:
  - `mapper.ShiftRequestMapper#find(long userId, LocalDate date): ShiftRequest`、`ShiftRequestRepository#find(...): Optional<ShiftRequest>`
  - `form.RequestEditForm`（`Long userId, String date, startTime, endTime, note`）、`dto.RequestEditView`（`userId, userName, date, dateLabel, exists, startTime, endTime, note`）
  - `service.AdminRequestService#getForEdit(long userId, LocalDate date)`、`#save(RequestEditForm): boolean`（「出勤できない」を解除したらtrue）、`#delete(long userId, LocalDate date)`
  - 画面：`GET /admin/requests/edit?userId=&date=`、`POST /admin/requests/edit`、`POST /admin/requests/delete`

- [ ] **Step 1: 失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/controller/AdminRequestEditTest.java`：

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
import java.time.LocalTime;
import java.util.List;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.dto.RequestEditView;
import jp.bk.shiftmanager.entity.ShiftRequest;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.CycleUnavailableMapper;
import jp.bk.shiftmanager.mapper.ShiftRequestMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 管理者による申請の代理編集（今日は2026-09-25） */
class AdminRequestEditTest extends IntegrationTestBase {

    private static final LocalDate SEP22 = LocalDate.of(2026, 9, 22);
    private static final LocalDate OCT1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT2 = LocalDate.of(2026, 10, 2);

    @Autowired
    ShiftRequestMapper shiftRequestMapper;

    @Autowired
    CycleUnavailableMapper cycleUnavailableMapper;

    LoginUser admin;
    User taro;

    @BeforeEach
    void setUp() {
        admin = data.login(data.user("boss", "店長", true));
        taro = data.user("taro", "山田太郎", false);
    }

    @Test
    void 一覧のマスから編集画面へ行ける() throws Exception {
        mvc.perform(get("/admin/requests").param("date", "2026-10-01").with(user(admin)))
                .andExpect(content().string(Matchers.containsString(
                        "/admin/requests/edit?userId=" + taro.getId() + "&amp;date=2026-10-02")));
    }

    @Test
    void 編集画面に現在の申請が表示される() throws Exception {
        data.request(taro, OCT2, "09:00", "17:00", "遅れるかも");

        MvcResult result = mvc.perform(get("/admin/requests/edit")
                        .param("userId", taro.getId().toString()).param("date", "2026-10-02").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("山田太郎")))
                .andReturn();

        RequestEditView view = (RequestEditView) result.getModelAndView().getModel().get("view");
        assertThat(view.isExists()).isTrue();
        assertThat(view.getStartTime()).isEqualTo("09:00");
        assertThat(view.getNote()).isEqualTo("遅れるかも");
        assertThat(view.getDateLabel()).isEqualTo("10/2（金）");
    }

    @Test
    void 締切後の日も代理で登録でき一覧へ戻る() throws Exception {
        mvc.perform(post("/admin/requests/edit").with(user(admin)).with(csrf())
                        .param("userId", taro.getId().toString()).param("date", "2026-09-22")
                        .param("startTime", "10:00").param("endTime", "18:00").param("note", " 電話で連絡あり "))
                .andExpect(redirectedUrl("/admin/requests?date=2026-09-22"))
                .andExpect(flash().attribute("message", "保存しました"));

        List<ShiftRequest> saved = shiftRequestMapper.findByUserAndPeriod(taro.getId(), SEP22, SEP22);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(saved.get(0).getNote()).isEqualTo("電話で連絡あり");
    }

    @Test
    void 既存の申請を上書きできる() throws Exception {
        data.request(taro, OCT2, "09:00", "17:00", "メモ");

        mvc.perform(post("/admin/requests/edit").with(user(admin)).with(csrf())
                        .param("userId", taro.getId().toString()).param("date", "2026-10-02")
                        .param("startTime", "12:00").param("endTime", "20:00").param("note", ""))
                .andExpect(redirectedUrl("/admin/requests?date=2026-10-02"));

        ShiftRequest saved = shiftRequestMapper.findByUserAndPeriod(taro.getId(), OCT2, OCT2).get(0);
        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(saved.getNote()).isNull();
    }

    @Test
    void 出勤できない期間に登録するとチェックが外れる() throws Exception {
        data.unavailable(taro, OCT1);

        mvc.perform(post("/admin/requests/edit").with(user(admin)).with(csrf())
                        .param("userId", taro.getId().toString()).param("date", "2026-10-02")
                        .param("startTime", "09:00").param("endTime", "17:00").param("note", ""))
                .andExpect(flash().attribute("message", "保存しました（「この期間は出勤できない」のチェックを外しました）"));

        assertThat(cycleUnavailableMapper.findStarts(taro.getId(), OCT1, OCT1)).isEmpty();
        assertThat(shiftRequestMapper.findByUserAndPeriod(taro.getId(), OCT2, OCT2)).hasSize(1);
    }

    @Test
    void 不正な時刻は保存されず編集画面へ戻る() throws Exception {
        for (String[] c : new String[][] {{"07:30", "12:00"}, {"13:00", "12:00"}, {"", "12:00"}}) {
            mvc.perform(post("/admin/requests/edit").with(user(admin)).with(csrf())
                            .param("userId", taro.getId().toString()).param("date", "2026-10-02")
                            .param("startTime", c[0]).param("endTime", c[1]).param("note", ""))
                    .andExpect(redirectedUrl("/admin/requests/edit?userId=" + taro.getId() + "&date=2026-10-02"))
                    .andExpect(flash().attributeExists("error"));
        }
        assertThat(shiftRequestMapper.findByUserAndPeriod(taro.getId(), OCT2, OCT2)).isEmpty();
    }

    @Test
    void 申請を削除できる() throws Exception {
        data.request(taro, OCT2, "09:00", "17:00", null);

        mvc.perform(post("/admin/requests/delete").with(user(admin)).with(csrf())
                        .param("userId", taro.getId().toString()).param("date", "2026-10-02"))
                .andExpect(redirectedUrl("/admin/requests?date=2026-10-02"))
                .andExpect(flash().attribute("message", "削除しました"));

        assertThat(shiftRequestMapper.findByUserAndPeriod(taro.getId(), OCT2, OCT2)).isEmpty();
    }

    @Test
    void 存在しないスタッフは編集できない() throws Exception {
        mvc.perform(get("/admin/requests/edit").param("userId", "99999").param("date", "2026-10-02")
                        .with(user(admin)))
                .andExpect(redirectedUrl("/admin/requests"))
                .andExpect(flash().attribute("error", "スタッフが見つかりません"));
    }

    @Test
    void 一般スタッフは代理編集できない() throws Exception {
        mvc.perform(post("/admin/requests/edit").with(user(data.login(taro))).with(csrf())
                        .param("userId", taro.getId().toString()).param("date", "2026-09-22")
                        .param("startTime", "10:00").param("endTime", "18:00").param("note", ""))
                .andExpect(status().isForbidden());

        assertThat(shiftRequestMapper.findByUserAndPeriod(taro.getId(), SEP22, SEP22)).isEmpty();
    }
}
```

- [ ] **Step 2: テストが失敗することを確認する**

Run: `./mvnw test -Dtest=AdminRequestEditTest`
Expected: FAIL（コンパイルエラー：`RequestEditView` が存在しない）

- [ ] **Step 3: Mapper・Repository・Form・DTOを実装する**

`mapper/ShiftRequestMapper.java` に追加する：

```java
    @Select("SELECT * FROM shift_requests WHERE user_id = #{userId} AND work_date = #{date}")
    ShiftRequest find(@Param("userId") long userId, @Param("date") LocalDate date);
```

`repository/ShiftRequestRepository.java` に追加する（import に `java.util.Optional`）：

```java
    public Optional<ShiftRequest> find(long userId, LocalDate date) {
        return Optional.ofNullable(shiftRequestMapper.find(userId, date));
    }
```

`src/main/java/jp/bk/shiftmanager/form/RequestEditForm.java`：

```java
package jp.bk.shiftmanager.form;

import lombok.Data;

/** 管理者による申請の代理編集。検証はServiceで行う */
@Data
public class RequestEditForm {
    private Long userId;
    /** yyyy-MM-dd */
    private String date;
    /** HH:mm */
    private String startTime;
    private String endTime;
    private String note;
}
```

`src/main/java/jp/bk/shiftmanager/dto/RequestEditView.java`：

```java
package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import lombok.Data;

/** 代理編集画面の表示内容 */
@Data
public class RequestEditView {
    private long userId;
    private String userName;
    private LocalDate date;
    /** 例：10/2（金） */
    private String dateLabel;
    /** 申請が登録済みか（削除ボタンの表示に使う） */
    private boolean exists;
    private String startTime;
    private String endTime;
    private String note;
}
```

- [ ] **Step 4: Serviceを置き換える**

`src/main/java/jp/bk/shiftmanager/service/AdminRequestService.java` を次の内容に置き換える：

```java
package jp.bk.shiftmanager.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jp.bk.shiftmanager.dto.RequestCell;
import jp.bk.shiftmanager.dto.RequestEditView;
import jp.bk.shiftmanager.dto.RequestTableRow;
import jp.bk.shiftmanager.dto.RequestTableView;
import jp.bk.shiftmanager.dto.StaffRow;
import jp.bk.shiftmanager.entity.ShiftRequest;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.RequestEditForm;
import jp.bk.shiftmanager.repository.AppSettingRepository;
import jp.bk.shiftmanager.repository.CycleUnavailableRepository;
import jp.bk.shiftmanager.repository.ShiftRequestRepository;
import jp.bk.shiftmanager.repository.UserRepository;
import jp.bk.shiftmanager.util.Cycle;
import jp.bk.shiftmanager.util.DateLabels;
import jp.bk.shiftmanager.util.RequestNote;
import jp.bk.shiftmanager.util.TimeRange;
import jp.bk.shiftmanager.util.TimeSlots;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理者による申請の閲覧・代理編集 */
@Service
@RequiredArgsConstructor
public class AdminRequestService {

    private static final String USER_NOT_FOUND = "スタッフが見つかりません";

    private final Clock clock;
    private final AppSettingRepository appSettingRepository;
    private final UserRepository userRepository;
    private final ShiftRequestRepository shiftRequestRepository;
    private final CycleUnavailableRepository cycleUnavailableRepository;

    /** 指定日を含むサイクル。指定がない・不正なら今日の次のサイクル */
    public Cycle resolveCycle(String date) {
        if (date != null) {
            try {
                return Cycle.of(LocalDate.parse(date));
            } catch (DateTimeParseException e) {
                // 次のサイクルを表示する
            }
        }
        return Cycle.of(LocalDate.now(clock)).next();
    }

    /** サイクル内の有効なスタッフ全員の申請表 */
    public RequestTableView getTable(Cycle cycle) {
        Map<Long, Map<LocalDate, ShiftRequest>> requests = shiftRequestRepository
                .findByPeriod(cycle.start(), cycle.end()).stream()
                .collect(Collectors.groupingBy(ShiftRequest::getUserId,
                        Collectors.toMap(ShiftRequest::getWorkDate, request -> request)));
        Set<Long> unavailable = new HashSet<>(cycleUnavailableRepository.findUserIds(cycle.start()));
        List<LocalDate> dates = cycle.dates();

        List<RequestTableRow> rows = new ArrayList<>();
        for (StaffRow staff : userRepository.findStaffRows()) {
            if (!staff.isEnabled()) {
                continue;
            }
            Map<LocalDate, ShiftRequest> byDate = requests.getOrDefault(staff.getId(), Map.of());
            RequestTableRow row = new RequestTableRow();
            row.setUserId(staff.getId());
            row.setName(staff.getName());
            row.setPositionName(staff.getPositionName());
            row.setUnavailable(unavailable.contains(staff.getId()));
            row.setSubmitted(row.isUnavailable() || !byDate.isEmpty());
            row.setCells(dates.stream().map(date -> toCell(date, byDate.get(date))).toList());
            rows.add(row);
        }

        RequestTableView view = new RequestTableView();
        view.setCycle(cycle);
        view.setLabel(cycle.label());
        view.setDeadlineLabel(DateLabels.monthDayWeek(cycle.deadline(appSettingRepository.getDeadlineDaysBefore())));
        view.setPreviousStart(cycle.previous().start());
        view.setNextStart(cycle.next().start());
        view.setDateLabels(dates.stream().map(DateLabels::dayWeek).toList());
        view.setRows(rows);
        return view;
    }

    /** 代理編集画面の表示内容 */
    public RequestEditView getForEdit(long userId, LocalDate date) {
        User user = findUser(userId);
        RequestEditView view = new RequestEditView();
        view.setUserId(userId);
        view.setUserName(user.getName());
        view.setDate(date);
        view.setDateLabel(DateLabels.monthDayWeek(date));
        shiftRequestRepository.find(userId, date).ifPresent(request -> {
            view.setExists(true);
            view.setStartTime(TimeSlots.format(request.getStartTime()));
            view.setEndTime(TimeSlots.format(request.getEndTime()));
            view.setNote(request.getNote());
        });
        return view;
    }

    /**
     * 管理者による代理登録（締切後も可）。
     * 「この期間は出勤できない」にしていたサイクルならチェックを外し、外した場合はtrueを返す
     */
    @Transactional
    public boolean save(RequestEditForm form) {
        if (form.getUserId() == null) {
            throw new BusinessException(USER_NOT_FOUND);
        }
        findUser(form.getUserId());
        LocalDate date = parseDate(form.getDate());
        TimeRange range = TimeRange.parse(form.getStartTime(), form.getEndTime());

        ShiftRequest request = new ShiftRequest();
        request.setUserId(form.getUserId());
        request.setWorkDate(date);
        request.setStartTime(range.start());
        request.setEndTime(range.end());
        request.setNote(RequestNote.normalize(form.getNote()));
        shiftRequestRepository.upsert(request);
        // 出勤できない期間には申請を持たない
        return cycleUnavailableRepository.delete(form.getUserId(), Cycle.of(date).start());
    }

    @Transactional
    public void delete(long userId, LocalDate date) {
        findUser(userId);
        shiftRequestRepository.delete(userId, date);
    }

    private User findUser(long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new BusinessException(USER_NOT_FOUND));
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text == null ? "" : text);
        } catch (DateTimeParseException e) {
            throw new BusinessException("不正な日付です");
        }
    }

    private RequestCell toCell(LocalDate date, ShiftRequest request) {
        if (request == null) {
            return new RequestCell(date, null, null, null);
        }
        return new RequestCell(date, TimeSlots.format(request.getStartTime()),
                TimeSlots.format(request.getEndTime()), request.getNote());
    }
}
```

- [ ] **Step 5: コントローラーを置き換える**

`src/main/java/jp/bk/shiftmanager/controller/AdminRequestController.java` を次の内容に置き換える：

```java
package jp.bk.shiftmanager.controller;

import java.time.LocalDate;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.RequestEditForm;
import jp.bk.shiftmanager.service.AdminRequestService;
import jp.bk.shiftmanager.util.TimeSlots;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 申請の閲覧・代理編集（管理者） */
@Controller
@RequestMapping("/admin/requests")
@RequiredArgsConstructor
public class AdminRequestController {

    private static final String REDIRECT_TABLE = "redirect:/admin/requests";

    private final AdminRequestService adminRequestService;

    @GetMapping
    public String table(@RequestParam(required = false) String date, Model model) {
        model.addAttribute("view", adminRequestService.getTable(adminRequestService.resolveCycle(date)));
        return "admin/requests/table";
    }

    @GetMapping("/edit")
    public String edit(@RequestParam long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Model model,
            RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("view", adminRequestService.getForEdit(userId, date));
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return REDIRECT_TABLE;
        }
        model.addAttribute("timeOptions", TimeSlots.OPTIONS);
        return "admin/requests/edit";
    }

    @PostMapping("/edit")
    public String save(@ModelAttribute RequestEditForm form, RedirectAttributes redirectAttributes) {
        try {
            boolean unavailableRemoved = adminRequestService.save(form);
            redirectAttributes.addFlashAttribute("message",
                    unavailableRemoved ? "保存しました（「この期間は出勤できない」のチェックを外しました）" : "保存しました");
            redirectAttributes.addAttribute("date", form.getDate());
            return REDIRECT_TABLE;
        } catch (BusinessException e) {
            // 入力は3項目のみのため、編集画面へ戻して選び直してもらう
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addAttribute("userId", form.getUserId());
            redirectAttributes.addAttribute("date", form.getDate());
            return "redirect:/admin/requests/edit";
        }
    }

    @PostMapping("/delete")
    public String delete(@RequestParam long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            RedirectAttributes redirectAttributes) {
        try {
            adminRequestService.delete(userId, date);
            redirectAttributes.addFlashAttribute("message", "削除しました");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        redirectAttributes.addAttribute("date", date.toString());
        return REDIRECT_TABLE;
    }
}
```

- [ ] **Step 6: 編集画面を作成し、一覧のマスをリンクにする**

`src/main/resources/templates/admin/requests/edit.html`：

```html
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout :: head('申請の代理編集')}"></head>
<body class="min-h-screen bg-stone-50 text-stone-900">
<header th:replace="~{layout :: header}"></header>
<main class="mx-auto max-w-md px-4 py-6">
  <h1 class="mb-4 text-lg font-bold">申請の代理編集</h1>
  <div th:replace="~{layout :: flash}"></div>
  <div class="card space-y-4">
    <p>
      <span class="font-bold" th:text="${view.userName}">山田太郎</span>
      <span class="ml-2" th:text="${view.dateLabel}">10/2（金）</span>
    </p>
    <form th:action="@{/admin/requests/edit}" method="post" class="space-y-3">
      <input type="hidden" name="userId" th:value="${view.userId}">
      <input type="hidden" name="date" th:value="${view.date}">
      <div class="grid grid-cols-2 gap-2">
        <label class="block">
          <span class="text-sm">IN</span>
          <select name="startTime" class="input px-1">
            <option value="">--:--</option>
            <option th:each="t : ${timeOptions}" th:value="${t}" th:text="${t}"
                    th:selected="${t == view.startTime}"></option>
          </select>
        </label>
        <label class="block">
          <span class="text-sm">OUT</span>
          <select name="endTime" class="input px-1">
            <option value="">--:--</option>
            <option th:each="t : ${timeOptions}" th:value="${t}" th:text="${t}"
                    th:selected="${t == view.endTime}"></option>
          </select>
        </label>
      </div>
      <label class="block">
        <span class="text-sm">備考</span>
        <input name="note" th:value="${view.note}" maxlength="200" class="input">
      </label>
      <p class="text-sm text-stone-600">締切後でも保存できます。「この期間は出勤できない」にしている期間に保存すると、チェックは外れます。</p>
      <button class="btn-primary w-full">保存する</button>
    </form>
    <form th:if="${view.exists}" th:action="@{/admin/requests/delete}" method="post" class="text-right">
      <input type="hidden" name="userId" th:value="${view.userId}">
      <input type="hidden" name="date" th:value="${view.date}">
      <button class="btn-danger">この日の申請を削除する</button>
    </form>
  </div>
  <a th:href="@{/admin/requests(date=${view.date})}" class="mt-4 inline-block text-sm underline">申請一覧へ戻る</a>
</main>
</body>
</html>
```

`src/main/resources/templates/admin/requests/table.html` の `<td th:each="cell : ${row.cells}" ...>` から `</td>` までを次の内容に置き換える：

```html
        <td th:each="cell : ${row.cells}" class="whitespace-nowrap p-0 text-center">
          <a th:href="@{/admin/requests/edit(userId=${row.userId},date=${cell.date})}"
             class="block min-h-10 min-w-14 px-2 py-1 hover:bg-amber-50">
            <th:block th:if="${cell.startTime != null}">
              <span th:text="${cell.startTime}">09:00</span><br>
              <span th:text="${cell.endTime}">17:00</span>
            </th:block>
            <span th:if="${cell.note != null}" th:title="${cell.note}" class="text-amber-700">※</span>
          </a>
        </td>
```

同じファイルの末尾の説明文を次の内容に置き換える：

```html
  <p class="mt-2 text-xs text-stone-500">マスをクリックすると代理で編集できます（締切後も可）。※ にカーソルを合わせると備考を表示します。</p>
```

- [ ] **Step 7: テストが通ることを確認する**

Run: `./mvnw test -Dtest=AdminRequestEditTest,AdminRequestTest`
Expected: PASS

- [ ] **Step 8: 全テストを実行してコミットする**

Run: `./mvnw test`
Expected: PASS

```bash
git add -A
git commit -m "feat: 管理者による申請の代理編集

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 9: この計画ファイルのTask 6のチェックボックスをすべて `[x]` にしてコミットし、停止してユーザーに報告する**

```bash
git add docs/superpowers/plans/2026-09-26-plan2-requests.md
git commit -m "docs: Plan 2 Task 6 完了

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## 後続Plan（Plan 2完了後に、実装済みコードを前提に詳細化する）

| Plan | 内容 |
|---|---|
| Plan 3 転記・公開 | 1日単位の転記画面（ポジション別、名前候補の並び、6行の空欄、「+ 追加する」、申請IN/OUT表示と差分警告、IN順の並べ替え）、二重登録防止、「この日を公開」「○日〜○日まで公開」（初期値は `Cycle.of(表示中の日)`）、公開済みの日の編集で `shift_changes` を記録 |
| Plan 4 閲覧 | スタッフのトップ画面（変更ありと確認済みボタン、次回出勤、直近の出勤、締切案内（提出済み判定は Plan 2 と同じ規則）、今月・来月の予定時間）、日別シフト一覧（ポジション別・IN順、過去1週間まで） |
