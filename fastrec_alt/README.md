# fastrec_alt - ESP32-S3ファームウェア

FastRecデバイス用のESP32-S3ファームウェア。BLE通信、オーディオ録音、LCD表示を制御します。

## 特徴

- **BLE通信**: NimBLEライブラリによる低消費電力Bluetooth
- **オーディオ録音**: I2Sマイク入力 + IMA ADPCM圧縮
- **振動フィードバック**: ボタン押下中のみ録音するハプティクスフィードバック
- **ファイル保存**: LittleFSによるフラッシュ保存
- **省電力**: Deep Sleepモード対応
- **ボタン制御**: ハードウェアボタンによる録音操作

## ファイル構成

- `fastrec_alt.ino`: メイン処理・ステートマシン
- `audio.ino`: オーディオ録音・エンコード
- `ble_setting.ino`: BLE設定・GATTサーバー
- `lcd_display.ino`: OLED表示制御
- `utils.ino`: ユーティリティ関数

## 設定

### ファイル構成

- `common.sh`: 共通関数ライブラリ
  - ESPポートチェックなどの共通処理を提供
  - 各スクリプトから自動的に読み込まれます
  - バージョン管理対象（変更不要）

- `setting.sh`: ユーザー設定ファイル
  - ESP_PORTなどのユーザー固有の設定を管理
  - `.gitignore` に含まれており、バージョン管理されません
  - `setting.sh.example` をコピーして作成します

### シリアルポートの設定（必須）

ファームウェア書き込みやログ表示に使用するシリアルポートを設定します。

**重要**: 自動検出を廃止し、明示的にポートを指定することで、意図しないデバイスへのファームウェア書き込みやログ出力を防ぐためです。

#### 設定手順

1. `setting.sh.example` をコピーして `setting.sh` を作成：

```bash
cp setting.sh.example setting.sh
```

2. `setting.sh` を編集して `ESP_PORT` を設定：

```bash
# ESP32 デバイスのシリアルポートを指定
ESP_PORT="/dev/cu.usbmodem21201"  # 自分の環境に合わせて変更
```

3. ポート名の確認方法：

```bash
# macOS の場合
ls /dev/cu.usbmodem*

# Linux の場合
ls /dev/ttyUSB* /dev/ttyACM*
```

**注意**: `setting.sh` は `.gitignore` に含まれており、バージョン管理されません。各開発者が自分の環境に合わせて設定してください。

## ビルド

```bash
./compile.sh    # コンパイル
./upload.sh     # ESP32-S3に書き込み（setting.sh で設定されたポートを使用）
```

またはArduino IDE 2.xで `fastrec_alt.ino` を開いて書き込みます。

**注意**: `upload.sh` や `consolelog.sh` を実行する前に、必ず `setting.sh` で `ESP_PORT` を設定してください。未設定の場合はエラーで終了します。

## 外部ライブラリ

詳細は [EXTERNAL_LIBRARIES.md](EXTERNAL_LIBRARIES.md) を参照

| ライブラリ | バージョン | 用途 |
|-----------|----------|------|
| NimBLE-Arduino | 2.3.6 | BLE通信 |
| ArduinoJson | 7.4.2 | JSON処理 |
| 72x40oled_lib | 1.0.1 | OLED表示 |

※ LittleFS、Wire等はESP32コア(3.3.4)に含まれています

## ヘルパースクリプト

- `common.sh`: 共通関数ライブラリ（ESPポートチェック等）
- `bletool.py`: BLE設定ツール
- `check_ino.sh`: コードチェック
- `check_unused.sh`: 未使用コード検出
- `consolelog.sh`: シリアルログ表示
- `setting.sh.example`: 設定ファイルテンプレート
- `setting.sh`: シリアルポート設定（各ユーザーが作成）

## パーティション

`partitions.csv` で定義：
- app: 1.8MB
- spiffs: 0.7MB（LittleFS領域）

## 状態遷移

```
IDLE → REC → IDLE → DSLEEP
    ↓
SETUP → DSLEEP
```
