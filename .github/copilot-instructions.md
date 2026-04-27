# GitHub Copilot Instructions

このファイルは GitHub Copilot がリポジトリを効率的に理解して作業するための情報をまとめたものです。

---

## プロジェクト概要

**Datadog Monitor Widget for Android** は、Datadog のモニター状態をホーム画面で一目で確認できる Android ウィジェットアプリです。
Jetpack Glance を使用し、複数のウィジェットをそれぞれ異なる API キー・クエリ・更新間隔で配置できます。

---

## リポジトリ構成

```
DatadogMonitorWidget/
├── app/
│   ├── build.gradle.kts              # アプリモジュールのビルド設定
│   └── src/
│       ├── main/
│       │   ├── java/jp/yuki_yamada/datadogmonitorwidget/
│       │   │   ├── MainActivity.kt                  # アプリ起動画面（ウィジェット追加ボタン）
│       │   │   ├── WidgetConfigurationActivity.kt   # ウィジェット初期設定画面
│       │   │   ├── MonitorWidget.kt                 # Glance ウィジェット実装
│       │   │   ├── MonitorWidgetState.kt            # DataStore の読み書きラッパー
│       │   │   ├── MonitorWorker.kt                 # WorkManager による定期更新 Worker
│       │   │   ├── MonitorDataRepository.kt         # Datadog API と DataStore の統合層（単一責任）
│       │   │   ├── MonitorListActivity.kt           # モニター一覧画面（ウィジェットタップ時）
│       │   │   ├── MonitorBreakdownActivity.kt      # マルチアラートモニターのグループ内訳画面
│       │   │   ├── DatadogApiClient.kt              # Retrofit HTTP クライアント
│       │   │   ├── DatadogApiService.kt             # Retrofit API インターフェース定義
│       │   │   ├── DatadogModels.kt                 # データモデルと変換ロジック
│       │   │   ├── DatadogSettings.kt               # DataStore によるアプリ設定管理
│       │   │   └── ui/theme/                        # Compose テーマ
│       │   └── res/                                 # リソース（レイアウト、drawable 等）
│       └── test/
│           └── java/jp/yuki_yamada/datadogmonitorwidget/
│               ├── DatadogApiServiceTest.kt         # API サービスのユニットテスト
│               ├── DatadogModelsTest.kt             # モデル変換ロジックのユニットテスト
│               ├── MonitorDataRepositoryTest.kt     # リポジトリのユニットテスト
│               ├── MonitorListActivityTest.kt       # 一覧画面ロジックのユニットテスト
│               └── MonitorWorkerStateUpdateTest.kt  # Worker の状態更新テスト
├── build.gradle.kts                  # プロジェクトレベルのビルド設定
├── settings.gradle.kts               # Gradle モジュール設定
└── gradle/
    └── libs.versions.toml            # バージョンカタログ（依存関係管理）
```

---

## アーキテクチャ

```
[ホーム画面ウィジェット]
        │ タップ
        ▼
[MonitorListActivity] ─────────────────────────────────────┐
        │ モニター選択（マルチアラートのみ）                   │
        ▼                                                    │
[MonitorBreakdownActivity]                                   │
                                                             │
[WorkManager スケジューラ]                                    │
        │ 定期実行                                            │
        ▼                                                    │
[MonitorWorker]                                              │
        │                                                    │
        ▼                                                    │
[MonitorDataRepository] ◄────────────────────────────────────┘
  ├── DatadogApiClient → DatadogApiService（Retrofit）
  │        └── GET /api/v1/monitor/search
  │        └── GET /api/v1/monitor/{id}?group_states=all
  │        └── POST /api/v1/monitor/{id}/mute
  └── DataStore（Glance Preferences）
           └── MonitorWidgetState / MutableMonitorWidgetState
```

### 主要クラスの責務

| クラス | 責務 |
|---|---|
| `MonitorDataRepository` | Datadog API 呼び出しと DataStore 保存の**単一の責任箇所**。`MonitorWorker`・`MonitorListActivity`・`MonitorBreakdownActivity` から使用される。 |
| `MonitorWorker` | WorkManager CoroutineWorker。`MonitorDataRepository.refresh()` を呼び出し、完了後に次回実行をスケジュール（チェーン実行）。 |
| `MonitorWidget` | Jetpack Glance による Appwidget。DataStore の状態を読み取って表示を更新する。 |
| `MonitorWidgetState` | DataStore の `Preferences` を型安全に読み取るラッパークラス。 |
| `MutableMonitorWidgetState` | DataStore の `MutablePreferences` を型安全に書き込むラッパークラス。 |
| `DatadogApiClient` | Retrofit を使って Datadog API を呼び出す。モニター検索・詳細取得・ミュートを担当。 |
| `DatadogApiService` | Retrofit の API インターフェース定義。 |
| `DatadogModels` | `Monitor`, `MonitorDetail`, `MonitorStatus` など全データモデルと変換関数を定義。 |

