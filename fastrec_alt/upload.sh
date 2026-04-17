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

# コマンドライン引数の処理
ERASE_FLASH=false
if [ "$1" = "--erase" ] || [ "$1" = "-e" ]; then
    ERASE_FLASH=true
    echo "========================================" >&2
    echo "フラッシュ完全消去モード" >&2
    echo "Arduino IDEと同等の処理を実行します" >&2
    echo "========================================" >&2
fi

# ESPポートチェック
check_esp_port

PORT="$ESP_PORT"
echo "使用するポート: $PORT" >&2

# Arduino IDEが使用するesptoolのパスを検索
ESPTOOL_PATHS=(
    "$HOME/Library/Arduino15/packages/esp32/tools/esptool_py/*/esptool"
    "$HOME/.arduino15/packages/esp32/tools/esptool_py/*/esptool"
    "/opt/homebrew/opt/esptool/esptool.py"
)

ESPTOOL=""
for path in "${ESPTOOL_PATHS[@]}"; do
    for expanded in $path; do
        if [ -f "$expanded" ]; then
            ESPTOOL="$expanded"
            break 2
        fi
    done
done

if [ "$ERASE_FLASH" = true ] && [ -z "$ESPTOOL" ]; then
    echo "エラー: esptoolが見つかりません" >&2
    echo "Arduino IDEをインストールしてください: https://www.arduino.cc/en/software" >&2
    exit 1
fi

if [ -n "$ESPTOOL" ]; then
    echo "esptool: $ESPTOOL" >&2
fi

# フラッシュ完全消去（Arduino IDEと同等）
if [ "$ERASE_FLASH" = true ]; then
    echo "Step 1: フラッシュ完全消去..." >&2

    ESPTOOL_OUTPUT=$("$ESPTOOL" --chip esp32s3 --port "$PORT" --baud 921600 erase-flash 2>&1)
    ESPTOOL_EXIT_CODE=$?

    if [ $ESPTOOL_EXIT_CODE -ne 0 ]; then
        # エラーメッセージを解析してわかりやすいメッセージを表示
        if echo "$ESPTOOL_OUTPUT" | grep -q "Could not open.*port.*busy\|port.*doesn't exist\|No such file or directory"; then
            echo "エラー: ポート $PORT が見つかりません" >&2
            echo "" >&2
            echo "考えられる原因:" >&2
            echo "  1. USBケーブルが接続されていません" >&2
            echo "  2. ESP32デバイスの電源が入っていません" >&2
            echo "  3. ポート名が異なる可能性があります" >&2
            echo "" >&2
            echo "確認方法:" >&2
            echo "  - USBケーブルを接続してください" >&2
            echo "  - 以下のコマンドで使用可能なポートを確認できます:" >&2
            echo "    ls /dev/cu.usbmodem* /dev/cu.usbserial* 2>/dev/null" >&2
            exit 1
        elif echo "$ESPTOOL_OUTPUT" | grep -q "A fatal error occurred"; then
            echo "エラー: esptoolで致命的なエラーが発生しました" >&2
            echo "$ESPTOOL_OUTPUT" >&2
            exit 1
        else
            echo "エラー: フラッシュ消去に失敗しました" >&2
            echo "$ESPTOOL_OUTPUT" >&2
            exit 1
        fi
    fi

    echo "フラッシュ消去完了" >&2
    sleep 2
fi

# アップロード（リトライ付き）
attempt=1
while [ $attempt -le $MAX_RETRIES ]; do
    if arduino-cli upload --fqbn esp32:esp32:XIAO_ESP32S3:JTAGAdapter=builtin --port "$PORT" .; then
        echo "アップロード成功!" >&2
        if [ "$ERASE_FLASH" = true ]; then
            echo "========================================" >&2
            echo "フラッシュ完全消去+書き込み完了！" >&2
            echo "========================================" >&2
        fi
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
