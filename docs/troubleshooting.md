# トラブルシューティング

## ファームウェア

### XIAO ESP32S3でボタンが反応しない・LCDが動かない

**症状**: ファームウェア書き込み後、ボタン操作に反応しない、LCD表示が動かない

**原因**: ボード設定が間違っている可能性があります

**解決策**:
1. **ボード設定を確認**:
   - XIAO ESP32S3を使用する場合は「Seeed XIAO ESP32-S3」を選択
   - 汎用のESP32-S3開発ボードを使用する場合は「ESP32S3 Dev Module」を選択
2. **Seeed Studioボードパッケージがインストールされているか確認**:
   - 「File」→「Preferences」→「Additional Boards Manager URLs」
   - 以下のURLが含まれているか確認：
     ```
     https://files.seeedstudio.com/arduino/package_seeeduino_boards_index.json
     ```
   - 含まれていない場合は追加して、ボードマネージャからSeeedのボードパッケージをインストール
3. Arduino IDEを再起動して、正しいボードを選択してから書き込み直してください

### ライブラリが見つからないエラー

**症状**: コンパイル時に `No such file or directory` エラー
```
fatal error: NimBLEDevice.h: No such file or directory
```

**原因**: 必要なライブラリがインストールされていない

**解決策**:
1. Arduino IDEのライブラリマネージャを開く：
   - 左側のサイドバーにあるライブラリマネージャアイコン（📚）をクリック
   - または「Tools」→「Manage Libraries...」
2. 以下のライブラリを検索してインストール：
   - `NimBLE-Arduino` by h2zero
   - `ArduinoJson` by Benoit Blanchon
   - `72x40oled_lib`
3. **注**: LittleFSはESP32ボードパッケージに含まれています
4. インストール後、Arduino IDEを再起動
5. 詳細は [EXTERNAL_LIBRARIES.md](../fastrec_alt/EXTERNAL_LIBRARIES.md) を参照

### Arduino IDE 2.xでボード選択時にエラー

**症状**: ボード選択時に以下のようなエラーが表示される
```
Uncaught Exception:
TypeError: Object has been destroyed
```

**原因**: Arduino IDE 2.xの既知の問題（Electron関連のバグ）

**解決策**:
1. Arduino IDEを完全に再起動する
2. 一度別のボードを選択してから、再度「ESP32S3 Dev Module」を選択する
3. Arduino IDEを最新版にアップデートする
4. 上記でも解決しない場合、CLIでの書き込みを検討してください：
   ```bash
   cd fastrec_alt
   ./compile.sh  # コンパイル
   ./upload.sh   # 書き込み
   ```

### 書き込み失敗

**症状**: `Failed to connect` エラー

**解決策**:
- BOOTボタンを押しながらUSB接続
- 別のUSBケーブルを試す
- ドライバーを再インストール（CP2102/CH340）

## Androidアプリ

### BLEスキャンでデバイスが見つからない

**原因**: 位置情報権限未許可

**解決策**:
1. 設定 → アプリ → FastRecMob → 権限
2. 位置情報を「許可」に設定
3. 位置情報サービス（GPS）をオンにする

### バックグラウンドで切断される

**原因**: OSによるバッテリー最適化

**解決策**:
1. 設定 → アプリ → FastRecMob → バッテリー
2. 「制限なし」に設定

### Google Cloud APIエラー

**エラーコード**: `401 Unauthorized`

**解決策**:
- サービスアカウントの秘密鍵を再生成
- APIキーの有効期限を確認

### 音声認識精度が低い

**改善策**:
1. サンプリングレートを16kHzに設定

## デバッグ方法

### シリアルログ

```bash
# fastrec_alt/
./consolelog.sh
```

### Androidログ

```bash
adb logcat | grep FastRecMob
```

## 更新履歴

- 2026-01-18: XIAO ESP32S3のボード設定が間違っている場合の対処法を追加
- 2026-01-18: Arduino IDE 2.xのライブラリ不足エラーへの対処法を追加
- 2026-01-18: Arduino IDE 2.xのボード選択エラーへの対処法を追加
- 2025-01-17: 初版
