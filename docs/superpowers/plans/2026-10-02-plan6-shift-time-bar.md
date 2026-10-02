# Plan 6 シフトの時間バー表示 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **セッション運用：** 1セッション1 Task。Taskの最後のステップ（コミットとチェックボックス更新）が終わったら停止してユーザーに報告する。次のTaskは `/clear` 後の新しいセッションで行う。新しいセッションではこの冒頭（File Structureまで）と、未完了の最初のTaskの範囲だけを読む（`CLAUDE.md` 参照）。

**Goal:** ポジションに色を持たせ、日別シフト一覧をガントチャートに、転記画面の各行にミニバーを付けて、誰が何時から何時まで入っているかを色とバーで一目で分かるようにする。

**Architecture:** 色の対応表と「管理者なら社員の緑、そうでなければポジションの色」の判定は `util/BarColor`（enum）の1か所に置く。バーの位置（%）と時刻ラベルの位置は `util/TimeBar`（record）で計算する。Serviceがこの2つを使ってDTOにクラス名とstyle文字列を詰め、テンプレートは `th:classappend`・`th:style` で描くだけにする。転記画面の描き直しは `shifts.js` が同じ式で行い、色のクラス名はdata属性で受け取る。

**Tech Stack:** Java 17、Spring Boot 4.1.1、MyBatis、Flyway、Thymeleaf 3.1、Tailwind CSS 4、JUnit 5 + MockMvc + Testcontainers 2

**Spec:** `docs/superpowers/specs/2026-10-02-shift-time-bar-design.md`

## Global Constraints

- 画面の文言・コードのコメントはすべて日本語
- パッケージは層ごと。controllerはserviceのみ、serviceはrepositoryのみを呼ぶ（`util` の状態を持たない部品はどの層から使ってもよい）
- 時間軸は 8:00〜23:00（900分）を100%とする。左端％＝（IN−8:00）分÷900×100、幅％＝（OUT−IN）分÷900×100。style文字列は小数4桁（例：`left:6.6667%;width:53.3333%`）
- ポジションの色は `sky` `pink` `red` `orange` `yellow` `purple` `blue` `gray` の8つ。初期値・未知のキーは `gray`。緑は社員（管理者）専用で選択肢に入れない
- 色のクラスは設計書の表のとおり（例：`sky` → `bg-sky-300 text-sky-950`、社員 → `bg-green-400 text-green-950`、`gray` → `bg-stone-300 text-stone-950`）
- 時刻ラベル：勤務が5時間以上ならバーの中、5時間未満ならバーの外。外に出すときはOUTが19:00以前なら右、19:00より後なら左
- Tailwindのクラス名は文字列連結で組み立てない（省略せずに書く）。`BarColor.java` は `@source` でTailwindのスキャン対象に入れる
- Thymeleaf 3.1ではテンプレートから `T()` で静的メンバーを参照できないため、テンプレートで使う値はすべてDTO・モデル経由で渡す
- テンプレートから参照するDTOは既存と同じくLombokの `@Data` クラスにする（recordにしない）
- コミットメッセージの末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` を付ける
- テスト実行には Docker Desktop の起動が必要（Testcontainers）

## Review Focus

1. DBに未知の色のキー（手で書き換えた、将来消した色など）が入っている：画面が500にならず、グレーで表示される → Task 1でテスト（`BarColorTest`）
2. 色の選択欄に社員の緑（`green`）や空文字が送られる：入力エラー「色を選択してください」になり、保存されない → Task 1でテスト
3. 8:00〜23:00の勤務（端から端まで）：左端0%・幅100%で、時刻はバーの中 → Task 2でテスト（`TimeBarTest`）
4. 管理者がキッチン枠に入っている日：日別一覧・転記画面ともにキッチンの色ではなく社員の緑になり、凡例に「社員」が出る。管理者のいない日は凡例に「社員」が出ない → Task 2・Task 3でテスト
5. 転記の登録でエラーになり入力を表示し直す（IN ≥ OUT、時刻が不正、存在しないスタッフID）：画面が500にならず、その行にはバーを出さない（存在しないスタッフIDでも時刻が正しければポジションの色でバーを出す） → Task 3でテスト（`ShiftSaveTest`）

## File Structure

```
src/main/java/jp/bk/shiftmanager/
  util/        BarColor（新規：色のキー・表示名・クラス、社員の色、色の判定）
               TimeBar（新規：バーのstyle、時刻ラベルの位置とstyle）
  entity/      Position（color を追加）
  mapper/      PositionMapper（color の登録・更新）
  form/        PositionForm（color を追加）
  service/     PositionService（color の検証と保存、色の選択肢）
               DailyShiftService（バー・色・凡例を詰める）
               ShiftService（候補の管理者フラグ、グループの色、行のバーを詰める）
  controller/  PositionAdminController（色の選択肢をモデルに渡す）
  dto/         LegendItem（新規：凡例1つ分）
               DailyShiftRow・DailyShiftView（バー・凡例）
               ShiftCandidate・ShiftGroupView・ShiftRowView・ShiftDayView（転記画面のバー）
src/main/resources/
  db/migration/V3__positions_color.sql   （新規）
  templates/admin/positions.html         色の選択欄と色見本
  templates/shifts/day.html              ガントチャートに書き換え
  templates/admin/shifts/day.html・row.html  ミニバーの列とdata属性
  static/js/shifts.js                    行のバーの描き直し
src/main/frontend/app.css                BarColor.java を @source に追加、時間軸の目盛線 .time-grid
Dockerfile                               CSSのビルドに BarColor.java をコピー
documents/2026-09-25-shift-manager-v2-spec.md・2026-09-25-db-design.md  追記
src/test/java/jp/bk/shiftmanager/
  util/        BarColorTest・TimeBarTest（新規）
  SchemaTest、controller/PositionAdminTest・DailyShiftTest・ShiftDayTest・ShiftSaveTest（変更）
