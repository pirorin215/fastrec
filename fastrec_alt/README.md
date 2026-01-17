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

## ビルド

```bash
./compile.sh    # コンパイル
./upload.sh     # ESP32-S3に書き込み
```

またはArduino IDE 2.xで `fastrec_alt.ino` を開いて書き込みます。

## 外部ライブラリ

詳細は [EXTERNAL_LIBRARIES.md](EXTERNAL_LIBRARIES.md) を参照

| ライブラリ | バージョン | 用途 |
|-----------|----------|------|
| NimBLE-Arduino | 2.3.6 | BLE通信 |
| LittleFS | 3.3.4 | ファイルシステム |
| ArduinoJson | 7.4.2 | JSON処理 |
| 72x40oled_lib | 1.0.1 | OLED表示 |
| Wire | 3.3.4 | I2C通信 |

## ヘルパースクリプト

- `bletool.py`: BLE設定ツール
- `check_ino.sh`: コードチェック
- `check_unused.sh`: 未使用コード検出
- `consolelog.sh`: シリアルログ表示

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
