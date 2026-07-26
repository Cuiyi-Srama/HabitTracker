📦 好习惯养成 (HabitTracker) 项目状态快照 @ 2026-07-26（v2 — 新增Hub模式）
├─ 项目信息:
│  ├─ 名称: 好习惯养成 (HabitTracker)
│  ├─ 路径: /data/local/tmp/HabitTracker/
│  ├─ 包名: com.sister.habits
│  ├─ 类型: Android原生App（Java + Gradle + Room + NanoHTTPD + ZXing）
│  └─ 当前阶段: 代码生成完成（待编译验证）

├─ 已完成:
│  ✅ 项目基础架构搭建（Gradle配置 + AndroidManifest + 主题）
│  ✅ 数据层 — 7个Room实体 + 7个DAO
│  ✅ 数据库 — AppDatabase单例 + DatabaseInitializer种子数据填充
│  ✅ 加密层 — CryptoHelper（AES-256-GCM，复用PasswordNotebook经验）
│  ✅ 同步层 — SyncManager（四层同步调度：Hub→局域网P2P→远程云端→QR码）
│  ✅ 同步层 — HubSync（家庭中枢模式，端口18081，自动发现+数据中继）
│  ✅ 同步层 — LanSync（NanoHTTPD局域网P2P同步）
│  ✅ 同步层 — DataMerger（UUID+时间戳冲突解决）
│  ✅ 同步层 — LanSyncService（后台常驻服务）
│  ✅ UI — MainActivity（双模式入口：孩子端一键进入/家长端PIN验证）
│  ✅ UI — ChildActivity（打卡+金币余额+TabLayout底部导航）
│  ✅ UI — 商城/任务/单词 Fragment
│  ✅ UI — ParentActivity（数据看板+审批兑换+发布任务+上架商品+Hub开关+经济参数）
│  ✅ Hub开关 — 家长端设置页Switch，即时启停，状态持久化
│  ✅ 词库种子 — 101个三年级单词（9大主题）
│
├─ 已下载依赖:
│  ├─ AndroidX / Material / Room / Lifecycle
│  ├─ NanoHTTPD 2.3.1 — 局域网HTTP Server + Hub Server
│  ├─ ZXing 3.5.1 — QR码
│  ├─ Gson 2.10.1
│  └─ Firebase已注释（MVP先用Hub+局域网+QR码）
│
├─ 架构决策:
│  ├─ 一个App双模式 + 同一个SQLite数据库
│  └─ 四层同步:
│      ├─ 🏠 家庭Hub（旧设备24/7 + 端口18081）
│      ├─ 📡 局域网P2P（NanoHTTPD端口扫描）
│      ├─ ☁️ 远程云端（Firebase端到端加密，可选）
│      └─ 📷 QR码（完全离线兜底）
│
├─ 待办事项:
│  ├─ [0] 编译验证
│  ├─ [1] 安装测试核心功能
│  ├─ [2] 在一台旧手机上开启Hub模式 → 验证其他设备同步
│  └─ [3] 可选：配Tailscale实现外网访问Hub
│
└─ 踩坑索引: （暂无）