```

---

### Task 1: ポジションの色

**Files:**
- Create: `src/main/java/jp/bk/shiftmanager/util/BarColor.java`
- Create: `src/main/resources/db/migration/V3__positions_color.sql`
- Create: `src/test/java/jp/bk/shiftmanager/util/BarColorTest.java`
- Modify: `src/main/java/jp/bk/shiftmanager/entity/Position.java`
- Modify: `src/main/java/jp/bk/shiftmanager/mapper/PositionMapper.java`（`insert`・`update`）
- Modify: `src/main/java/jp/bk/shiftmanager/form/PositionForm.java`
- Modify: `src/main/java/jp/bk/shiftmanager/service/PositionService.java`
- Modify: `src/main/java/jp/bk/shiftmanager/controller/PositionAdminController.java`（`list`）
- Modify: `src/main/resources/templates/admin/positions.html`
- Modify: `src/main/frontend/app.css`
- Modify: `Dockerfile`
- Modify: `src/test/java/jp/bk/shiftmanager/SchemaTest.java`
- Modify: `src/test/java/jp/bk/shiftmanager/controller/PositionAdminTest.java`
- Modify: `documents/2026-09-25-shift-manager-v2-spec.md`（「ポジション管理（管理者）」）
- Modify: `documents/2026-09-25-db-design.md`（`positions`）

**Interfaces:**
- Consumes: `PositionRepository.insert(Position)` / `update(Position)` / `findAll()`、`BusinessException(String)`
- Produces:
  - `enum BarColor`（`SKY` `PINK` `RED` `ORANGE` `YELLOW` `PURPLE` `BLUE` `GRAY`）。`String getKey()`、`String getLabel()`、`String getBarClass()`
  - `BarColor.DEFAULT_KEY`（`"gray"`）、`BarColor.EMPLOYEE_LABEL`（`"社員"`）、`BarColor.EMPLOYEE_CLASS`（`"bg-green-400 text-green-950"`）
  - `static Optional<BarColor> BarColor.fromKey(String key)`：選択肢にないキー・nullなら空
  - `static BarColor BarColor.ofKey(String key)`：選択肢にないキーは `GRAY`
  - `static String BarColor.barClass(String positionColor, boolean admin)`：管理者なら `EMPLOYEE_CLASS`、そうでなければ `ofKey(positionColor).getBarClass()`
  - `Position.getColor()` / `setColor(String)`（初期値 `"gray"`）
  - `PositionService.colors()`：`List<BarColor>`（画面の選択肢。enumの定義順）
  - モデル属性 `colors`（`/admin/positions`）

- [x] **Step 1: BarColorの失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/util/BarColorTest.java`

```java
package jp.bk.shiftmanager.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BarColorTest {

    @Test
    void キーから色を引ける() {
        assertThat(BarColor.fromKey("sky")).contains(BarColor.SKY);
        assertThat(BarColor.SKY.getLabel()).isEqualTo("水色");
        assertThat(BarColor.SKY.getBarClass()).isEqualTo("bg-sky-300 text-sky-950");
        assertThat(BarColor.GRAY.getBarClass()).isEqualTo("bg-stone-300 text-stone-950");
    }

    @Test
    void 選択肢にないキーは空_社員の緑も選択肢にない() {
        assertThat(BarColor.fromKey("green")).isEmpty();
        assertThat(BarColor.fromKey("")).isEmpty();
        assertThat(BarColor.fromKey(null)).isEmpty();
        assertThat(BarColor.fromKey("SKY")).isEmpty();
    }

    @Test
    void 未知のキーはグレーとして扱う() {
        assertThat(BarColor.ofKey("green")).isEqualTo(BarColor.GRAY);
        assertThat(BarColor.ofKey(null)).isEqualTo(BarColor.GRAY);
    }

    @Test
    void 管理者なら社員の緑_そうでなければポジションの色() {
        assertThat(BarColor.barClass("pink", true)).isEqualTo("bg-green-400 text-green-950");
        assertThat(BarColor.barClass("pink", false)).isEqualTo("bg-pink-300 text-pink-950");
        assertThat(BarColor.barClass("unknown", false)).isEqualTo("bg-stone-300 text-stone-950");
    }
}
```

- [x] **Step 2: テストが失敗することを確認する**

Run: `./mvnw -q test -Dtest=BarColorTest`
Expected: コンパイルエラー（`BarColor` がない）

- [x] **Step 3: BarColorを実装する**

`src/main/java/jp/bk/shiftmanager/util/BarColor.java`

```java
package jp.bk.shiftmanager.util;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * シフトのバーの色。ポジションに選べる色と、社員（管理者）の色を持つ。
 * Tailwindはクラス名を文字列連結で組み立てるとCSSに含めないため、クラス名は省略せずに書く
 * （このファイルは app.css の @source でスキャン対象にしている）
 */
@Getter
@RequiredArgsConstructor
public enum BarColor {
    SKY("sky", "水色", "bg-sky-300 text-sky-950"),
    PINK("pink", "ピンク", "bg-pink-300 text-pink-950"),
    RED("red", "赤", "bg-red-300 text-red-950"),
    ORANGE("orange", "オレンジ", "bg-orange-300 text-orange-950"),
    YELLOW("yellow", "黄", "bg-yellow-300 text-yellow-950"),
    PURPLE("purple", "紫", "bg-purple-300 text-purple-950"),
    BLUE("blue", "青", "bg-blue-300 text-blue-950"),
    GRAY("gray", "グレー", "bg-stone-300 text-stone-950");

    /** ポジションの色の初期値 */
    public static final String DEFAULT_KEY = "gray";
    /** 社員（管理者）の凡例の名前 */
    public static final String EMPLOYEE_LABEL = "社員";
    /** 社員（管理者）のバーの色。ポジションの選択肢には入れない */
    public static final String EMPLOYEE_CLASS = "bg-green-400 text-green-950";

    /** DBに保存する値 */
    private final String key;
    /** 画面の表示名 */
    private final String label;
    /** バーに付けるTailwindのクラス */
    private final String barClass;

    /** 選択肢にないキー・nullなら空 */
    public static Optional<BarColor> fromKey(String key) {
        return Arrays.stream(values()).filter(color -> color.key.equals(key)).findFirst();
    }

    /** 表示用。選択肢にないキーはグレーとして扱う */
    public static BarColor ofKey(String key) {
        return fromKey(key).orElse(GRAY);
    }

    /** バーの色：管理者なら社員の緑、そうでなければポジションの色 */
    public static String barClass(String positionColor, boolean admin) {
        return admin ? EMPLOYEE_CLASS : ofKey(positionColor).getBarClass();
    }
}
```

- [x] **Step 4: テストが通ることを確認する**

Run: `./mvnw -q test -Dtest=BarColorTest`
Expected: PASS

- [x] **Step 5: DBとポジション管理の失敗するテストを書く**

`SchemaTest` に追加：

```java
    @Test
    void ポジションの色の初期値はgray() {
        jdbc.update("INSERT INTO positions (name, display_order) VALUES ('色テスト', 1)");
        String color = jdbc.queryForObject("SELECT color FROM positions WHERE name = '色テスト'", String.class);
        jdbc.update("DELETE FROM positions WHERE name = '色テスト'");
        assertThat(color).isEqualTo("gray");
    }
```

`PositionAdminTest` に追加：

