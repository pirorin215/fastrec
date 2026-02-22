# External Libraries Used in fastrec_alt

## ESP32コアバージョン（最重要）

**Arduino core for ESP32 のバージョン 3.3.7 では動作しません。**

| コンポーネント        | バージョン |
| :------------------- | :--------- |
| Arduino core for ESP32 | **3.3.4**  |

**重要**: NimBLE-ArduinoライブラリがESP32コア 3.3.7環境でコアダンプを発生する問題が確認されています。3.3.7にアップグレードすると、BLE通信開始時にNimBLEがクラッシュし、デバイスが再起動を繰り返す状態になります。

バージョン確認方法は後述の「バージョン確認方法」を参照してください。

## Arduinoライブラリ（外部インストールが必要）

ESP32コアに含まれるライブラリ（FS、LittleFS、Wire、Networking等）は除外しています。

| Library Name        | Version | 用途            |
| :------------------ | :------ | :-------------- |
| NimBLE-Arduino      | 2.3.6   | BLE通信         |
| ArduinoJson         | 7.4.2   | JSON処理        |
| 72x40oled_lib       | 1.0.1   | OLED表示        |

This list can be used by Jules to understand the dependencies of the project.

## バージョン確認方法

以下のコマンドで現在の環境のバージョンを確認できます。

### ESP32コアの確認

```bash
arduino-cli core list
```

**期待される出力:**
```
ID          インストール済 Latest Name
arduino:avr 1.8.7   1.8.7  Arduino AVR Boards
esp32:esp32 3.3.4   3.3.7  esp32
```

**重要**: `esp32:esp32` のインストール済バージョンが **3.3.4** であることを確認してください。3.3.7はNimBLEと互換性がありません。

### ライブラリの確認

```bash
arduino-cli lib list
```

**期待される出力:**
```
Name           インストール済  Available     Location Description
72x40oled_lib  1.0.1    -             user     -
ArduinoJson    7.4.2    -             user     -
NimBLE-Arduino 2.3.6    2.3.7         user     Bluetooth low energy (BLE) library fo...
```

#### Arduino IDE 2.xの場合

1. `ツール` > `ボード` > `ボードマネージャ` を開く
2. "esp32"で検索
3. `Arduino core for ESP32` のバージョンが3.3.4であることを確認
4. 3.3.7がインストールされている場合は、3.3.4へダウングレードしてください

## Python Libraries

| Library Name | Version   | Source |
| :----------- | :-------- | :----- |
| bleak        | (Unknown) | PyPI   |

