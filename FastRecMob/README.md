# FastRecMob - Androidアプリ

FastRecデバイスと連携するAndroidアプリケーション。Kotlin + Jetpack Composeで構築されています。

## 特徴

- **BLEスキャン・接続**: 近接するFastRecデバイスの自動検出
- **データ転送**: 録音データのBLE経由での転送
- **音声認識**: Google Cloud Speech-to-Textとの統合
- **Tasks連携**: 文字起こし結果をGoogle Tasksに登録
- **バックグラウンド実行**: 常駐サービスによる自動接続
- **パラメータ設定**: デバイスパラメータの変更（振動フィードバック含む）

## 技術スタック

- **言語**: Kotlin
- **UI**: Jetpack Compose + Material3
- **非同期処理**: Kotlin Coroutines + Flow
- **通信**: Retrofit + OkHttp
- **データ永続化**: DataStore Preferences
- **グラフ**: Vico
- **クラウド**: Google Cloud Speech-to-Text

## ビルド

```bash
./gradlew assembleDebug    # デバッグビルド
./gradlew assembleRelease  # リリースビルド
```

## 依存関係

主なライブラリ（gradle/libs.versions.tomlで管理）：

- androidx.core.ktx
- androidx.compose.* (BOM管理)
- androidx.lifecycle.*
- retrofit2
- okhttp3
- google.cloud.speech
- kotlinx.serialization

## パーミッション

- BLUETOOTH_SCAN / BLUETOOTH_CONNECT
- ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION
- FOREGROUND_SERVICE / FOREGROUND_SERVICE_CONNECTED_DEVICE
- INTERNET
- POST_NOTIFICATIONS

## アーキテクチャ

- `MainActivity`: アプリのエントリーポイント
- `BleScanService`: バックグラウンドBLEスキャン
- `BootCompletedReceiver`: 起動時自動実行
