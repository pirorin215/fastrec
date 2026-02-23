#!/bin/bash

# 共通関数と設定ファイルを読み込み
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/common.sh" ]; then
    source "$SCRIPT_DIR/common.sh"
fi
if [ -f "$SCRIPT_DIR/setting.sh" ]; then
    source "$SCRIPT_DIR/setting.sh"
fi

MAX_RETRIES=10
RETRY_DELAY=3

# ESPポートチェック
check_esp_port

PORT="$ESP_PORT"
echo "使用するポート: $PORT" >&2

# アップロード（リトライ付き）
attempt=1
while [ $attempt -le $MAX_RETRIES ]; do
    if arduino-cli upload --fqbn esp32:esp32:XIAO_ESP32S3:JTAGAdapter=builtin --port "$PORT" .; then
        echo "アップロード成功!" >&2
        exit 0
    fi

    if [ $attempt -lt $MAX_RETRIES ]; then
        echo "アップロード失敗 ($attempt/$MAX_RETRIES)、${RETRY_DELAY}秒待機してリトライします..." >&2
        sleep $RETRY_DELAY
    fi

    attempt=$((attempt + 1))
done

echo "エラー: アップロードに失敗しました" >&2
exit 1
#arduino-cli upload --fqbn esp32:esp32:XIAO_ESP32S3 --port "$PORT" .
#arduino-cli upload --fqbn esp32:esp32:XIAO_ESP32S3:PSRAM=opi --port "$PORT" .