```java
    @Test
    void 色を選んで追加_変更できる_省略時はグレー() throws Exception {
        mvc.perform(post("/admin/positions").with(user(admin)).with(csrf())
                        .param("name", "キッチン").param("displayOrder", "1").param("color", "sky"))
                .andExpect(redirectedUrl("/admin/positions"));
        mvc.perform(post("/admin/positions").with(user(admin)).with(csrf())
                        .param("name", "ホール").param("displayOrder", "2"))
                .andExpect(redirectedUrl("/admin/positions"));

        List<Position> all = positionMapper.findAll();
        assertThat(all).extracting(Position::getName, Position::getColor)
                .containsExactly(tuple("キッチン", "sky"), tuple("ホール", "gray"));

        mvc.perform(post("/admin/positions/{id}", all.get(1).getId()).with(user(admin)).with(csrf())
                        .param("name", "ホール").param("displayOrder", "2").param("color", "pink"))
                .andExpect(redirectedUrl("/admin/positions"));
        assertThat(positionMapper.findById(all.get(1).getId()).getColor()).isEqualTo("pink");
    }

    @Test
    void 選択肢にない色は入力エラーで保存されない() throws Exception {
        Position kitchen = data.position("キッチン", 1);

        // 社員の緑は選択肢にない
        for (String color : new String[] {"green", ""}) {
            mvc.perform(post("/admin/positions").with(user(admin)).with(csrf())
                            .param("name", "ホール").param("displayOrder", "2").param("color", color))
                    .andExpect(redirectedUrl("/admin/positions"))
                    .andExpect(flash().attribute("error", "色を選択してください"));
            mvc.perform(post("/admin/positions/{id}", kitchen.getId()).with(user(admin)).with(csrf())
                            .param("name", "キッチン").param("displayOrder", "1").param("color", color))
                    .andExpect(redirectedUrl("/admin/positions"))
                    .andExpect(flash().attribute("error", "色を選択してください"));
        }

        assertThat(positionMapper.findAll()).extracting(Position::getName, Position::getColor)
                .containsExactly(tuple("キッチン", "gray"));
    }

    @Test
    void 一覧画面に色の選択肢と色見本を表示する() throws Exception {
        Position kitchen = data.position("キッチン", 1);
        kitchen.setColor("sky");
        positionMapper.update(kitchen);

        mvc.perform(get("/admin/positions").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("水色")))
                .andExpect(content().string(Matchers.containsString("value=\"sky\" selected=\"selected\"")))
                .andExpect(content().string(Matchers.containsString("bg-sky-300 text-sky-950")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("value=\"green\""))));
    }
```

`PositionAdminTest` の import に `import static org.assertj.core.api.Assertions.tuple;` を追加する。

- [x] **Step 6: テストが失敗することを確認する**

Run: `./mvnw -q test -Dtest=SchemaTest,PositionAdminTest`
Expected: コンパイルエラー（`Position.getColor` がない）

- [x] **Step 7: マイグレーション・エンティティ・Mapperを変更する**

`src/main/resources/db/migration/V3__positions_color.sql`

```sql
-- 転記画面・日別一覧のバーの色（util/BarColor のキー）。既存のポジションはグレー
ALTER TABLE positions ADD COLUMN color VARCHAR(20) NOT NULL DEFAULT 'gray';
```

`Position.java` に追加（`hidden` の下）：

```java
    /** バーの色（BarColor のキー） */
    private String color = BarColor.DEFAULT_KEY;
```

import に `import jp.bk.shiftmanager.util.BarColor;` を追加する。

`PositionMapper.java` の `insert`・`update` を変更：

```java
    @Insert("INSERT INTO positions (name, display_order, hidden, color) "
            + "VALUES (#{name}, #{displayOrder}, #{hidden}, #{color})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(Position position);

    @Update("UPDATE positions SET name = #{name}, display_order = #{displayOrder}, hidden = #{hidden}, "
            + "color = #{color} WHERE id = #{id}")
    int update(Position position);
```

- [x] **Step 8: フォーム・Service・Controllerを変更する**

`PositionForm.java` に追加（`hidden` の下）。送られなかった場合（古い画面から送信した場合）はグレーにする：

```java
    /** BarColor のキー。選択肢にない値は PositionService で入力エラーにする */
    private String color = BarColor.DEFAULT_KEY;
```

import に `import jp.bk.shiftmanager.util.BarColor;` を追加する。

`PositionService.java`：

```java
    /** 色の選択肢（定義順） */
    public List<BarColor> colors() {
        return List.of(BarColor.values());
    }

    @Transactional
    public void create(PositionForm form) {
        checkNameUnique(form.getName(), 0);
        Position position = new Position();
        position.setName(form.getName());
        position.setDisplayOrder(form.getDisplayOrder());
        position.setHidden(form.isHidden());
        position.setColor(validColor(form.getColor()));
        positionRepository.insert(position);
    }

    @Transactional
    public void update(long id, PositionForm form) {
        Position position = find(id);
        checkNameUnique(form.getName(), id);
        position.setName(form.getName());
        position.setDisplayOrder(form.getDisplayOrder());
        position.setHidden(form.isHidden());
        position.setColor(validColor(form.getColor()));
        positionRepository.update(position);
    }

    /** 選択肢にない色（社員の緑を含む）は入力エラー */
    private String validColor(String key) {
        return BarColor.fromKey(key).map(BarColor::getKey)
                .orElseThrow(() -> new BusinessException("色を選択してください"));
    }
```

import に `import jp.bk.shiftmanager.util.BarColor;` を追加する。

`PositionAdminController.list`：

```java
    @GetMapping
    public String list(Model model) {
        model.addAttribute("positions", positionService.findAll());
        model.addAttribute("colors", positionService.colors());
        return "admin/positions";
    }
```

- [x] **Step 9: 画面を変更する**

`templates/admin/positions.html` の一覧の行（各ポジションの更新フォーム）で、「表示順」のlabelの後に追加：

```html
        <label class="block">
          <span class="text-xs">色</span>
          <span class="flex items-center gap-1">
            <!-- 色見本（選択肢にない色はグレーとして扱うため何も出さない） -->
            <span th:each="c : ${colors}" th:if="${c.key == p.color}"
                  class="inline-block h-5 w-5 rounded" th:classappend="${c.barClass}"></span>
            <select name="color" class="input mt-0 w-auto px-1" aria-label="色">
              <option th:each="c : ${colors}" th:value="${c.key}" th:text="${c.label}"
                      th:selected="${c.key == p.color}">水色</option>
            </select>
          </span>
        </label>
```

追加フォームの「表示順」のlabelの後に追加（初期値はグレー）：

```html
    <label class="block">
      <span class="text-xs">色</span>
      <select name="color" class="input w-auto px-1">
        <option th:each="c : ${colors}" th:value="${c.key}" th:text="${c.label}"
                th:selected="${c.key == 'gray'}">グレー</option>
      </select>
    </label>
```

説明文（`<p class="mb-4 text-sm text-stone-600">`）の末尾に「色は転記画面・日別一覧のバーに使います（管理者は色に関係なく緑で表示します）。」を追加する。

- [x] **Step 10: TailwindのスキャンとDockerfileを変更する**

`src/main/frontend/app.css` の `@source` の下に追加：

```css
/* バーの色のクラスはJavaのenumに書いているため、スキャン対象に入れる */
@source "../java/jp/bk/shiftmanager/util/BarColor.java";
```

