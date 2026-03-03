# fastrec_alt プロジェクト - Claude Codeへの指示

## 自動ビルドルール

**重要:** このプロジェクトのコードを変更した場合、**必ずビルドを実行してください**。

### 手順

1. コードを変更する
2. **即座にビルドを実行**: `arduino-cli compile --fqbn esp32:esp32:XIAO_ESP32S3:JTAGAdapter=builtin fastrec_alt.ino`
3. ビルド結果をユーザーに報告

### 例

**ユーザー:** 「関数を修正して」

**Claudeの応答:**
```
✅ 変更完了しました！

ビルドを実行します...
[ビルド結果を表示]
```

## ビルドコマンド

```bash
arduino-cli compile --fqbn esp32:esp32:XIAO_ESP32S3:JTAGAdapter=builtin fastrec_alt.ino
```

## プラットフォーム情報

- **種類**: custom
- **プロジェクト名**: fastrec_alt
- **設定日**: 2026-03-03
