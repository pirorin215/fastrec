import re

def decode_c_header(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # 16進数データを抽出 (0xXX,0xXX のペアを探す)
    hex_pairs = re.findall(r'0x([0-9A-Fa-f]{2}),0x([0-9A-Fa-f]{2})', content)
    
    # キャラクター数（28行で1文字分）
    num_chars = len(hex_pairs) // 28
    
    for c in range(num_chars):
        print(f"####################\n# Character {c}\n####################")
        for row in range(28):
            idx = c * 28 + row
            byte1, byte2 = hex_pairs[idx]
            # 16ビットの数値に結合
            combined = (int(byte1, 16) << 8) | int(byte2, 16)
            # 13ビット分を抽出して '#' と '0' に変換
            # ビット15から3までをスキャン
            line = ""
            for b in range(15, 2, -1):
                line += "1" if (combined & (1 << b)) else "0"
            print(line)
        print()

if __name__ == "__main__":
    # custom_font.h を読み込んで標準出力に表示
    decode_c_header('custom_font.h')