`Dockerfile` のCSSビルドの `COPY src/main/resources/templates ...` の下に追加：

```dockerfile
COPY src/main/java/jp/bk/shiftmanager/util/BarColor.java src/main/java/jp/bk/shiftmanager/util/BarColor.java
```

Run: `npm run build` の後、`grep -c "bg-sky-300" src/main/resources/static/css/app.css`
Expected: 1以上（`app.css` はgit管理外のためコミットしない）

- [x] **Step 11: テストが通ることを確認する**

Run: `./mvnw -q test -Dtest=BarColorTest,SchemaTest,PositionAdminTest`
Expected: PASS

- [x] **Step 12: ドキュメントを更新する**

`documents/2026-09-25-shift-manager-v2-spec.md` の「ポジション管理（管理者）」に追記：

```markdown
- 色を持つ。水色・ピンク・赤・オレンジ・黄・紫・青・グレーから選ぶ（初期値グレー）。転記画面・日別一覧のバーの色に使う。管理者は色に関係なく緑（社員）で表示するため、緑は選択肢に入れない（2026-10-02 追加）
```

`documents/2026-09-25-db-design.md` の `positions` の表の `hidden` の行の下に追加：

```markdown
| color | VARCHAR(20) | NOT NULL DEFAULT 'gray' | バーの色（`util/BarColor` のキー：sky・pink・red・orange・yellow・purple・blue・gray） |
```

- [x] **Step 13: 全テストを実行する**

Run: `./mvnw -q test`
Expected: PASS（既存のテストは `color` を送らないため、グレーとして保存される）

- [x] **Step 14: コミットし、この計画のTask 1のチェックボックスを更新する**

```bash
git add -A src/main/java src/main/resources/db src/main/resources/templates src/main/frontend Dockerfile src/test documents docs/superpowers/plans/2026-10-02-plan6-shift-time-bar.md
git commit -m "feat: ポジションにバーの色を持たせる

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: 日別シフト一覧のガントチャート

**Files:**
- Create: `src/main/java/jp/bk/shiftmanager/util/TimeBar.java`
- Create: `src/main/java/jp/bk/shiftmanager/dto/LegendItem.java`
- Create: `src/test/java/jp/bk/shiftmanager/util/TimeBarTest.java`
- Modify: `src/main/java/jp/bk/shiftmanager/dto/DailyShiftRow.java`
- Modify: `src/main/java/jp/bk/shiftmanager/dto/DailyShiftView.java`
- Modify: `src/main/java/jp/bk/shiftmanager/service/DailyShiftService.java`（`getDay`・`toRow`）
- Modify: `src/main/resources/templates/shifts/day.html`
- Modify: `src/main/frontend/app.css`
- Modify: `src/test/java/jp/bk/shiftmanager/controller/DailyShiftTest.java`
- Modify: `documents/2026-09-25-shift-manager-v2-spec.md`（「日別シフト一覧」）

**Interfaces:**
- Consumes: Task 1 の `BarColor.barClass(String, boolean)`・`BarColor.ofKey(String)`・`BarColor.EMPLOYEE_LABEL`・`BarColor.EMPLOYEE_CLASS`、`Position.getColor()`、`TimeSlots.FIRST`・`TimeSlots.LAST`・`TimeSlots.formatRange`
- Produces:
  - `record TimeBar(String style, TimeBar.Label label, String labelStyle)`、`enum TimeBar.Label { INSIDE, RIGHT, LEFT }`
  - `static TimeBar TimeBar.of(LocalTime start, LocalTime end)`：どちらかがnull、または `start >= end` ならnull
  - `boolean TimeBar.labelInside()`
  - `LegendItem(String label, String barClass)`（`@Data @AllArgsConstructor`）
  - `DailyShiftRow` の `barClass`・`barStyle`・`labelInside`・`labelStyle`
  - `DailyShiftView.getLegend()`：`List<LegendItem>`
  - CSSクラス `.time-grid`（1時間おきの薄い縦線。Task 3でも使う）

- [ ] **Step 1: TimeBarの失敗するテストを書く**

`src/test/java/jp/bk/shiftmanager/util/TimeBarTest.java`

```java
package jp.bk.shiftmanager.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class TimeBarTest {

    private static TimeBar bar(String start, String end) {
        return TimeBar.of(LocalTime.parse(start), LocalTime.parse(end));
    }

    @Test
    void 左端と幅を8時から23時を100パーセントとして計算する() {
        assertThat(bar("09:00", "17:00").style()).isEqualTo("left:6.6667%;width:53.3333%");
    }

    @Test
    void 端から端までの勤務は左端0_幅100で時刻はバーの中() {
        TimeBar bar = bar("08:00", "23:00");
        assertThat(bar.style()).isEqualTo("left:0.0000%;width:100.0000%");
        assertThat(bar.labelInside()).isTrue();
        assertThat(bar.labelStyle()).isNull();
    }

    @Test
    void 勤務が5時間ちょうどならバーの中_5時間未満ならバーの外() {
        assertThat(bar("10:00", "15:00").label()).isEqualTo(TimeBar.Label.INSIDE);
        assertThat(bar("10:00", "14:30").label()).isEqualTo(TimeBar.Label.RIGHT);
    }

    @Test
    void 外に出すときはOUTが19時以前なら右_19時より後なら左() {
        TimeBar right = bar("17:00", "19:00");
        assertThat(right.label()).isEqualTo(TimeBar.Label.RIGHT);
        // バーの右端の位置から書き始める
        assertThat(right.labelStyle()).isEqualTo("left:73.3333%");

        TimeBar left = bar("17:30", "19:30");
        assertThat(left.label()).isEqualTo(TimeBar.Label.LEFT);
        // バーの左端の位置で書き終える（右からの距離で指定する）
        assertThat(left.labelStyle()).isEqualTo("right:63.3333%");
    }

    @Test
    void 時刻がないかINがOUT以降ならバーを作らない() {
        assertThat(TimeBar.of(null, LocalTime.of(17, 0))).isNull();
        assertThat(TimeBar.of(LocalTime.of(9, 0), null)).isNull();
        assertThat(bar("12:00", "12:00")).isNull();
        assertThat(bar("13:00", "12:00")).isNull();
    }
}
```

- [ ] **Step 2: テストが失敗することを確認する**

Run: `./mvnw -q test -Dtest=TimeBarTest`
Expected: コンパイルエラー（`TimeBar` がない）

- [ ] **Step 3: TimeBarを実装する**

`src/main/java/jp/bk/shiftmanager/util/TimeBar.java`

```java
package jp.bk.shiftmanager.util;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Locale;

/**
 * シフトのバーの位置。8:00〜23:00を100%とする（転記画面の shifts.js も同じ式で計算する）。
 * style はバーの左端と幅、labelStyle は時刻をバーの外に出すときの位置（中に書くときはnull）
 */
public record TimeBar(String style, Label label, String labelStyle) {

