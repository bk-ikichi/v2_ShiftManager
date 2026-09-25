## このプロジェクトのルール
- 日本語ですべて話すこと
- コメント等は日本語ですべて書くこと
- 客観的に指摘すること
- 重要な作業等は勝手に進めず必ず聞くこと
- 結論ファースト
- 絵文字、前置き禁止

## プロジェクトの概要
- 1店舗専用のシフト管理アプリ（v2）。`../ShiftManager`（v1）とは別物で、v1の仕様は引き継がない
- 仕様書：`documents/2026-09-25-shift-manager-v2-spec.md`
- DB設計：`documents/2026-09-25-db-design.md`
- 実装計画：`docs/superpowers/plans/`
- Java 17 / Spring Boot 4.1.1 / MyBatis / Spring Security / Thymeleaf / Tailwind CSS 4 / PostgreSQL 17（Neon）/ Docker

## パッケージ構成（層ごとに分ける）

`src/main/java/jp/bk/shiftmanager/` 配下：

| パッケージ | 役割 |
|---|---|
| controller | 画面のリクエスト処理。Serviceのみを呼ぶ |
| service | 業務ロジック・トランザクション。Repositoryのみを呼ぶ |
| repository | データアクセスの窓口。Mapperを包み、単件取得は `Optional` で返す |
| mapper | MyBatisのMapperインターフェース（SQLはアノテーションで直書き） |
| entity | テーブルに対応するクラス |
| dto | 画面表示用などテーブルと1対1でないクラス |
| form | 画面入力のフォームクラス（Bean Validation） |
| exception | 例外クラス |
| auth | Spring Security関連（設定・ログインユーザー・パスワードルール等） |
| config | Spring MVC等の設定 |
| util | 状態を持たない小さな共通処理 |

## 作業の進め方（重要）

- 実装計画は **1回のセッションで1 Taskだけ** 行う。Taskが終わったら（テストが通りコミットしたら）計画ファイルのチェックボックスを更新し、**そこで作業を停止してユーザーに報告する**。次のTaskには進まない
- ユーザーが `/clear` した後の新しいセッションで次のTaskを行う
- 新しいセッションでは、計画ファイル全体を読まない。以下の手順でコンテキストを節約する
  1. 計画ファイルの冒頭（Global Constraints・Review Focus・File Structureまで）を読む
  2. `- [ ]` が残っている最初の `### Task N` を Grep で探し、その Task の範囲だけを Read（offset/limit指定）で読む
  3. その Task の Interfaces に書かれた既存クラスだけを必要に応じて読む
- テスト実行には Docker Desktop の起動が必要（Testcontainers）
