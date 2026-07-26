#!/usr/bin/env python3
"""
家庭中枢服务器 (PC版) — HabitTracker Family Hub Server
=======================================================
在电脑上运行此脚本，效果等同于在旧手机上开启「家庭中枢模式」。
所有设备在同一局域网下自动发现并同步数据。

使用方法：
    python3 HubServer.py

默认端口：18081
数据存储：hub_data.json（自动创建）

可选参数：
    python3 HubServer.py --port 19000          # 自定义端口
    python3 HubServer.py --data ./my_data.json  # 自定义数据文件

注意：Android 设备连接时，确保电脑防火墙允许 18081 端口入站。
"""

import json
import os
import sys
import uuid
import shutil
from datetime import datetime
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

# ==================== 配置 ====================
DEFAULT_PORT = 18081
DATA_FILE = "hub_data.json"

# ==================== 数据存储 ====================

class HubDataStore:
    """简单的JSON文件存储，模拟Android端Room数据库"""
    
    def __init__(self, filepath):
        self.filepath = filepath
        self.data = {
            "device_id": f"pc_hub_{uuid.uuid4().hex[:8]}",
            "check_ins": [],
            "coin_transactions": [],
            "tasks": [],
            "shop_items": [],
            "redemptions": [],
            "vocabulary": [],
            "economy_config": None,
            "last_updated": 0
        }
        self._load()
    
    def _load(self):
        if os.path.exists(self.filepath):
            try:
                with open(self.filepath, 'r', encoding='utf-8') as f:
                    loaded = json.load(f)
                    self.data.update(loaded)
                print(f"📂 已加载数据: {self.filepath}")
            except Exception as e:
                print(f"⚠️ 数据文件读取失败，使用新数据: {e}")
    
    def _save(self):
        self.data["last_updated"] = int(datetime.now().timestamp() * 1000)
        # 先写临时文件再覆盖，防止写入时崩溃损坏数据
        tmp = self.filepath + ".tmp"
        with open(tmp, 'w', encoding='utf-8') as f:
            json.dump(self.data, f, ensure_ascii=False, indent=2)
        shutil.move(tmp, self.filepath)
    
    def get_snapshot(self):
        """返回当前全量数据快照"""
        return {
            "serverTime": int(datetime.now().timestamp() * 1000),
            "hubDeviceId": self.data["device_id"],
            "checkIns": self.data["check_ins"],
            "coinTransactions": self.data["coin_transactions"],
            "tasks": self.data["tasks"],
            "shopItems": self.data["shop_items"],
            "redemptions": self.data["redemptions"],
            "vocabulary": self.data["vocabulary"],
            "economyConfig": self.data["economy_config"],
            "lastUpdated": self.data["last_updated"]
        }
    
    def merge_push_data(self, payload):
        """合并客户端推送的数据，基于 syncTimestamp 最新优先"""
        now = int(datetime.now().timestamp() * 1000)
        updated = 0
        
        for entity_type, merge_key, items in [
            ("checkIns", "id", payload.get("checkIns", [])),
            ("coinTransactions", "id", payload.get("coinTransactions", [])),
            ("tasks", "id", payload.get("tasks", [])),
            ("shopItems", "id", payload.get("shopItems", [])),
            ("redemptions", "id", payload.get("redemptions", [])),
        ]:
            existing = {item[merge_key]: item for item in self.data.get(entity_type, [])}
            for item in items:
                item_id = item.get(merge_key)
                if item_id:
                    # 时间戳最新优先
                    old_item = existing.get(item_id)
                    if old_item is None or item.get("syncTimestamp", 0) >= old_item.get("syncTimestamp", 0):
                        existing[item_id] = item
                        updated += 1
            self.data[entity_type] = list(existing.values())
        
        # 合并经济配置
        eco = payload.get("economyConfig")
        if eco and eco.get("syncTimestamp", 0) >= (self.data.get("economy_config") or {}).get("syncTimestamp", 0):
            self.data["economy_config"] = eco
            updated += 1
        
        self._save()
        return updated
    
    def get_incremental(self, since):
        """获取 since 时间戳之后的增量数据"""
        snapshot = self.get_snapshot()
        # 简化：直接返回全量，客户端自己按 syncTimestamp 过滤
        # 小数据量场景足够用
        return snapshot
    
    def get_peek(self):
        """健康检查 + 基础信息"""
        return {
            "status": "online",
            "deviceId": self.data["device_id"],
            "deviceName": f"PC-Hub-{os.uname().nodename}",
            "totalRecords": sum(len(self.data[k]) for k in 
                ["check_ins", "coin_transactions", "tasks", "shop_items", "redemptions"]),
            "uptime": int(datetime.now().timestamp() * 1000) - self.data["last_updated"]
        }


# ==================== HTTP 请求处理器 ====================

store = HubDataStore(DATA_FILE)

