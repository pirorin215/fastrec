#!/usr/bin/env python3
"""
Android通知アイコン用XMLファイル自動修正スクリプト

使い方:
    python fix_notification_icon.py input.xml output.xml
    
機能:
    1. 色を黒(#000000)から白(#FFFFFF)に変更
    2. サイズを24dpに変更
    3. 背景を塗りつぶす最初の大きなpathを削除
    4. viewportは元の比率を維持
"""

import sys
import re
import xml.etree.ElementTree as ET

def fix_notification_icon(input_file, output_file):
    """通知アイコン用にXMLを修正"""
    
    # XMLをパース
    tree = ET.parse(input_file)
    root = tree.getroot()
    
    # 名前空間の設定
    ns = {'android': 'http://schemas.android.com/apk/res/android'}
    ET.register_namespace('android', ns['android'])
    
    # 1. サイズを24dpに変更
    root.set('{http://schemas.android.com/apk/res/android}width', '24dp')
    root.set('{http://schemas.android.com/apk/res/android}height', '24dp')
    
    # 2. 全てのpathを取得
    paths = root.findall('.//path', ns)
    
    if len(paths) == 0:
        print("警告: pathが見つかりません")
        tree.write(output_file, encoding='utf-8', xml_declaration=True)
        return
    
    # 3. pathDataから背景部分を削除
    for path in paths:
        path_data = path.get('{http://schemas.android.com/apk/res/android}pathData', '')
        
        # 背景パターン: "M0.000 208.807 L 0.000 417.615 ..." のような全画面を塗りつぶすパス
        # これは最初の "M" で始まり、次の "M" の前まで
        if path_data.startswith('M0.000') or path_data.startswith('M0.0'):
            # 最初のMから2番目のMまでを削除
            parts = path_data.split(' M')
            if len(parts) > 1:
                # 2番目以降のパスを残す（Mを戻す）
                new_path_data = 'M' + ' M'.join(parts[1:])
                path.set('{http://schemas.android.com/apk/res/android}pathData', new_path_data)
                print(f"✓ pathDataから背景部分を削除しました")
        
        # 別パターン: 複数のpathがあり、最初が明らかに背景の場合
        elif len(paths) > 1 and path == paths[0]:
            # 最初のpathが小さい（アイコン本体より小さい）場合のみ削除
            if '0.000 0.000' in path_data and len(path_data) < 200:
                root.remove(path)
                print(f"✓ 背景pathを削除しました")
                paths = paths[1:]
                break
    
    # 5. 全てのpathの色を白に変更
    for path in root.findall('.//path', ns):
        fill_color = path.get('{http://schemas.android.com/apk/res/android}fillColor')
        if fill_color:
            # 黒系の色を白に変更
            if fill_color.lower() in ['#000000', '#000', '#040404', '#0c0c0c']:
                path.set('{http://schemas.android.com/apk/res/android}fillColor', '#FFFFFF')
                print(f"✓ 色を {fill_color} → #FFFFFF に変更")
            # グレー系の色も白に変更（通知アイコンは単色のみ）
            elif fill_color.lower().startswith('#') and fill_color.lower() not in ['#ffffff', '#fff']:
                path.set('{http://schemas.android.com/apk/res/android}fillColor', '#FFFFFF')
                print(f"✓ 色を {fill_color} → #FFFFFF に変更（グレー系削除）")
    
    # 6. 保存
    tree.write(output_file, encoding='utf-8', xml_declaration=True)
    print(f"\n✓ 修正完了: {output_file}")
    print(f"  - サイズ: 24dp × 24dp")
    print(f"  - 色: 白 (#FFFFFF)")
    print(f"  - 背景: 透明")

def main():
    if len(sys.argv) != 3:
        print("使い方: python fix_notification_icon.py <入力ファイル> <出力ファイル>")
        print("\n例:")
        print("  python fix_notification_icon.py image2vector.xml ic_notification_icon.xml")
        sys.exit(1)
    
    input_file = sys.argv[1]
    output_file = sys.argv[2]
    
    try:
        fix_notification_icon(input_file, output_file)
    except FileNotFoundError:
        print(f"エラー: ファイルが見つかりません: {input_file}")
        sys.exit(1)
    except ET.ParseError as e:
        print(f"エラー: XMLの解析に失敗しました: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"エラー: {e}")
        sys.exit(1)

if __name__ == '__main__':
    main()
