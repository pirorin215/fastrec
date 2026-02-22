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

- **複数の文字起こしプロバイダー対応**: Google Cloud Speech-to-Text または Groq (Whisper) を選択可能
- **AIボタン**: 録音内容に対するAI応答を生成（Groq使用時はGroq、それ以外の場合はGemini APIで応答生成）
- **ワイヤレス転送**: 録音データをBLE経由でスマホに転送
- **Tasks連携**: 文字起こし結果をGoogle Tasksに自動登録
  - 通常録音: タイトルに文字起こし、詳細に全文
  - AI録音: タイトルに文字起こし、詳細にAI応答
- **振動フィードバック**: ボタン押下中のみ録音するハプティクスフィードバック
- **低電圧通知**: 電池電圧を監視し、設定電圧を下回るとAndroidアプリに通知（充電忘れ防止）
- **バッテリー効率**: Deep Sleepモード対応で長時間駆動（150mAhで約5日）
- **オーディオ圧縮**: IMA ADPCMフォーマットでストレージ節約
- **バックグラウンド動作**: Androidアプリのバックグラウンドサービス対応

## 📋 要件

### ハードウェア

必要な部品と接続方法は [hardware/parts.md](hardware/parts.md) を参照してください。

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

## 🔧 セットアップ

### リポジトリのクローン

```bash
git clone https://github.com/pirorin215/fastrec.git
cd fastrec
```

### 1. ハードウェアの作成

詳細な部品表、接続ピン割り当て、回路図は [hardware/parts.md](hardware/parts.md) を参照してください。

### 2. Androidアプリのインストール

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

### 3. ファームウェアの書き込み

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
   - 「esp32 by Espressif Systems」を**バージョン3.3.4**でインストール
     - **重要**: バージョン3.3.7はNimBLEと互換性がなく、コアダンプが発生します

4. **必要なライブラリをインストール**（初回のみ）
   - 左側のサイドバーにあるライブラリマネージャアイコン（📚のような本のアイコン）をクリック
   - またはメニューから「Tools」→「Manage Libraries...」を選択
   - 以下のライブラリを検索してインストール：
     - `NimBLE-Arduino` by h2zero（BLE通信）
     - `ArduinoJson` by Benoit Blanchon（JSON処理）
     - `72x40oled_lib`（OLED表示）
   - 詳細は [EXTERNAL_LIBRARIES.md](fastrec_alt/EXTERNAL_LIBRARIES.md) を参照
     - **ESP32コアバージョンに関する重要な注意事項があります**

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

## 🔌 初期設定

セットアップ完了後、初回使用時に以下の設定を行います。

**いずれかの方式を選択してください**（推奨: Groq + GAS方式）

### 共通設定：BLEペアリング

- ESP32の電源を入れる
- Androidアプリで「スキャン」をタップ
- `FastRec-XXXX` をタップして接続

---

### 推奨：Groq + GAS方式

Groq APIを使用した最も簡単な設定方式です。文字起こしもAI応答もGroqで処理され、Google Tasks連携にはGAS方式を使用します。

#### 1. Groq API設定

