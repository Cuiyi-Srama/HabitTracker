📦 好习惯养成 (HabitTracker) 项目状态快照 @ 2026-08-01（v2.2.0 — WebDAV远程同步完成）
├─ 项目信息:
│  ├─ 名称: 好习惯养成 (HabitTracker)
│  ├─ 包名: com.sister.habits
│  ├─ 类型: Android原生App（Java 17 + Gradle + Room v13 + NanoHTTPD + ZXing + Glide）
│  ├─ 当前版本: v2.2.0 / versionCode 36
│  └─ 当前阶段: ✅ 六批计划全部完成（H/I已实现，J待用户双设备实测）
│
├─ 已完成核心功能:
│  ✅ 打卡/单词/商城/任务/洗衣/作业/抽奖 全闭环
│  ✅ 抽奖系统：固定积分+百分比概率+中奖审批闭环+老虎机动效
│  ✅ 商品链接导入（淘宝/京东/拼多多，1元=10积分，自动解析标题/价格/封面）
│  ✅ 背单词「我不会」：详情弹窗+TTS双语朗读+自动加入复习
│  ✅ 备份：SAF自定义导出导入+位置命名标注+AES-256-GCM加密
│  ✅ 同步：Hub中枢/局域网P2P/WebDAV云端(RemoteSync)/QR码 四层
│  ✅ Key绑定跨设备同步（随WebDAV快照自动恢复）
│  ✅ 经济参数/假期折扣/加速器/审批中心批量操作
│
├─ 数据库:
│  ├─ Room Database v13（lottery_prizes含prizeType/pointsValue/shopItemId）
│  └─ 固定调试签名，versionCode连续升级，更新不丢数据
│
├─ 文档状态:
│  ├─ README.md → v2.2.0 ✅
│  ├─ CHANGELOG.md → v1.0.0~v2.2.0 完整历史 ✅
│  └─ 本文件 → 2026-08-01 ✅
│
├─ 待办:
│  ├─ [J] 双设备 WebDAV 同步实测（需用户提供坚果云账号+第二台设备）
│  └─ [可选] Tailscale 外网访问 Hub
│
└─ 踩坑索引:
   ├─ Room迁移ALTER带DEFAULT会schema校验失败 → 重建表+COALESCE
   ├─ java.util.Base64仅API26+ → android.util.Base64
   ├─ 模拟器通知栏下拉挡安装确认 → input swipe收起再pm install
   └─ 版本号不更新→INSTALL_FAILED_ABORTED→禁止pm uninstall

---
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