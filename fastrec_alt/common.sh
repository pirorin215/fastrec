#!/bin/bash
# fastrec_alt 共通関数ライブラリ
# 各スクリプトから source コマンドで読み込まれます

#=============================================================================
# ESPポート設定チェック
#=============================================================================
# ESP_PORT が設定されていることをチェックし、未設定の場合は
# 利用可能なシリアルポートの候補を表示してエラー終了します
#
# 使用方法:
#   source "common.sh"
#   check_esp_port
#=============================================================================
check_esp_port() {
    if [ -z "$ESP_PORT" ]; then
        echo "エラー: ESP_PORT が設定されていません。" >&2
        echo "" >&2

        # 利用可能なシリアルポートの候補を検索
        echo "利用可能なシリアルポートの候補：" >&2
        FOUND=false
        for pattern in /dev/cu.usbmodem* /dev/cu.usbserial* /dev/cu.wchusbserial* /dev/ttyUSB* /dev/ttyACM*; do
            if [ -e "$pattern" ]; then
                # 緑色で強調表示
                printf "  \033[32m%s\033[0m\n" "$pattern" >&2
                FOUND=true
            fi
        done

        if [ "$FOUND" = false ]; then
            echo "  （見つかりませんでした）" >&2
        fi

        echo "" >&2
        echo "次の手順で設定してください：" >&2
        echo "1. cp fastrec_alt/setting.sh.example fastrec_alt/setting.sh" >&2
        echo "2. fastrec_alt/setting.sh を編集して ESP_PORT を設定" >&2
        echo "" >&2
        echo "設定例：" >&2
        echo "  ESP_PORT=\"/dev/cu.usbmodem21201\"" >&2
        exit 1
    fi
}

#=============================================================================
# その他の共通関数（将来の拡張用）
#=============================================================================
# ここに他の共通関数を追加できます
