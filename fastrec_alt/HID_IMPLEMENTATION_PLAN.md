# FastRec HIDキー操作機能実装計画

## コンテキスト

### 目的
FastRecデバイスのGPIOスイッチをHIDキーボード/メディアキーとしてAndroidで使用可能にするためのファームウェア実装計画。

### 背景
- **BikeClockファームウェアの実装**（/Users/yoshi/dev/Arduino/btclock/bikeclock/）: 7つのスイッチ（I/Oエキスパンダー使用）、動的キー変更機能
- **FastRecファームウェアの仕様**（/Users/yoshi/dev/Arduino/fastrec/fastrec_alt/）: 2つのGPIO（GPIO4、GPIO6）、固定キー実装、I/Oエキスパンダーなし
- **ユーザー要件**: 固定キーで実装、Androidアプリ側（FastRecMob）は変更不要

### 技術アーキテクチャ

```
FastRecデバイス（ESP32）
├─ GPIO4 → 固定HIDキー（例：音量アップ 0xE9）
├─ GPIO6 → 固定HIDキー（例：音量ダウン 0xEA）
├─ HIDサービス (0x1812)        → Android OSが直接接続
└─ カスタムGATTサービス          → FastRecMobアプリが接続（既存機能）
```

**重要**:
- HID接続とBLE接続は**同じデバイスの異なるサービス**として並列動作
- **アプリ側は変更不要**：Android OSがHIDを自動認識、アプリは既存BLE機能のみ使用

## 実装範囲

### ✅ ファームウェア側実装（必要）
FastRecデバイスのファームウェアに以下を実装：

1. **BLE HIDサービス実装**
   - HIDサービス (0x1812) の実装
   - HIDディスクリプタの定義
   - BLE GATTサービスとHIDサービスの同時アドバタイズ

2. **GPIO割り当て**
   - GPIO4: 音量アップ (HID Usage 0xE9)
   - GPIO6: 音量ダウン (HID Usage 0xEA)

3. **スイッチ検出とHID送信**
   - GPIO入力割り込み検出
   - HIDキーコード送信処理

### ❌ アプリ側実装（不要）
FastRecMobアプリへの変更は一切不要：

- 既存のBLE通信機能がそのまま動作
- Android OSがHID接続を自動管理
- 設定UI、BLEコマンド追加など不要

## BLEプロトコル仕様

**不要**: 固定キー実装のため、BLEでのキー設定送信は不要。

## 検証手順

### 1. ファームウェア実装
1. FastRecデバイスにHIDサービスを実装
2. GPIO4を固定キー（例：音量アップ 0xE9）に割り当て
3. GPIO6を固定キー（例：音量ダウン 0xEA）に割り当て
4. BLE GATTサービスとHIDサービスの両方をアドバタイズ

### 2. ペアリングテスト
1. Android端末のBluetooth設定でFastRecデバイスをスキャン
2. ペアリング実行
3. Android OSがHIDデバイスとして認識することを確認

### 3. HID動作テスト
1. FastRecデバイスのGPIO4スイッチを押下
2. Android端末で音量アップが動作することを確認
3. FastRecデバイスのGPIO6スイッチを押下
4. Android端末で音量ダウンが動作することを確認

### 4. BLE並列動作テスト
1. FastRecMobアプリを起動
2. FastRecデバイスにBLE接続
3. 時刻同期が動作することを確認（既存機能）
4. HIDスイッチが同時に動作することを確認

## 依存関係

### 外部ライブラリ（ファームウェア側）
- ESP32 BLE HIDライブラリ（例：ESP32-BLE-HID）
- HID Usage ID定義

### デバイス側実装（別途作業）
- FastRecファームウェアへのHIDサービス実装
- GPIO4、GPIO6の入力検出とHIDキー送信処理
- BLE GATTサービスとHIDサービスの同時アドバタイズ

## リスクと対策

| リスク | 対策 |
|--------|------|
| HID + BLE同時接続の安定性 | BikeClockファームウェアの実績ある構成を参考にする |
| GPIO割り込みとHID送信のタイミング | 割り込みハンドラでキュー処理を実装 |
| Android端末のHID対応 | Android 8.0+は標準対応、古い端末は除外 |
| バッテリー消費 | HID送信は最小限、BLE接続間隔を適切に設定 |

## 実装優先順位

1. **高**: ファームウェア側HIDサービス実装
2. **高**: GPIO4、GPIO6のスイッチ検出とHID送信
3. **中**: BLE + HID同時アドバタイズ
4. **低**:（アプリ側変更なし）

## 参考ファイル

### BikeClockファームウェア（HID実装の参考）
- `/Users/yoshi/dev/Arduino/btclock/bikeclock/` - BikeClockデバイスのArduinoファームウェア
  - BLE HIDサービスの実装
  - GPIOスイッチ検出とHIDキー送信処理
  - BLE GATTサービスとHIDサービスの同時アドバタイズ

### ファームウェア実装参考
- ESP32 BLE HIDライブラリ（ESP32-BLE-HID等）
- nRF52 HIDライブラリ（nRF52使用の場合）

## まとめ

**重要**: FastRecMobアプリ側の変更は一切不要。実装作業はFastRecデバイスのファームウェア側で完結します。

### 実装範囲
- ✅ **ファームウェア側**: HIDサービス実装、GPIO4/6の固定キー割り当て
- ❌ **アプリ側**: 変更不要、既存のBLE機能のみ使用

### 固定キー推奨設定
| GPIO | HIDキー | 用途 |
|------|---------|------|
| GPIO4 | 0xE9 | 音量アップ |
| GPIO6 | 0xEA | 音量ダウン |

### HID Usage ID 参考
**Consumer Page (0x0C)**
- 0xE9: Volume Up
- 0xEA: Volume Down
- 0xE2: Mute
- 0xCD: Play/Pause
- 0xB6: Next Track
- 0xB5: Previous Track

**Keyboard Page (0x01)**
- 0x28: Enter
- 0x2C: Space
- 0x4F: Right Arrow
- 0x50: Left Arrow
- 0x51: Down Arrow
- 0x52: Up Arrow