    /** 時刻を書く位置 */
    public enum Label { INSIDE, RIGHT, LEFT }

    private static final double SPAN_MINUTES = Duration.between(TimeSlots.FIRST, TimeSlots.LAST).toMinutes();
    /** これより短い勤務は、スマホでは時刻がバーに収まらないため外に出す */
    private static final long INSIDE_MIN_MINUTES = 5 * 60;
    /** OUTがこれより後なら、右側に時刻を書く余白がないため左に出す */
    private static final LocalTime RIGHT_LABEL_LAST = LocalTime.of(19, 0);

    /** どちらかがnull、またはINがOUT以降ならnull */
    public static TimeBar of(LocalTime start, LocalTime end) {
        if (start == null || end == null || !start.isBefore(end)) {
            return null;
        }
        double left = percent(Duration.between(TimeSlots.FIRST, start).toMinutes());
        double width = percent(Duration.between(start, end).toMinutes());
        String style = "left:" + format(left) + "%;width:" + format(width) + "%";
        if (Duration.between(start, end).toMinutes() >= INSIDE_MIN_MINUTES) {
            return new TimeBar(style, Label.INSIDE, null);
        }
        if (!end.isAfter(RIGHT_LABEL_LAST)) {
            return new TimeBar(style, Label.RIGHT, "left:" + format(left + width) + "%");
        }
        return new TimeBar(style, Label.LEFT, "right:" + format(100 - left) + "%");
    }

    public boolean labelInside() {
        return label == Label.INSIDE;
    }

    private static double percent(long minutes) {
        return minutes * 100 / SPAN_MINUTES;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
```

- [ ] **Step 4: テストが通ることを確認する**

Run: `./mvnw -q test -Dtest=TimeBarTest`
Expected: PASS

- [ ] **Step 5: 日別一覧の失敗するテストを書く**

`DailyShiftTest` に追加：

```java
    @Test
    void バーの位置と色を持ち_管理者は社員の緑になる() throws Exception {
        kitchen.setColor("sky");
        positionMapper.update(kitchen);
        User boss = data.user("boss2", "副店長", true);
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.shift(boss, kitchen, OCT2, "17:00", "19:00");
        data.publish(OCT2);

        DailyShiftView view = view(data.login(taro), "2026-10-02");

        assertThat(view.getGroups().get(0).getRows())
                .extracting(DailyShiftRow::getName, DailyShiftRow::getBarClass, DailyShiftRow::getBarStyle,
                        DailyShiftRow::isLabelInside, DailyShiftRow::getLabelStyle)
                .containsExactly(
                        tuple("山田太郎", "bg-sky-300 text-sky-950", "left:6.6667%;width:53.3333%", true, null),
                        tuple("副店長", "bg-green-400 text-green-950", "left:60.0000%;width:13.3333%", false,
                                "left:73.3333%"));
        // 凡例はその日に出てくるポジション（表示順）と、管理者がいれば社員
        assertThat(view.getLegend()).extracting(LegendItem::getLabel, LegendItem::getBarClass)
                .containsExactly(tuple("キッチン", "bg-sky-300 text-sky-950"),
                        tuple("社員", "bg-green-400 text-green-950"));
    }

    @Test
    void 管理者のいない日は凡例に社員を出さない() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.shift(hanako, counter, OCT2, "12:00", "18:00");
        data.publish(OCT2);

        DailyShiftView view = view(data.login(taro), "2026-10-02");

        assertThat(view.getLegend()).extracting(LegendItem::getLabel).containsExactly("キッチン", "カウンター");
    }

    @Test
    void ガントチャートとして描画する() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.publish(OCT2);

        mvc.perform(get("/shifts").param("date", "2026-10-02").with(user(data.login(taro))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("style=\"left:6.6667%;width:53.3333%\"")))
                .andExpect(content().string(Matchers.containsString("bg-stone-300 text-stone-950")))
                .andExpect(content().string(Matchers.containsString("09:00〜17:00")));
    }
```

`DailyShiftTest` に `@Autowired PositionMapper positionMapper;` のフィールドと、import（`jp.bk.shiftmanager.dto.LegendItem`、`jp.bk.shiftmanager.mapper.PositionMapper`、`org.springframework.beans.factory.annotation.Autowired`）を追加する。

- [ ] **Step 6: テストが失敗することを確認する**

Run: `./mvnw -q test -Dtest=DailyShiftTest`
Expected: コンパイルエラー（`LegendItem`・`getBarClass` がない）

- [ ] **Step 7: DTOを変更する**

`src/main/java/jp/bk/shiftmanager/dto/LegendItem.java`

```java
package jp.bk.shiftmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** バーの色の凡例1つ分 */
@Data
@AllArgsConstructor
public class LegendItem {
    /** ポジション名、または「社員」 */
    private String label;
    private String barClass;
}
```

`DailyShiftRow.java` に追加（`mine` の下）：

```java
    /** バーの色のクラス */
    private String barClass;
    /** バーの左端と幅（例：left:6.6667%;width:53.3333%） */
    private String barStyle;
    /** 時刻をバーの中に書くか（短い勤務はバーの外に書く） */
    private boolean labelInside;
    /** 時刻をバーの外に書くときの位置（中に書くときはnull） */
    private String labelStyle;
```

`DailyShiftView.java` に追加（`groups` の下）：

```java
    /** 凡例。その日に出てくるポジション（表示順）と、管理者がいれば社員 */
    private List<LegendItem> legend = new ArrayList<>();
```

- [ ] **Step 8: DailyShiftServiceを変更する**

`getDay` の「公開後に無効化されたスタッフ…」以降を次に置き換える：

```java
        // 公開後に無効化されたスタッフのシフトも表示するため、無効を含む全員から引く
        Map<Long, User> users = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        // INの早い順
        List<Shift> shifts = shiftRepository.findByDate(date);
        boolean employeeShown = false;
        // 非表示にしたポジションのシフトも表示するため、非表示を含む全ポジションで分ける
        for (Position position : positionRepository.findAll()) {
            List<DailyShiftRow> rows = shifts.stream()
                    .filter(shift -> shift.getPositionId().equals(position.getId()))
                    .map(shift -> toRow(shift, position, users.get(shift.getUserId()), viewerId))
                    .toList();
            if (!rows.isEmpty()) {
                DailyShiftGroup group = new DailyShiftGroup();
                group.setPositionName(position.getName());
                group.setRows(rows);
                view.getGroups().add(group);
                view.getLegend().add(new LegendItem(position.getName(),
                        BarColor.ofKey(position.getColor()).getBarClass()));
                employeeShown |= rows.stream().anyMatch(row -> BarColor.EMPLOYEE_CLASS.equals(row.getBarClass()));
            }
        }
        if (employeeShown) {
            view.getLegend().add(new LegendItem(BarColor.EMPLOYEE_LABEL, BarColor.EMPLOYEE_CLASS));
        }
        if (view.getGroups().isEmpty()) {
            view.setMessage("この日の出勤者はいません");
        }
        return view;
    }

    private DailyShiftRow toRow(Shift shift, Position position, User user, long viewerId) {
        DailyShiftRow row = new DailyShiftRow();
        row.setName(user.getName());
        row.setTimeLabel(TimeSlots.formatRange(shift.getStartTime(), shift.getEndTime()));
        row.setMine(shift.getUserId() == viewerId);
        row.setBarClass(BarColor.barClass(position.getColor(), user.isAdmin()));
        // DBの制約で IN < OUT のため、バーは必ず作られる
        TimeBar bar = TimeBar.of(shift.getStartTime(), shift.getEndTime());
        row.setBarStyle(bar.style());
        row.setLabelInside(bar.labelInside());
        row.setLabelStyle(bar.labelStyle());
        return row;
    }
