# Datadog Monitor Widget for Android

Datadogのモニター状態をホーム画面で一目で確認できるAndroidウィジェットアプリです。
Jetpack Glanceを使用し、複数のウィジェットをそれぞれ異なるクエリや更新間隔で配置できます。

## 特徴

- **個別設定**: ウィジェットごとに異なるAPIキー、アプリケーションキー、検索クエリ、更新間隔を設定可能。
- **一目でわかるステータス**: モニターの「OK数 / 全体数」を大きく表示。
- **状態に応じた色変化**: Alertがあれば赤、Warnがあればオレンジ、すべてOKなら緑に背景色が変化。
- **最終更新時刻の表示**: いつデータが取得されたかを常に確認可能。
- **詳細画面**: ウィジェットをタップすると公式アプリ起動ではなく詳細画面を開き、監視対象モニターの状態やマルチアラート内訳を確認可能。
- **簡単配置**: アプリ内から直接ホーム画面にウィジェットを追加するショートカットボタンを搭載。

## スクリーンショット

| アプリ画面 | ウィジェット表示 (緑/OK) | ウィジェット表示 (赤/Alert) |
| :---: | :---: | :---: |
| <img src="app/src/main/res/drawable/ic_launcher.png" width="200"> | ![Widget OK](https://placehold.jp/24/159a31/ffffff/150x150.png?text=5/5%0A12:34) | ![Widget Alert](https://placehold.jp/24/f44336/ffffff/150x150.png?text=2/5%0A12:35) |

## セットアップとビルド方法

### 必要なもの
- Android Studio Ladybug 以降
- JDK 17 以上
- Datadogの API Key および Application Key

### ビルド手順
1. このリポジトリをクローンします。
   ```bash
   git clone https://github.com/gooyyu1/DatadogMonitorWidget.git
   ```
2. Android Studioでプロジェクトを開きます。
3. Gradleの同期が完了するのを待ちます。
4. `Run` ボタンを押して実機またはエミュレータにインストールします。

## 使い方

1. アプリを起動し、「Add Widget to Home Screen」をタップしてウィジェットを配置します（またはホーム画面を長押ししてウィジェット一覧から追加します）。
2. 配置時に表示される設定画面で、以下の情報を入力します。
   - **API Key / Application Key**: Datadogの管理画面から取得してください。
   - **Query**: 監視したいモニターの条件を指定します（例: `status:alert`, `tag:service:my-app`）。
   - **Datadog Site**: ご利用のリージョン（US1, EU1, AP1など）を選択します。
   - **Update Interval**: データを更新する頻度を選択します。
3. 「Save and Start」をタップすると、監視が開始されます。

## 技術スタック

- **UI**: Jetpack Compose, Jetpack Glance (Widget)
- **非同期処理/バックグラウンド**: Kotlin Coroutines, WorkManager
- **ネットワーク**: Retrofit2, OkHttp3, Kotlinx Serialization
- **データ保存**: Jetpack DataStore (Preferences)

## ライセンス

[MIT License](LICENSE) (または任意のライセンス)
