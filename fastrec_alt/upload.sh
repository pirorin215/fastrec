#!/bin/bash

# デバイスパスの自動検出
PORT=$(ls /dev/cu.usbmodem* /dev/tty.usbmodem* 2>/dev/null | head -n 1)

if [ -z "$PORT" ]; then
    echo "エラー: ESP32デバイスが見つかりません"
    echo "ヒント: 環境変数 ESP_PORT でデバイスパスを指定できます"
    echo "例: ESP_PORT=/dev/cu.usbmodem21201 ./upload.sh"
    exit 1
fi

echo "使用するポート: $PORT"

arduino-cli upload --fqbn esp32:esp32:XIAO_ESP32S3:JTAGAdapter=builtin --port "$PORT" .
#arduino-cli upload --fqbn esp32:esp32:XIAO_ESP32S3 --port "$PORT" .
#arduino-cli upload --fqbn esp32:esp32:XIAO_ESP32S3:PSRAM=opi --port "$PORT" .