```

import に `java.util.function.Function`、`jp.bk.shiftmanager.dto.LegendItem`、`jp.bk.shiftmanager.util.BarColor`、`jp.bk.shiftmanager.util.TimeBar` を追加する。

- [ ] **Step 9: 時間軸の目盛線のCSSを追加する**

`src/main/frontend/app.css` の `@layer components` 内に追加：

```css
  /* 8:00〜23:00の時間軸に1時間おきの薄い縦線を引く（15等分） */
  .time-grid {
    background-image: repeating-linear-gradient(to right,
      transparent 0, transparent calc(100% / 15 - 1px),
      var(--color-stone-200) calc(100% / 15 - 1px), var(--color-stone-200) calc(100% / 15));
  }
```

- [ ] **Step 10: 日別一覧の画面をガントチャートに書き換える**

`templates/shifts/day.html` の `<section th:each="g : ${view.groups}" ...>…</section>` を次に置き換える：

```html
  <div th:if="${!#lists.isEmpty(view.groups)}" class="rounded-lg border border-stone-200 bg-white p-3">
    <!-- 凡例 -->
    <ul class="mb-3 flex flex-wrap gap-3 text-xs">
      <li th:each="item : ${view.legend}" class="flex items-center gap-1">
        <span class="inline-block h-3 w-3 rounded-sm" th:classappend="${item.barClass}"></span>
        <span th:text="${item.label}">キッチン</span>
      </li>
    </ul>
    <!-- 時間軸の見出し（2時間おき）。名前の列の幅（w-20）だけ右にずらす -->
    <div class="flex text-xs text-stone-500">
      <span class="w-20 shrink-0"></span>
      <div class="relative h-4 flex-1">
        <span th:each="h : ${#numbers.sequence(8, 22, 2)}" class="absolute -translate-x-1/2 tabular-nums"
              th:style="|left:${(h - 8) * 100 / 15.0}%|" th:text="${h}">8</span>
      </div>
    </div>
    <section th:each="g : ${view.groups}" class="mt-2">
      <h2 class="mb-1 text-sm font-bold" th:text="${g.positionName}">キッチン</h2>
      <div th:each="r : ${g.rows}" class="flex items-center"
           th:classappend="${r.mine} ? 'bg-amber-50 font-bold'">
        <span class="w-20 shrink-0 truncate pr-1 text-sm" th:text="${r.name}" th:title="${r.name}">山田太郎</span>
        <div class="time-grid relative h-8 flex-1">
          <div class="absolute inset-y-1 flex items-center justify-center overflow-hidden whitespace-nowrap rounded text-xs tabular-nums"
               th:classappend="${r.barClass}" th:style="${r.barStyle}">
            <span th:if="${r.labelInside}" th:text="${r.timeLabel}">09:00〜17:00</span>
          </div>
          <span th:unless="${r.labelInside}"
                class="absolute inset-y-0 flex items-center whitespace-nowrap px-1 text-xs tabular-nums"
                th:style="${r.labelStyle}" th:text="${r.timeLabel}">17:00〜19:00</span>
        </div>
      </div>
    </section>
  </div>
```

- [ ] **Step 11: テストが通ることを確認する**

Run: `./mvnw -q test -Dtest=TimeBarTest,DailyShiftTest`
Expected: PASS

- [ ] **Step 12: ブラウザで確認する**

`npm run build` の後にアプリを起動し、公開済みの日の `/shifts?date=...` を開く。確認すること：
- PC幅とスマホ幅（375px）で横スクロールが出ない
- 2時間おきの数字と1時間おきの縦線が揃っている
- 5時間以上はバーの中、5時間未満はバーの外（OUTが19:00以前は右、それより後は左）に時刻が出る
- 自分の行が薄い黄色で太字
- 管理者のバーが緑、凡例に「社員」が出る

確認後、起動したアプリを停止する。

- [ ] **Step 13: 仕様書を更新する**

`documents/2026-09-25-shift-manager-v2-spec.md` の「日別シフト一覧」に追記：

```markdown
- ガントチャートで表示する。左に名前、右に8:00〜23:00の時間軸（2時間おきに数字、1時間おきに縦線）を置き、IN〜OUTを横バーで表す。バーの色は、管理者なら緑（社員）、それ以外は入れたポジションの色。時刻はバーの中に書き、5時間未満の勤務はバーの外（OUTが19:00以前なら右、それより後なら左）に書く。上部に凡例（その日のポジションの色と、管理者がいれば社員）を置く（2026-10-02 追加）
```

- [ ] **Step 14: 全テストを実行する**

Run: `./mvnw -q test`
Expected: PASS

- [ ] **Step 15: コミットし、この計画のTask 2のチェックボックスを更新する**

```bash
git add -A src/main/java src/main/resources/templates src/main/frontend src/test documents docs/superpowers/plans/2026-10-02-plan6-shift-time-bar.md
git commit -m "feat: 日別シフト一覧をガントチャートにする

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: 転記画面のミニバー

**Files:**
- Modify: `src/main/java/jp/bk/shiftmanager/dto/ShiftCandidate.java`
- Modify: `src/main/java/jp/bk/shiftmanager/dto/ShiftGroupView.java`
- Modify: `src/main/java/jp/bk/shiftmanager/dto/ShiftRowView.java`
- Modify: `src/main/java/jp/bk/shiftmanager/dto/ShiftDayView.java`
- Modify: `src/main/java/jp/bk/shiftmanager/service/ShiftService.java`（`buildView`・`toRowView`・`toCandidate`）
- Modify: `src/main/resources/templates/admin/shifts/day.html`
- Modify: `src/main/resources/templates/admin/shifts/row.html`
- Modify: `src/main/resources/static/js/shifts.js`
- Modify: `src/test/java/jp/bk/shiftmanager/controller/ShiftDayTest.java`
- Modify: `src/test/java/jp/bk/shiftmanager/controller/ShiftSaveTest.java`
- Modify: `documents/2026-09-25-shift-manager-v2-spec.md`（「確定シフトの転記（管理者）」の「画面構成」）

