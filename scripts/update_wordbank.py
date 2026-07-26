#!/usr/bin/env python3
"""
词库更新工具 — 从在线源同步 wordbank.json
用法:
  python3 scripts/update_wordbank.py                    # 从GitHub拉取最新词库
  python3 scripts/update_wordbank.py --push             # 拉取后如果有修改则推送
  python3 scripts/update_wordbank.py --source URL       # 从自定义源拉取
  
环境变量:
  GITHUB_TOKEN  - GitHub个人访问令牌（--push时需要）
"""
import json, os, sys, urllib.request, tempfile, shutil

REPO = "Cuiyi-Srama/HabitTracker"
BRANCH = "main"
DEFAULT_SOURCE = f"https://raw.githubusercontent.com/{REPO}/{BRANCH}/app/src/main/assets/wordbank.json"
LOCAL_PATH = "app/src/main/assets/wordbank.json"

def fetch_wordbank(source_url):
    print(f"📥 从 {source_url} 拉取词库...")
    req = urllib.request.Request(source_url)
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read())
    print(f"✅ 获取到 {len(data)} 个单词")
    return data

def update_local(data, local_path):
    os.makedirs(os.path.dirname(local_path), exist_ok=True)
    tmp = local_path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    shutil.move(tmp, local_path)
    print(f"💾 已保存到 {local_path}")

def push_to_github(data, token):
    url = f"https://api.github.com/repos/{REPO}/contents/{LOCAL_PATH}"
    b64 = base64.b64encode(json.dumps(data, ensure_ascii=False, indent=2).encode()).decode()
    req = urllib.request.Request(url, headers={"Authorization": f"token {token}"})
    sha = None
    try:
        sha = json.loads(urllib.request.urlopen(req).read()).get("sha")
    except: pass
    payload = {"message": "chore: auto-update wordbank", "content": b64, "branch": BRANCH}
    if sha: payload["sha"] = sha
    req = urllib.request.Request(url, data=json.dumps(payload).encode(),
        headers={"Authorization": f"token {token}", "Content-Type": "application/json"})
    req.get_method = lambda: "PUT"
    urllib.request.urlopen(req)
    print(f"✅ 已推送到 GitHub")

if __name__ == "__main__":
    import base64
    
    source = DEFAULT_SOURCE
    do_push = "--push" in sys.argv
    
    for i, arg in enumerate(sys.argv):
        if arg == "--source" and i + 1 < len(sys.argv):
            source = sys.argv[i + 1]
    
    data = fetch_wordbank(source)
    update_local(data, LOCAL_PATH)
    
    if do_push:
        token = os.environ.get("GITHUB_TOKEN")
        if not token:
            print("❌ 需要设置 GITHUB_TOKEN 环境变量")
            sys.exit(1)
        push_to_github(data, token)
    else:
        print("💡 使用 --push 可自动推送到GitHub")
