# FastRec

![License](https://img.shields.io/badge/License-MIT-blue.svg)
![Platform](https://img.shields.io/badge/platform-Android%20%7C%20ESP32--S3-green.svg)
![Arduino](https://img.shields.io/badge/Arduino-2.x-orange.svg)

FastRecは、ESP32-S3ベースのポータブル録音デバイスと専用のAndroidスマホアプリを連携させたシステムです。BLE（Bluetooth Low Energy）を通じてワイヤレスでデータ転送、文字起こし、Google Tasksへの登録が可能です。

## 📸 概要

FastRecは以下のコンポーネントで構成されるポータブル録音システムです：

- **ハードウェア**: ESP32-S3搭載のポータブル録音デバイス
- **Androidアプリ**: 録音制御、データ転送、音声認識
- **音声認識**: Google Cloud Speech-to-Text利用

## ✨ 特徴

- **ワイヤレス転送**: 録音データをBLE経由でスマホに転送
- **音声認識**: Google Cloud Speech-to-Text
- **Tasks連携**: 文字起こし結果をGoogle Tasksに自動登録
- **振動フィードバック**: ボタン押下中のみ録音するハプティクスフィードバック
- **低電圧通知**: 電池電圧を監視し、設定電圧を下回るとAndroidアプリに通知（充電忘れ防止）
- **バッテリー効率**: Deep Sleepモード対応で長時間駆動（150mAhで約5日）
- **オーディオ圧縮**: IMA ADPCMフォーマットでストレージ節約
- **バックグラウンド動作**: Androidアプリのバックグラウンドサービス対応

## 📋 要件

### ハードウェア

必要な部品と接続方法は [hardware/parts.md](hardware/parts.md) を参照してください。

主な構成部品:
- XIAO ESP32S3（マイコン）
- 外部アンテナ（XIAO ESP32S3に付属）
- I2Sマイク（SPH0645LM4H）
- 振動モーター
- リチウムイオン電池（401730 150mAh）
- タクトスイッチ x2
- 抵抗（10kΩ x2：電圧監視用）

合計費用: 約3000円（OLEDなし）

### ソフトウェア開発環境

#### Androidアプリ
- Android Studio Hedgehog以降
- JDK 11以上
- Android SDK API 36
- Gradle 8.x

#### ファームウェア
- Arduino IDE 2.x または PlatformIO
- ESP32-S3 ボードサポートパッケージ
- Python 3.x（bletool.py使用時）

## 🏗️ プロジェクト構成

```
fastrec/
├── FastRecMob/      # Androidアプリ
├── fastrec_alt/     # ESP32-S3ファームウェア
├── docs/            # 詳細ドキュメント
├── hardware/        # 回路図・部品表
└── images/          # スクリーンショット・接続図
```

## 🔧 セットアップ

### リポジトリのクローン

```bash
git clone https://github.com/pirorin215/fastrec.git
cd fastrec
```

### 1. ハードウェアの作成

FastRecデバイスを組み立てるには、以下の部品が必要です：

**主要部品**:
- XIAO ESP32S3（マイコン）
- 外部アンテナ（XIAO ESP32S3に付属）
- I2Sマイク（SPH0645LM4H）
- 振動モーター
- リチウムイオン電池（401730 150mAh）
- タクトスイッチ x2
- 抵抗（10kΩ x2：電圧監視用）

**合計費用**: 約3000円（OLEDなし）

**組み立て手順**:
1. 詳細な部品表、接続ピン割り当て、接続例は [hardware/parts.md](hardware/parts.md) を参照
2. 配線を確認してからファームウェア書き込みへ進んでください

### 2. ファームウェアの書き込み

1. **Android Studioを開く**

2. **プロジェクトを開く**
   - 「Open」をクリック
   - `FastRecMob` フォルダを選択して開く

3. **Gradle同期を待つ**
   - 自動的にGradle同期が開始されます
   - 画面下部のプログレスバーが完了するまで待ちます

4. **スマホを接続**
   - USBデバッグが有効なAndroidスマホをUSBで接続
   - 「開発者向けオプション」で「USBデバッグ」をオンにしてください

5. **アプリをインストール**
   - 上部の緑色の実行ボタン（▶）をクリック
   - または `Shift + F10` を押す
   - 接続したスマホにアプリがインストールされ、自動起動します

### ファームウェアの書き込み

Arduino IDE 2.xを使用する場合:

1. **Arduino IDE 2.xを開く**

2. **Seeed Studioボードサポートを追加**（XIAO ESP32S3の場合のみ、初回のみ）
   - 「File」→「Preferences」を開く
   - 「Additional Boards Manager URLs」の横にあるアイコンをクリック
   - 以下のURLを追加：
     ```
     https://files.seeedstudio.com/arduino/package_seeeduino_boards_index.json
     ```
   - 「OK」をクリック

3. **ESP32-S3ボードサポートをインストール**（初回のみ）
   - 左側のサイドバーにあるボードマネージャアイコン（📟のようなチップのアイコン）をクリック
     - サイドバーが表示されない場合は「View」→「Toggle Sidebar」で表示をオンにしてください
   - またはメニューから「Tools」→「Board」→「Boards Manager...」を選択
   - 検索欄に「esp32」と入力
   - 「esp32 by Espressif Systems」をインストール

4. **必要なライブラリをインストール**（初回のみ）
   - 左側のサイドバーにあるライブラリマネージャアイコン（📚のような本のアイコン）をクリック
   - またはメニューから「Tools」→「Manage Libraries...」を選択
   - 以下のライブラリを検索してインストール：
     - `NimBLE-Arduino` by h2zero（BLE通信）
     - `ArduinoJson` by Benoit Blanchon（JSON処理）
     - `72x40oled_lib`（OLED表示）
   - 詳細は [EXTERNAL_LIBRARIES.md](fastrec_alt/EXTERNAL_LIBRARIES.md) を参照

5. **スケッチを開く**
   - 「File」→「Open」
   - `fastrec_alt/fastrec_alt.ino` を選択

6. **ボードとポートを選択**
   - 上部のボード選択で「XIAO_ESP32S3」を選択
     - **エラーが出る場合**: Arduino IDEを再起動するか、別のボードを選択してから再度試してください
     - それでも解決しない場合は [トラブルシューティング](docs/troubleshooting.md) を参照してください
   - **ポート選択（重要）**:
     - XIAO ESP32S3を**まだ接続していない状態**でポート選択ドロップダウンを開き、表示されるポートを確認
     - XIAO ESP32S3を**USB接続**
     - もう一度ポート選択ドロップダウンを開き、**新しく増えたポート**を選択
       - Macの場合: `/dev/cu.usbmodemXXXX` のような名前（例: `/dev/cu.usbmodem21201`）
       - Windowsの場合: `COM3`、`COM4` のような番号付きのポート
       - 増えたポートが複数ある場合は、一度接続を外して再度試してください

7. **書き込み**
   - 左上の右矢印ボタン（→）をクリック
   - 書き込みが完了するまで待ちます

**補足**: CLIでのビルドも可能です（`fastrec_alt/compile.sh`, `fastrec_alt/upload.sh`）

## 📖 使い方

### 基本的な使用手順

1. **ハードウェアの準備**
   - [hardware/parts.md](hardware/parts.md) を参照してデバイスを組み立てる
   - XIAO ESP32S3の外部アンテナを接続してください

2. **ファームウェアの書き込み**
   - Arduino IDE または CLI でファームウェアを書き込む

3. **Androidアプリのインストール**
   - APKをインストールまたは Android Studio でビルド

4. **BLEペアリング**
   - ESP32の電源を入れる
   - Androidアプリで「スキャン」をタップ
   - `FastRec-XXXX` をタップして接続

5. **Google Cloud Speech API設定**
   - [Google Cloud Console](https://console.cloud.google.com) でAPIキーを取得
   - Androidアプリの設定画面でAPIキーを入力

6. **録音・転送・文字起こし**
   - デバイスの録音ボタンを押してる間録音
   - Androidアプリでデータ転送
   - 自動で音声認識・文字起こし
   - Google Tasksに登録

![Androidアプリのスクリーンショット](images/app_screenshot.png)
*(画像: アプリ画面 - 準備中)*

## 📚 詳細ドキュメント

- [Androidアプリ詳細](FastRecMob/README.md)
- [ファームウェア詳細](fastrec_alt/README.md)
- [システムアーキテクチャ](docs/architecture.md)
- [部品表・接続ガイド](hardware/parts.md)
- [トラブルシューティング](docs/troubleshooting.md)

## 🔧 既知の問題・制限事項

- Android 12以降でのBLE接続制限があります
- 音声認識にはネットワーク接続が必要です
- Google Cloud Speech API の利用制限に注意してください

## 📄 ライセンス

このプロジェクトは [MIT License](LICENSE) の下でライセンスされています。

## 🙏 謝辞

- [NimBLE-Arduino](https://github.com/h2zero/NimBLE-Arduino) - BLEライブラリ
- [Google Cloud Speech-to-Text](https://cloud.google.com/speech-to-text) - 音声認識API
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Android UIフレームワーク

---

**最終更新**: 2026-01-17