class HubRequestHandler(BaseHTTPRequestHandler):
    
    def _send_json(self, data, status=200):
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()
        self.wfile.write(json.dumps(data, ensure_ascii=False).encode("utf-8"))
    
    def _read_body(self):
        length = int(self.headers.get("Content-Length", 0))
        if length > 0:
            return json.loads(self.rfile.read(length))
        return {}
    
    def log_message(self, format, *args):
        print(f"📝 {datetime.now().strftime('%H:%M:%S')} {args[0]} {args[1]} {args[2]}")
    
    # ---- CORS 预检 ----
    def do_OPTIONS(self):
        self._send_json({"ok": True})
    
    # ---- GET /hub/peek — 健康检查 ----
    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path
        params = parse_qs(parsed.query)
        
        if path == "/hub/peek":
            self._send_json(store.get_peek())
        
        elif path == "/hub/discover":
            self._send_json(store.get_peek())
        
        elif path == "/hub/pull":
            since_str = params.get("since", [None])[0]
            since = int(since_str) if since_str else 0
            data = store.get_incremental(since)
            data["pulledAt"] = int(datetime.now().timestamp() * 1000)
            self._send_json(data)
        
        elif path == "/hub/stats":
            peek = store.get_peek()
            snapshot = store.get_snapshot()
            self._send_json({
                **peek,
                "checkIns": len(snapshot.get("checkIns", [])),
                "redemptions": len(snapshot.get("redemptions", [])),
            })
        
        elif path == "/":
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            html = (
                "<html><head><meta charset='utf-8'><title>🏠 HabitTracker Hub</title>"
                "<style>body{font-family:sans-serif;max-width:600px;margin:50px auto;"
                "padding:20px;background:#f5f5f5;border-radius:12px}"
                "h1{color:#FF6B9D}.info{background:white;padding:16px;border-radius:8px;"
                "margin:12px 0}</style></head><body>"
                f"<h1>🏠 HabitTracker 家庭中枢</h1>"
                f"<div class='info'>✅ 服务器运行中<br>"
                f"📊 数据记录数: {store.get_peek()['totalRecords']}<br>"
                f"🆔 设备ID: {store.data['device_id']}<br>"
                f"📁 数据文件: {os.path.abspath(DATA_FILE)}</div>"
                "<p>Android设备在同一WiFi下会自动发现此服务器。</p>"
                "</body></html>"
            )
            self.wfile.write(html.encode("utf-8"))
        
        else:
            self._send_json({"error": "not_found", "path": path}, 404)
    
    # ---- POST /hub/sync — 数据同步上报 ----
    def do_POST(self):
        parsed = urlparse(self.path)
        path = parsed.path
        
        if path == "/hub/sync":
            try:
                payload = self._read_body()
                count = store.merge_push_data(payload)
                snapshot = store.get_snapshot()
                self._send_json({
                    "status": "ok",
                    "serverTime": int(datetime.now().timestamp() * 1000),
                    "updated": count,
                    "hubDeviceId": store.data["device_id"],
                    "snapshot": snapshot
                })
                print(f"🔄 同步成功: 更新了 {count} 条记录")
            except Exception as e:
                self._send_json({"error": str(e)}, 500)
        
        elif path == "/hub/reset":
            # 重置所有数据（危险操作！）
            store.data = {
                "device_id": store.data["device_id"],
                "check_ins": [],
                "coin_transactions": [],
                "tasks": [],
                "shop_items": [],
                "redemptions": [],
                "vocabulary": [],
                "economy_config": None,
                "last_updated": 0
            }
            store._save()
            self._send_json({"status": "reset_ok"})
            print("🗑️ 数据已重置")
        
        else:
            self._send_json({"error": "not_found"}, 404)


# ==================== 启动 ====================

if __name__ == "__main__":
    port = DEFAULT_PORT
    if len(sys.argv) > 1 and sys.argv[1] == "--port":
        port = int(sys.argv[2])
    if len(sys.argv) > 3 and sys.argv[3] == "--data":
        DATA_FILE = sys.argv[4]
        store = HubDataStore(DATA_FILE)
    
    server = HTTPServer(("0.0.0.0", port), HubRequestHandler)
    
    print("=" * 50)
    print("  🏠 HabitTracker 家庭中枢服务器")
    print("=" * 50)
    print(f"  📡 端口: {port}")
    print(f"  📁 数据: {os.path.abspath(DATA_FILE)}")
    print(f"  🆔 设备: {store.data['device_id']}")
    print(f"  📊 记录: {store.get_peek()['totalRecords']} 条")
    print("-" * 50)
    print(f"  🌐 HTTP 服务已启动...")
    print(f"  📱 在浏览器打开: http://localhost:{port}")
    print(f"  🔍 健康检查: http://localhost:{port}/hub/peek")
    print("=" * 50)
    
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n👋 服务器已停止")
        server.server_close()