**Interfaces:**
- Consumes: Task 1 の `BarColor.barClass(String, boolean)`・`BarColor.ofKey(String)`・`BarColor.EMPLOYEE_CLASS`、Task 2 の `TimeBar.of(LocalTime, LocalTime)`・`TimeBar.style()`・CSSクラス `.time-grid`、既存の `ShiftService.parseTimeOrNull(String)`・`parseId(String)`
- Produces:
  - `ShiftCandidate(String userId, String name, boolean admin)`
  - `ShiftGroupView.getBarClass()`：ポジションの色のクラス
  - `ShiftRowView.getBarClass()`・`getBarStyle()`：バーを出さない行はどちらもnull
  - `ShiftDayView.getEmployeeBarClass()`：`BarColor.EMPLOYEE_CLASS`
  - HTMLのdata属性：`section[data-group]` の `data-bar-class`、`option` の `data-admin`、`#shift-form` の `data-employee-bar-class`、行内のバー要素 `[data-bar]`

- [ ] **Step 1: 転記画面の失敗するテストを書く**

`ShiftDayTest` に追加：

```java
    @Test
    void 登録済みの行にバーを付け_管理者は社員の緑になる() throws Exception {
        kitchen.setColor("sky");
        positionMapper.update(kitchen);
        User boss = userMapper.findById(admin.getId());
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.shift(boss, kitchen, OCT2, "17:00", "19:00");

        ShiftDayView view = view("2026-10-02");

        ShiftGroupView kitchenGroup = view.getGroups().get(0);
        assertThat(kitchenGroup.getBarClass()).isEqualTo("bg-sky-300 text-sky-950");
        assertThat(view.getEmployeeBarClass()).isEqualTo("bg-green-400 text-green-950");
        assertThat(kitchenGroup.getRows())
                .extracting(ShiftRowView::getUserId, ShiftRowView::getBarClass, ShiftRowView::getBarStyle)
                .containsExactly(
                        tuple(taro.getId().toString(), "bg-sky-300 text-sky-950", "left:6.6667%;width:53.3333%"),
                        tuple(boss.getId().toString(), "bg-green-400 text-green-950", "left:60.0000%;width:13.3333%"));
        assertThat(kitchenGroup.getOtherCandidates()).filteredOn(c -> c.getName().equals("店長"))
                .extracting(ShiftCandidate::isAdmin).containsExactly(true);
    }

    @Test
    void 空欄行にはバーを出さず_色はdata属性で渡す() throws Exception {
        kitchen.setColor("sky");
        positionMapper.update(kitchen);

        ShiftDayView view = view("2026-10-02");
        assertThat(view.getGroups().get(0).getRows()).allSatisfy(row -> {
            assertThat(row.getBarClass()).isNull();
            assertThat(row.getBarStyle()).isNull();
        });

        mvc.perform(get("/admin/shifts").param("date", "2026-10-02").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("data-bar-class=\"bg-sky-300 text-sky-950\"")))
                .andExpect(content().string(
                        Matchers.containsString("data-employee-bar-class=\"bg-green-400 text-green-950\"")))
                .andExpect(content().string(Matchers.containsString("data-admin=\"true\"")))
                .andExpect(content().string(Matchers.containsString("data-bar")));
    }
```

`ShiftDayTest` に `@Autowired PositionMapper positionMapper;`・`@Autowired UserMapper userMapper;` のフィールドと、import（`static org.assertj.core.api.Assertions.tuple`、`jp.bk.shiftmanager.mapper.PositionMapper`、`jp.bk.shiftmanager.mapper.UserMapper`、`org.springframework.beans.factory.annotation.Autowired`）を追加する。

`ShiftSaveTest` に追加（Review Focus 5）：

```java
    @Test
    void 登録できずに表示し直すとき_時刻が不正な行にはバーを出さない() throws Exception {
        String[][] invalid = {{"13:00", "12:00"}, {"12:00", "12:00"}, {"abc", "12:00"}, {"09:00", ""}};
        for (String[] times : invalid) {
            MockHttpServletRequestBuilder request = save("2026-10-02");
            row(request, 0, kitchen, taro, times[0], times[1]);
            MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
            ShiftDayView view = (ShiftDayView) result.getModelAndView().getModel().get("view");
            assertThat(view.getGroups().get(0).getRows().get(0).getBarStyle()).isNull();
        }

        // 存在しないスタッフIDでも、時刻が正しければポジションの色でバーを出す
        MvcResult result = mvc.perform(save("2026-10-02")
                        .param("rows[0].positionId", kitchen.getId().toString()).param("rows[0].userId", "99999")
                        .param("rows[0].startTime", "09:00").param("rows[0].endTime", "17:00"))
                .andExpect(status().isOk())
                .andReturn();
        ShiftDayView view = (ShiftDayView) result.getModelAndView().getModel().get("view");
        assertThat(view.getGroups().get(0).getRows().get(0).getBarClass()).isEqualTo("bg-stone-300 text-stone-950");
        assertThat(view.getGroups().get(0).getRows().get(0).getBarStyle()).isEqualTo("left:6.6667%;width:53.3333%");
    }
```

- [ ] **Step 2: テストが失敗することを確認する**

Run: `./mvnw -q test -Dtest=ShiftDayTest,ShiftSaveTest`
Expected: コンパイルエラー（`getBarClass`・`isAdmin` がない）

- [ ] **Step 3: DTOを変更する**

`ShiftCandidate.java` に追加（`name` の下）：

```java
    /** 管理者なら転記画面のバーを社員の緑にする */
    private boolean admin;
```

`ShiftGroupView.java` に追加（`positionName` の下）：

```java
    /** ポジションの色のクラス（管理者以外のバーに使う） */
    private String barClass;
```

`ShiftRowView.java` に追加（`warning` の下）：

```java
    /** バーの色のクラス（名前・IN・OUTのどれかが未選択、またはIN ≥ OUTならnull） */
    private String barClass;
    /** バーの左端と幅（バーを出さない行はnull） */
    private String barStyle;
```

`ShiftDayView.java` に追加（`groups` の下）：

```java
    /** 社員（管理者）のバーの色のクラス。名前を選び直したときに shifts.js が使う */
    private String employeeBarClass;
```

- [ ] **Step 4: ShiftServiceを変更する**

`toCandidate`：

```java
    private ShiftCandidate toCandidate(User user) {
        return new ShiftCandidate(user.getId().toString(), user.getName(), user.isAdmin());
    }
```

`buildView` のグループのループで、`toRowView` の呼び出しに `position` と `usersById` を渡し、グループに色を詰める：