- [Groq Console](https://console.groq.com/) にアクセス
- アカウントを作成またはログイン
- 左側メニューから「API Keys」を選択
- 「Create API Key」をクリックしてAPIキーを取得
- Androidアプリの設定画面で:
  - 「文字起こしプロバイダー」で「Groq (Whisper)」を選択
  - 「Groq API Key」にAPIキーを入力

**料金について（2025年1月時点）**:
- Groqは現在**非常に寛大な無料枠**を提供しています
- 個人利用であれば、まず無料枠で十分です
- 詳細: [Groq Pricing](https://groq.com/pricing/)

**推奨モデル**:
- `whisper-large-v3-turbo`: 高速かつ高精度な文字起こし
- 日本語対応で優れた認識精度

#### 2. GAS方式の設定（Google Tasks連携）

GCPプロジェクトの作成やOAuth設定が不要な設定方式です。詳細: [docs/gas_setup.md](docs/gas_setup.md)

---

### 代替：GCP方式

Google Cloud Platformを既に利用しているユーザー向けの設定方式です。

#### 1. Google Cloud Speech-to-Text設定

- [Google Cloud Console](https://console.cloud.google.com) でAPIキーを取得
- Androidアプリの設定画面でAPIキーを入力

#### 2. Gemini API設定（AIボタン使用時）

AIボタンを使用してAI応答を生成する場合に必要です。

- [Google AI Studio](https://aistudio.google.com/app/api-keys) にアクセス
- Googleアカウントでログイン（まだの場合はログインしてください）
- **初回のみ**: 利用規約が表示されるので「同意する」をクリック
- 「Create API Key」ボタンをクリック
- **Google Cloudプロジェクトの作成/選択**:
  - 「Create a new Google Cloud project」を選択
  - プロジェクト名を入力（任意、例: `FastRec-Gemini`）
  - 「Create」ボタンをクリック
- APIキーが自動生成されるので、コピー

- **重要 - 請求先アカウントのリンク（必須）**:
  - APIキー画面で、作成したAPIキーの右側にある「お支払い情報を設定」をクリック
  - Google Cloud Consoleに自動的に遷移します
  - 「このプロジェクトには請求先アカウントがありません」と表示されます
  - 「請求先アカウントをリンク」をクリック
  - 既存の請求先アカウントを選択するか、新しい請求先アカウントを作成
    - 新規作成の場合：
      - 国/地域を選択（「日本」など）
      - プロフィール情報を入力
      - お支払い方法を登録（クレジットカード等）
  - 「リンク」または「アカウントを設定」をクリック
  - 請求先アカウントがリンクされたことを確認
  - [Google AI Studio](https://aistudio.google.com/app/api-keys) に戻り、APIキーが有効になっていることを確認

- Androidアプリの設定画面で「Gemini API Key」に入力

**重要 - 予算上限の設定（推奨）**:

Gemini API は従量課金ですが、無料枠を使い切ると有料になります。予期せぬ課金を防ぐため、予算上限を設定することを強く推奨します。

**予算上限設定手順**:
1. [Google Cloud Console - 請求先アカウント](https://console.cloud.google.com/billing) にアクセス
2. 該当の請求先アカウントがリンクされていることを確認
3. 左側メニューから「お支払い」→「予算とアラート」を選択
4. 「予算を設定」をクリック
5. 予算金額を入力（例: 500円）
6. 予算に達した時のアクションを選択:
   - メール通知を受け取る（推奨）
   - API を無効にする（強く推奨）
7. 「保存」をクリック

**料金目安（2025年1月時点）**:
- Gemini 2.0 Flash: 個人利用で月額 **100円〜200円程度**（使用頻度によります）
- 詳細: [Google AI Pricing](https://ai.google.dev/pricing)

**注意**: AIボタンを使用するには、請求先アカウントの設定と予算上限の設定が推奨されます。設定しない場合、無制限に課金される可能性があります。

#### 3. Google Sign-In設定（Google Tasks連携）

Google Tasks APIを使用してGoogle Tasks連携を行う場合に必要です。

1. **GCPプロジェクトでOAuth同意画面を設定**
   - [Google Cloud Console](https://console.cloud.google.com) でプロジェクトを選択
   - 「APIとサービス」→「OAuth同意画面」を選択
   - 「外部」を選択して「作成」をクリック
   - 必要な情報を入力（アプリ名、ユーザーサポートメールなど）
   - 「保存して次へ」をクリック
   - スコープの追加は不要（「保存して次へ」をクリック）
   - テストユーザーの追加は不要（「保存して次へ」をクリック）

2. **OAuth 2.0クライアントIDを作成**
   - 「APIとサービス」→「認証情報」を選択
   - 「認証情報を作成」→「OAuthクライアントID」をクリック
   - アプリケーションの種類で「Android」を選択
   - 以下の情報を入力:
     - **パッケージ名**: `com.pirorin215.fastrecmob`
     - **SHA-1証明書フィンガプリント**: Androidアプリの署名証明書のSHA-1フィンガプリント
       - デバッグ版: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`
       - リリース版: アプリの署名時に使用したキーストアのSHA-1フィンガプリント
   - 「作成」をクリック
   - 表示されるクライアントIDをコピー（ただし、Androidアプリでは自動的に検出されます）

3. **AndroidアプリでGoogle Sign-In**
   - FastRecアプリで「Google Tasks同期設定」を開く
   - 「Google Tasks にサインイン」をタップ
   - Googleアカウントを選択してサインイン
   - サインインが完了すると、Google Tasksとの同期が可能になります

## 📖 使い方

初期設定が完了したら、以下の手順で録音します。

### 通常録音

1. **録音ボタン（REC）**を押しつづける
2. 録音レコーダが振動する
3. 喋る
4. ボタンを離す

自動的に以下が行われます：
- Androidアプリにデータ転送
- 音声認識・文字起こし
- Google Tasksに登録（タイトル: 文字起こし、詳細: 全文）

### AI録音

AIボタンを使用すると、AIによる応答付きでタスク登録されます。

1. **AIボタン**を押しつづける
2. 録音レコーダが振動する
3. 喋る
4. ボタンを離す

自動的に以下が行われます：
- Androidアプリにデータ転送
- 音声認識・文字起こし
- **AIによる応答を生成**
  - 文字起こしプロバイダーがGroqの場合: Groqで応答生成
  - 文字起こしプロバイダーがGoogle Cloud Speech-to-TextまたはGAS方式の場合: Gemini APIで応答生成
- Google Tasksに登録（タイトル: 文字起こし、詳細: AI応答）
- 通知にはAI応答が表示されます

**注意**: AIボタンを使用するには、事前にAI応答生成用のAPI設定が必要です：
- Groqを文字起こしプロバイダーとして選択している場合: 追加の設定は不要（Groq APIキーで完結）
- Google Cloud Speech-to-TextまたはGAS方式の場合: Gemini APIキーの設定が必要

![Androidアプリのスクリーンショット](images/app_screenshot.png)
*(画像: アプリ画面 - 準備中)*

## 📚 詳細ドキュメント

- [Androidアプリ詳細](FastRecMob/README.md)
- [ファームウェア詳細](fastrec_alt/README.md)
- [システムアーキテクチャ](docs/architecture.md)
- [部品表・接続ガイド](hardware/parts.md)
- [トラブルシューティング](docs/troubleshooting.md)
- [Google Apps Script (GAS) でGoogle Tasks連携](docs/gas_setup.md)

## 🔧 既知の問題・制限事項

- Android 12以降でのBLE接続制限があります
- 音声認識にはネットワーク接続が必要です
- Google Cloud Speech API の利用制限に注意してください
- **Google Tasks連携**:
  - 新規タスクの追加のみ対応（更新・削除はできません）
  - 再文字起こし時は、新しいタスクとして再登録されます
- Groq API（文字起こしプロバイダーとして選択時）:
  - 現在は寛大な無料枠がありますが、将来の変更には注意してください
  - 詳細: [Groq Pricing](https://groq.com/pricing/)
- Gemini API（AIボタン）は従量課金です:
  - 無料枠を使い切ると有料になります（月額100円〜200円程度が目安）
  - **必ず予算上限を設定してください**（上記の手順参照）
  - 詳細: [Google AI Pricing](https://ai.google.dev/pricing)

## 📄 ライセンス

このプロジェクトは [MIT License](LICENSE) の下でライセンスされています。

## 🙏 謝辞

- [NimBLE-Arduino](https://github.com/h2zero/NimBLE-Arduino) - BLEライブラリ
- [Google Cloud Speech-to-Text](https://cloud.google.com/speech-to-text) - 音声認識API
- [Groq](https://groq.com/) - 高速AI推論プラットフォーム（Whisper文字起こし）
- [Google Gemini API](https://ai.google.dev/) - AI応答生成
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Android UIフレームワーク

---

**最終更新**: 2025-01-21
