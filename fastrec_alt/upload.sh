#!/bin/bash

MAX_RETRIES=10
RETRY_DELAY=3

# デバイスパスの自動検出（リトライ付き）
find_port() {
    local attempt=1
    local port

    while [ $attempt -le $MAX_RETRIES ]; do
        port=$(ls /dev/cu.usbmodem* /dev/tty.usbmodem* 2>/dev/null | head -n 1)

        if [ -n "$port" ]; then
            echo "$port"
            return 0
        fi

        if [ $attempt -lt $MAX_RETRIES ]; then
            echo "デバイスが見つかりません ($attempt/$MAX_RETRIES)、${RETRY_DELAY}秒待機してリトライします..." >&2
            sleep $RETRY_DELAY
        fi

        attempt=$((attempt + 1))
    done

    return 1
}

PORT=$(find_port)

if [ -z "$PORT" ]; then
    echo "エラー: ESP32デバイスが見つかりません" >&2
    echo "ヒント: 環境変数 ESP_PORT でデバイスパスを指定できます" >&2
    echo "例: ESP_PORT=/dev/cu.usbmodem21201 ./upload.sh" >&2
    exit 1
fi

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