```java
            List<ShiftRowView> rows = new ArrayList<>();
            for (ShiftRowForm input : inputs) {
                rows.add(toRowView(index++, input, requests, position, usersById));
            }
            if (addBlankRows) {
                for (int i = 0; i < BLANK_ROWS; i++) {
                    rows.add(toRowView(index++, new ShiftRowForm(), requests, position, usersById));
                }
            }
            ShiftGroupView group = new ShiftGroupView();
            group.setPositionId(position.getId());
            group.setPositionName(position.getName());
            group.setBarClass(BarColor.ofKey(position.getColor()).getBarClass());
```

`buildView` の `view.setGroups(groups);` の下に追加：

```java
        view.setEmployeeBarClass(BarColor.EMPLOYEE_CLASS);
```

`toRowView` を次に置き換える（バーは名前未選択の判定より前に決める）：

```java
    private ShiftRowView toRowView(int index, ShiftRowForm input, Map<String, ShiftRequest> requests,
            Position position, Map<Long, User> usersById) {
        ShiftRowView row = new ShiftRowView();
        row.setIndex(index);
        row.setUserId(input.getUserId());
        row.setStartTime(input.getStartTime());
        row.setEndTime(input.getEndTime());
        LocalTime start = parseTimeOrNull(input.getStartTime());
        LocalTime end = parseTimeOrNull(input.getEndTime());
        TimeBar bar = TimeBar.of(start, end);
        if (!isBlank(input.getUserId()) && bar != null) {
            // 存在しないスタッフID（登録時に入力エラーになる）は管理者でないものとして扱う
            User user = usersById.get(parseId(input.getUserId()));
            row.setBarClass(BarColor.barClass(position.getColor(), user != null && user.isAdmin()));
            row.setBarStyle(bar.style());
        }
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
        row.setWarning(ShiftWarnings.of(start, end, requested));
        return row;
    }
```

`parseId` は数値でなければnullを返すため、`usersById.get(null)` はnullになる（`HashMap` 由来の `Collectors.toMap` はnullキーの `get` を許す）。import に `jp.bk.shiftmanager.util.BarColor`、`jp.bk.shiftmanager.util.TimeBar` を追加する。

- [ ] **Step 5: テストが通ることを確認する**

Run: `./mvnw -q test -Dtest=ShiftDayTest,ShiftSaveTest`
Expected: `data-bar-class` などHTMLを確かめるテスト以外はPASS（テンプレートは次のステップで変更する）

- [ ] **Step 6: テンプレートを変更する**

`templates/admin/shifts/day.html`：
- `<form id="shift-form" ...>` に `th:data-employee-bar-class="${view.employeeBarClass}"` を追加する
- `<section th:each="g : ${view.groups}" ...>` に `th:data-bar-class="${g.barClass}"` を追加する
- 表の `min-w-3xl` を `min-w-4xl` に変える
- 見出し行の `<th class="px-2 py-2">備考</th>` の下に追加：

```html
            <th class="px-2 py-2 text-xs font-normal text-stone-500">8〜23時</th>
```

`templates/admin/shifts/row.html`：
- 2つの `<option th:each="c : ...">` に `th:data-admin="${c.admin}"` を追加する
- 備考の `<td>` の下に追加：

```html
    <!-- 時間のミニバー（名前・IN・OUTを変えると shifts.js が描き直す） -->
    <td class="px-2 py-3">
      <div class="time-grid relative h-5 w-40 rounded bg-stone-50">
        <div data-bar class="absolute inset-y-0.5 rounded" th:classappend="${row?.barClass}"
             th:style="${row?.barStyle}" th:hidden="${row?.barStyle == null}"></div>
      </div>
    </td>
```

- [ ] **Step 7: テストが通ることを確認する**

Run: `./mvnw -q test -Dtest=ShiftDayTest,ShiftSaveTest`
Expected: PASS

- [ ] **Step 8: shifts.jsにバーの描き直しを追加する**

`refreshRow` の定義の直前に追加：

```js
  // 時間のミニバー（TimeBar と同じ式：8:00〜23:00を100%とする）
  const BAR_FIRST = 8 * 60;
  const BAR_SPAN = 15 * 60;
  const BAR_BASE_CLASS = 'absolute inset-y-0.5 rounded';
  const minutesOf = (hhmm) => {
    const [h, m] = hhmm.split(':').map(Number);
    return h * 60 + m;
  };
  const percentOf = (minutes) => `${(minutes * 100 / BAR_SPAN).toFixed(4)}%`;
  const refreshBar = (row) => {
    const bar = row.querySelector('[data-bar]');
    const select = row.querySelector('select[data-user]');
    const start = row.querySelector('select[data-in]').value;
    const end = row.querySelector('select[data-out]').value;
    // 時刻は HH:mm のため文字列のまま比較できる
    if (!select.value || !start || !end || start >= end) {
      bar.hidden = true;
      return;
    }
    const admin = select.selectedOptions[0].dataset.admin === 'true';
    const colorClass = admin ? form.dataset.employeeBarClass : row.closest('[data-group]').dataset.barClass;
    bar.className = `${BAR_BASE_CLASS} ${colorClass}`;
    bar.style.left = percentOf(minutesOf(start) - BAR_FIRST);
    bar.style.width = percentOf(minutesOf(end) - minutesOf(start));
    bar.hidden = false;
  };
```

`refreshRow` の先頭（`const userId = ...` の前）に追加し、名前・IN・OUTの変更、クリア、行の追加、希望シフトの反映のすべてで描き直されるようにする：

```js
    refreshBar(row);
```

- [ ] **Step 9: ブラウザで確認する**

`npm run build` の後にアプリを起動し、`/admin/shifts?date=...` を開く。確認すること：
- 登録済みの行にバーが出て、日別一覧と同じ位置・色
- 名前・IN・OUTを変えるとバーが動く。IN ≥ OUT・未選択・「クリア」でバーが消える
- 管理者を選ぶと緑、それ以外はポジションの色
- 「+ 追加する」の行と「希望シフトを反映する」で埋まった行にもバーが出る
- スマホ幅では今までどおり表の枠内で横スクロールする

確認後、起動したアプリを停止する。

- [ ] **Step 10: 仕様書を更新する**

`documents/2026-09-25-shift-manager-v2-spec.md` の「確定シフトの転記（管理者）」の「画面構成」の箇条書きの末尾に追記：

```markdown
- 各行の右端に、8:00〜23:00の時間軸上でIN〜OUTを表すミニバーを表示する。色は、管理者なら緑（社員）、それ以外はその行のポジションの色。名前・IN・OUTを変えるとその場で描き直す。名前・IN・OUTのどれかが未選択、またはIN ≥ OUTの行には出さない（2026-10-02 追加）
```

- [ ] **Step 11: 全テストを実行する**

Run: `./mvnw -q test`
Expected: PASS

- [ ] **Step 12: コミットし、この計画のTask 3のチェックボックスを更新する**

```bash
git add -A src/main/java src/main/resources/templates src/main/resources/static/js src/test documents docs/superpowers/plans/2026-10-02-plan6-shift-time-bar.md
git commit -m "feat: 転記画面の各行に時間のミニバーを付ける

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
