# アーキテクチャ

## システム概要

FastRecは以下の3つの主要コンポーネントで構成されます：

1. **ESP32-S3ファームウェア** - 録音・BLE通信の制御
2. **Androidアプリ** - ユーザーインターフェース・音声認識
3. **クラウドサービス** - Google Cloud Speech-to-Text

## 通信フロー

```
┌─────────────────────────────┐      BLE       ┌──────────────┐
│  Android                    │ ←───────────→  │   ESP32-S3   │
│  com.pirorin215.fastrecmob  │               │  fastrec_alt  │
└─────────────────────────────┘               └──────────────┘
       │                              │
       │ HTTPS                        │
       ↓                              │
┌─────────────┐                       │
│ Google Cloud│                       │
│ Speech API  │                       │
└─────────────┘                       │
                                      │
                               ┌──────┴──────┐
                               │ I2S Mic     │
                               │ OLED        │
                               │ Buttons     │
                               └─────────────┘
```

## Androidアプリ構成

### レイヤー構造

```
Presentation:  Compose UI
        ↓
Domain:        ViewModel + UseCase
        ↓
Data:          Repository + BLE Service + DataStore
```

### 主要コンポーネント

| コンポーネント | 役割 |
|--------------|------|
| MainActivity | UIホスト、パーミッション処理 |
| BleScanService | バックグラウンドBLEスキャン |
| BleRepository | GATT通信・データ転送 |
| SpeechToTextService | Google Cloud Speech-to-Text API呼び出し |
| AppSettingsRepository | 設定の永続化（DataStore） |
| MainViewModel | UI状態管理・BLEオーケストレーション |

## ESP32-S3ファームウェア構成

### ステートマシン

```cpp
enum AppState {
    IDLE,     // 待機状態
    REC,      // 録音中
    SETUP,    // 設定モード
    DSLEEP    // ディープスリープ
};
```

### モジュール分割

| ファイル | 責務 |
|---------|------|
| fastrec_alt.ino | メインループ・状態遷移 |
| audio.ino | I2S録音・ADPCMエンコード |
| ble_setting.ino | BLE GATTサーバー |
| lcd_display.ino | OLED描画 |
| utils.ino | 共通ユーティリティ |

### BLE GATTサービス

- **Service UUID**: `4fafc201-1fb5-459e-8fcc-c5c9c331914b`（カスタムサービス）
- **Characteristic**:
  - COMMAND UUID: `beb5483e-36e1-4688-b7f5-ea07361b26aa`（Write）- コマンド送信
  - RESPONSE UUID: `beb5483e-36e1-4688-b7f5-ea07361b26ab`（Notify）- データ転送・ステータス通知
  - ACK UUID: `beb5483e-36e1-4688-b7f5-ea07361b26ac`（Write No Response）- 転送確認

## データフロー

### 録音開始フロー

1. Android: ユーザーが録音ボタンをタップ
2. Android: BLE経由で録音開始コマンド送信
3. ESP32: コマンド受信→I2S録音開始
4. ESP32: OLEDに録音中を表示
5. ESP32: ステータス変更をAndroidにNotify

### データ転送フロー

1. Android: 転送リクエスト送信
2. ESP32: LittleFSからデータ読み取り
3. ESP32: BLE MTUサイズで分割送信
4. Android: パケット受信→結合
5. Android: WAVファイルとして保存