---

## データフロー（更新サイクル）

1. `MonitorWorker.doWork()` が WorkManager により起動
2. `MonitorDataRepository.getSettings()` で DataStore から設定を読み込む
3. `MonitorDataRepository.refresh()` で Datadog API を呼び出してモニター情報を取得
   - `DatadogApiClient.searchDetailedMonitors(query)` → 一覧取得
   - 各モニターの `getMonitorDetail(id)` を並列実行 → ミュート状態やグループ情報を取得
4. 取得結果を DataStore（Glance Preferences）に書き込む
5. `MonitorWidget().update()` でウィジェットの再描画をトリガー
6. 次回実行を `WorkManager.enqueueUniqueWork()` で登録（チェーン実行）

---

## モニターステータスの仕様

- **優先順位**: `ALERT(0)` > `WARN(1)` > `MUTED(2)` > `OK(3)` > `NO_DATA(4)`
- ウィジェット背景色はウィジェット内の全モニターで最も優先度の高いステータスに従う
- ミュートされたモニター/グループはステータスを `MUTED` に変換し、OK と同様に「問題なし」としてカウント
- マルチアラートモニター（`isMultiMonitor` または `groupStatuses.size > 1`）は `MonitorBreakdownActivity` でグループ内訳を表示

### 表示テキスト仕様

- `statusText`: `"displayOkCount/total"` の形式（例: `"3/5"`）
  - `displayOkCount` = OK件数 + MUTED件数
- 設定なし・取得失敗時: `"NA"`

---

## ビルドとテストのコマンド

```bash
# リント＋ユニットテスト＋ビルドをまとめて実行（推奨）
./gradlew --no-daemon lintDebug testDebugUnitTest assembleDebug

# ユニットテストのみ
./gradlew --no-daemon testDebugUnitTest

# デバッグ APK のビルドのみ
./gradlew --no-daemon assembleDebug

# リントのみ
./gradlew --no-daemon lintDebug
```

> **注意**: Android エミュレータや実機を必要とするインストルメントテスト（`androidTest`）は CI 環境では実行しません。

---

## 依存関係の管理

バージョンは `gradle/libs.versions.toml` で一元管理されています。

主要な依存ライブラリ:

| カテゴリ | ライブラリ |
|---|---|
| UI | Jetpack Compose, Jetpack Glance (AppWidget) |
| ネットワーク | Retrofit2, OkHttp3, Kotlinx Serialization |
| データ保存 | Jetpack DataStore (Preferences) |
| 非同期処理 | Kotlin Coroutines, WorkManager |

---

## テストの書き方

- ユニットテストは `app/src/test/` 以下に配置する
- Android フレームワーク（Context 等）に依存しない純粋なロジックのテストを優先する
- DataStore の操作をテストする場合は `mutablePreferencesOf()` を使って Preferences を直接生成する（例: `MonitorWorkerStateUpdateTest`）
- テストクラス名は `<テスト対象クラス>Test` とする

### テストのサンプル

```kotlin
// DataStore ラッパーのテスト例（Android 非依存）
val prefs = mutablePreferencesOf(MonitorWidgetState.LAST_ERROR to "error")
val mutableState = MutableMonitorWidgetState(prefs)
assertEquals("error", mutableState.lastError)
```

---

## コーディング規約

- Kotlin の標準的なコーディングスタイルに従う
- コメントは**日本語**で記述する（既存コードに倣う）
- 公開 API・クラス・重要なロジックには KDoc コメントを付ける
- ウィジェットへの書き込みは必ず `MonitorDataRepository` を経由する（直接 DataStore を操作しない）
- `MonitorStatus.fromRaw()` を使って文字列から安全にステータスを変換する
- API のエラー時は `MonitorDetail` にエラー情報を `rawJson` フィールドに含めて返す（例外を握りつぶさない）

---

## Datadog API の仕様メモ

- 認証: HTTP ヘッダー `DD-API-KEY` と `DD-APPLICATION-KEY`
- モニター一覧: `GET /api/v1/monitor/search?query=<query>`
- モニター詳細: `GET /api/v1/monitor/{id}?group_states=all`
- ミュート: `POST /api/v1/monitor/{id}/mute` (Body: `{ scope?, end? }`)
- サイト URL はリージョンごとに異なる（US1: `https://api.datadoghq.com/`, EU1: `https://api.datadoghq.eu/` 等）
