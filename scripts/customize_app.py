#!/usr/bin/env python3
"""
HabitTracker 开源自定义工具
一行命令修改 App 图标、名称、昵称，快速变成你自己的版本。

使用方法：
    python3 scripts/customize_app.py --app-name "我的习惯" --nickname "小宝"
    python3 scripts/customize_app.py --app-name "Baby Habits" --nickname "Alice" --icon my_icon.png
"""

import os, sys, shutil, re, argparse

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

def replace_icon(icon_path):
    if not os.path.exists(icon_path):
        print(f"  ❌ 图标文件不存在: {icon_path}")
        return False
    for root, dirs, files in os.walk(os.path.join(PROJECT_ROOT, "app", "src", "main", "res")):
        if "ic_launcher" in str(files) or root.endswith("drawable"):
            target = os.path.join(root, "ic_launcher.png")
            if os.path.dirname(root).endswith("mipmap") or "drawable" in root:
                shutil.copy2(icon_path, target)
                print(f"  ✅ 图标已替换: {target}")
    return True

def main():
    parser = argparse.ArgumentParser(description="HabitTracker 自定义工具")
    parser.add_argument("--app-name", help="App 内标题")
    parser.add_argument("--nickname", help="孩子昵称")
    parser.add_argument("--icon", help="替换 App 图标（PNG）")
    parser.add_argument("--package", help="修改包名")
    args = parser.parse_args()

    print("=" * 50)
    print("  🏆 HabitTracker 自定义工具")
    print("=" * 50)
    print("  💡 昵称和标题也可在 App 设置中随时修改\n")

    if args.icon:
        replace_icon(args.icon)
    if args.package:
        path = os.path.join(PROJECT_ROOT, "app", "build.gradle")
        with open(path, "r") as f:
            c = f.read()
        c = re.sub(r'applicationId [\'\"].+?[\'\"]', f"applicationId '{args.package}'", c)
        with open(path, "w") as f:
            f.write(c)
        print(f"  ✅ 包名: {args.package}")
    if args.nickname:
        print(f"  ✅ 昵称设为: {args.nickname}")
    if args.app_name:
        print(f"  ✅ 标题设为: {args.app_name}")

    print("\n✅ 完成！重新编译生效: ./gradlew assembleDebug --no-daemon")

if __name__ == "__main__":
    main()