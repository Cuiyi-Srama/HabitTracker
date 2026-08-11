# 🎯 好习惯养成 (HabitTracker)

> **一个专为亲子设计的习惯养成与英语学习 Android 应用**
>
> Java 原生开发 · Room 本地数据库 · Material Design 3 · 离线可用

[![构建状态](https://github.com/Cuiyi-Srama/HabitTracker/actions/workflows/build.yml/badge.svg)](https://github.com/Cuiyi-Srama/HabitTracker/actions)
[![版本](https://img.shields.io/badge/版本-v3.0.34-blue)](https://github.com/Cuiyi-Srama/HabitTracker/releases)
[![许可](https://img.shields.io/badge/许可-MIT-green)](LICENSE)

---

## 📖 目录

- [项目简介](#user-content-项目简介)
- [核心功能](#user-content-核心功能)
- [系统架构](#user-content-系统架构)
- [快速开始](#user-content-快速开始)
- [项目结构](#user-content-项目结构)
- [经济系统](#user-content-经济系统)
- [备份与恢复](#user-content-备份与恢复)
- [开发指南](#user-content-开发指南)
- [常见问题](#user-content-常见问题)
- [更新日志](#user-content-更新日志)

---

## 项目简介

好习惯养成是一套完整的亲子互动系统，将**习惯打卡**、**英语单词学习**、**积分奖励**、**商城兑换**有机融合。

### 设计理念

| 理念 | 说明 |
|:-----|:-----|
| 🎮 **游戏化** | 完成任务 → 获得积分 → 兑换奖励，形成正向循环 |
| 👨‍👩‍👧 **亲子协作** | 孩子完成任务、家长审批确认，增强互动 |
| 📱 **离线优先** | 所有数据本地存储，无需网络即可使用 |
| 🔐 **隐私安全** | AES-256-GCM 加密备份，敏感操作需家长验证 |

### 适用场景

- 小学生每日习惯养成（刷牙、阅读、练字等）
- 英语单词分级学习（小学→初中→高中词库）
- 家长设置积分规则，孩子通过努力兑换心愿

---

## 核心功能

### 👶 孩子端

| 功能 | 说明 |
|:-----|:-----|
| ✅ **每日打卡** | 五种任务类型：一次性、每日、每周、每月、限时 |
| 📖 **单词学习** | 分级词库 + 复习模式 +「😅 我不会」详情弹窗（朗读/音标/自动复习） |
| 🎰 **抽奖机** | 老虎机滚动动效，固定积分抽奖，概率出奖（含未中奖） |
| 🏪 **积分商城** | 浏览商品、提交兑换申请、查看心愿单 |
| 📊 **积分概览** | 今日预计积分（已确认 + 待审批）、加速器加成 |
| 🎂 **生日礼包** | 生日当天自动获得额外积分奖励 |

### 👨‍👩‍👧 家长端

| 功能 | 说明 |
|:-----|:-----|
| 📊 **数据总览** | 近期打卡统计、积分变化趋势 |
| ✅ **审批中心** | 批量确认/拒绝：任务完成 + 积分申请 + 兑换申请；全部审批后汇总净积分变化 |
| 🎰 **抽奖管理** | 设价格、百分比概率（≤100%）、从商城添加奖品、完整编辑 |
| 📚 **学习管理** | 词库年级切换、每日学习限额、学习奖励参数 |
| 📝 **作业管理** | 假期配置（日期/周末开关）、审核五档（完成/未完成/AI作弊/免检/补交）、赦免配置（外出/旅行免检）、⚡快速赦免今天 |
| 💰 **积分账单** | 积分历史明细（近 100 条流水 + 收入/支出/余额汇总） |
| 🏪 **商城管理** | 添加/编辑商品、🔗 淘宝/京东/拼多多链接自动导入（标题/价格/封面图）、上架/下架/删除原地刷新 |
| 📋 **任务管理** | 创建任务、模板库（20+ 预设多选批量添加）、洗衣类型 |
| ⚙️ **系统设置** | 个人信息、经济参数、假期与折扣、加速器、备份（SAF自定义位置）、PIN管理 |
| 📡 **设备同步** | Hub中枢 / 局域网P2P / ☁️ WebDAV远程同步 / QR配对 / Key绑定 |

### 🚀 加速器系统

| 加速器 | 触发条件 | 奖励 |
|:-----|:-----|:-----|
| 🔥 连续打卡（7天） | 同一任务连续 7 天完成 | +50 积分 |
| 📅 周勤勉奖 | 本周完成 ≥ 20 项任务 | +30 积分 |
| 🗓️ 月度全勤 | 当月每天 ≥ 3 项任务 | +100 积分 |
| 🎂 小生日礼包 | 生日当天 | +100 积分 |
| 🎄 节日礼包 | 特定节日 | +50 积分 |
| ✨ 双倍积分日 | 家长手动开启 | 当日积分 ×2 |

---

### 🔄 多设备同步
| 层级 | 方式 | 适用场景 |
|:-----|:-----|:-----|
| 🏠 **Hub 中枢** | NanoHTTPD 局域网服务器（端口18081），32线程并行发现 | 家中固定设备常驻同步，全量下发 |
| 📡 **局域网 P2P** | 端口扫描直连（端口18080），空设备自动全量引导 | 同 WiFi 下两台设备 |
| ☁️ **WebDAV 云端** | 坚果云/Nextcloud 加密快照（AES-256-GCM） | 不同网络/跨地域同步（含Key绑定） |
| 📷 **QR 码** | 增量数据二维码（HSYNCv1） | 完全离线兜底 |

> **同步模型**：局域网走增量 JSON 交换（未同步记录），新设备加入时自动全量引导；WebDAV 走全量加密快照（下载合并 + 上传覆盖）。数据合并采用**叠加模式**（只增不删、天然键去重），家长 Key 绑定关系随 WebDAV 同步跨设备复制。绑定新设备后自动触发全同步。

### 🔐 数据安全
- AES-256-GCM 加密备份与云端快照
- 家长 Key 绑定（HABIT-P/C-XXXX）随云端同步自动跨设备恢复
- 敏感操作需家长 PIN 验证

---
## 系统架构

```
┌──────────────────────────────────────────┐
│                 用户界面层                 │
│  ParentActivity    ChildActivity          │
│  ├─ Dashboard      ├─ HomeFragment        │
│  ├─ ApprovalCenter ├─ TaskFragment         │
│  ├─ ShopManager    ├─ ShopFragment         │
│  └─ SystemSettings └─ WordFragment         │
├──────────────────────────────────────────┤
│                 业务逻辑层                 │
│  EarningService   AcceleratorService      │
│  BackupExportHelper   MenuHelper          │
│  ProfileManager   SoundHelper             │
│  CryptoHelper     QRCodeHelper            │
├──────────────────────────────────────────┤
│                 数据持久层                 │
│  Room Database (v14)                       │
│  ├─ tasks          ├─ coin_earnings       │
│  ├─ shop_items     ├─ economy_config      │
│  ├─ word_records   ├─ checkin_records     │
│  ├─ gate_config    ├─ daily_gates         │
│  └─ ... (共 17 张表)                       │
│  SharedPreferences (Profile)              │
│  文件存储 (商品图片 / 头像 / 备份)          │
└──────────────────────────────────────────┘
```

### 技术栈

| 类别 | 技术选型 | 版本 |
|:-----|:-----|:-----|
| 语言 | Java | 17 |
| 构建 | Gradle + AGP | 8.0.1 |
| 数据库 | Room (SQLite) | 2.5.2 |
| UI | Material Design 3 | 1.9.0 |
| 加密 | AES-256-GCM | javax.crypto |
| 二维码 | ZXing | 3.5.1 |
| HTTP | NanoHTTPD (中枢) | 2.3.1 |
| CI/CD | GitHub Actions | ubuntu-latest |

### 架构决策记录

| 决策 | 原因 | 日期 |
|:-----|:-----|:-----|
| 使用 Room 而非直接 SQLite | 编译时 SQL 校验、LiveData 集成 | 2026-07 |
| 主线程数据库操作 | 简化代码，数据量小 (<10MB) | 2026-07 |
| MenuHelper 路由表替代 switch/case | 消除索引脆弱性，v1.3.1 引入 | 2026-07-29 |
| ZIP 格式备份替代纯 JSON | 支持图片文件打包 | 2026-07-29 |
| 固定调试签名 | 更新不丢数据库 | 2026-07 |
| 单元测试接入 CI（JUnit4+Mockito） | 核心积分/合并逻辑防回归 | 2026-08-07 |
| 对话框禁用 setMessage+setItems 组合 | vivo ROM 列表不渲染，一律自定义布局 | 2026-08-09 |

---

## 快速开始

### 环境要求

| 工具 | 最低版本 |
|:-----|:-----|
| Android Studio | Hedgehog (2023.1.1) |
| Gradle | 8.0 |
| JDK | 17 |
| Android SDK | API 34 |
| 设备 | Android 8.0+ (API 26) |

### 克隆与构建

```bash
# 克隆仓库
git clone https://github.com/Cuiyi-Srama/HabitTracker.git
cd HabitTracker

# 构建 Debug APK（命令行）
./gradlew assembleDebug

# 或使用 Android Studio
# File → Open → 选择项目目录 → Run
```

### 安装

```bash
# 方式一：USB 安装
adb install app/build/outputs/apk/debug/app-debug.apk

# 方式二：直接下载
# 前往 Releases 页面下载最新 APK
```

### 首次启动

1. 首次打开会显示 3 页新手引导
2. 选择角色模式：**家长端** 或 **孩子端**
3. 家长端需设置安全密码（用于敏感操作验证）
4. 在系统设置中配置经济参数和加速器规则

---

## 项目结构

```
HabitTracker/
├── .github/workflows/build.yml    # CI/CD 自动编译
├── app/
│   ├── build.gradle               # 应用构建配置
│   ├── debug.keystore             # 固定调试签名（更新不丢数据）
│   └── src/main/
│       ├── AndroidManifest.xml    # 权限与组件声明
│       ├── java/com/sister/habits/
│       │   ├── child/             # 孩子端 Activity + Fragment
│       │   │   ├── ChildActivity.java
│       │   │   ├── HomeFragment.java
│       │   │   ├── TaskFragment.java
│       │   │   ├── ShopFragment.java
│       │   │   └── WordFragment.java
│       │   ├── parent/            # 家长端 Activity + 所有管理界面
│       │   │   └── ParentActivity.java
│       │   ├── data/              # Room 数据库 + DAO + 实体
│       │   │   ├── AppDatabase.java
│       │   │   ├── dao/           # 数据访问对象 (12个)
│       │   │   └── models/        # 实体模型 (12个)
│       │   ├── sync/              # 业务服务
│       │   │   ├── EarningService.java
│       │   │   └── AcceleratorService.java
│       │   └── utils/             # 工具类
│       │       ├── BackupExportHelper.java  # 备份导出/导入
│       │       ├── MenuHelper.java          # 路由表（v1.3.1 新增）
│       │       ├── ProfileManager.java
│       │       ├── CryptoHelper.java
│       │       ├── SoundHelper.java
│       │       └── QRCodeHelper.java
│       └── res/                   # 资源文件
│           ├── layout/            # 布局 XML
│           ├── drawable/          # 图标与图片
│           ├── values/            # 字符串、颜色、主题
│           └── raw/               # 词库 JSON 文件
├── scripts/update_wordbank.py     # 词库更新脚本
├── HubServer.py                   # Windows 中枢服务
├── HubServer.spec                 # PyInstaller 打包配置
├── .env.example                   # 环境变量示例
├── ECONOMY_SYSTEM.md              # 经济系统设计文档
└── README.md                      # 本文件
```

---

## 经济系统

详见 [`ECONOMY_SYSTEM.md`](ECONOMY_SYSTEM.md)

### 积分流转

```
完成任务 → 提交 CoinEarning(pending)
    ↓
家长审批 → confirmed（积分到账）/ rejected（拒绝）
    ↓
孩子兑换商品 → 扣除积分
```

### 可配置参数

| 参数 | 默认值 | 说明 |
|:-----|:-----|:-----|
| 任务基础积分 | 10 | 每次完成任务的基准积分 |
| 单词学习积分 | 5 | 每答对一个单词 |
| 复习通关积分 | 20 | 复习模式全部通过 |
| 每日软上限（工作日） | 100 | 工作日积分上限 |
| 每日软上限（周末） | 150 | 周末积分上限 |
| 双倍积分日 | 关闭 | 手动开启的特殊奖励日 |

---

## 备份与恢复

### 导出

家长端 → ⚙️ 系统设置 → 🔐 数据导出备份 → 输入加密密码

备份文件格式：`.habitbak`（ZIP + AES-256-GCM 加密）

备份内容：
- ✅ 全部 17 张数据库表（`data.json`，含 gate_config 作业关卡配置 / daily_gates 每日作业记录）
- ✅ 商城商品图片 + 头像（`images/`，恢复时自动重定向路径）
- ✅ SharedPreferences 全量导出（枚举 `shared_prefs/` 目录，含经济参数/词库/洗衣/PIN/Key绑定等所有配置）
- ✅ 词库学习进度
- ✅ 积分审批记录

**自动备份**：每天首次打开家长端时静默自动备份一次（文件名 `auto_yyyyMMdd_HHmmss.habitbak`），滚动保留最近 **10 份**，超限自动删除最旧备份。恢复时 `auto_` 开头文件密码留空即可自动使用内置密码。

### 导入

家长端 → ⚙️ 系统设置 → 🔐 数据导出备份 → 选择备份文件 → 输入密码

> ⚠️ **注意**：v1.3.0 之前的旧版备份（8415 字节纯 JSON）与新 ZIP 格式不兼容。
> 如需恢复旧备份，请使用 v1.2.x 版本先导入，再升级导出。

### 存储位置

- 导出：`/storage/emulated/0/Download/HabitTracker_backup_[设备码]_[日期].habitbak`
- 应用数据：`/data/data/com.sister.habits/`

---

## 开发指南

### 数据库迁移

```java
// AppDatabase.java — 添加新版本时
static final Migration MIGRATION_X_Y = new Migration(X, Y) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase database) {
        database.execSQL("ALTER TABLE ... ADD COLUMN ...");
    }
};
```

### 添加新菜单项（使用 MenuHelper）

```java
// ✅ 推荐：路由表模式
MenuHelper.showWithBack(this, "标题", new String[]{
    "选项A", "选项B", "选项C"
}, this::goBack,          // 返回动作
    this::handleA,         // 第0项
    this::handleB,         // 第1项
    this::handleC          // 第2项
);

// ❌ 已废弃：手写 switch/case（索引易错）
```

### 单元测试

核心逻辑已接入 JUnit4 + Mockito，CI 每次提交自动执行 `testDebugUnitTest`：

```bash
# 本地运行全部单元测试
./gradlew testDebugUnitTest

# 测试覆盖范围
# - EarningService（积分计算）：6 个用例
# - DataMerger（同步合并去重）：5 个用例
```

> 新代码规范：核心业务逻辑（积分/合并/计算）必须配套单元测试；DAO 通过构造器注入 mock，保证纯 JVM 可测。

### 代码规范

- 所有 DAO 操作允许主线程（`allowMainThreadQueries`）
- 图片上传必须使用 `BitmapFactory.Options.inSampleSize` 压缩
- 敏感操作（删除、覆盖）必须经家长密码验证
- 临时文件使用 `/data/local/tmp/`，用完即删
- 新功能一律写独立类，ParentActivity 只留一行委托调用（禁止再膨胀上帝类）
- 对话框禁止 `setMessage + setItems` 组合（vivo ROM 列表不渲染），一律自定义布局

---

## 常见问题

### Q: 更新 APK 后数据丢失？
A: 项目使用固定调试签名（`app/debug.keystore`），同签名覆盖安装不会丢数据。如果从不同签名版本切换，需先导出备份。

### Q: 备份导入提示"未找到备份"？
A: Android 10+ 需要授予"读取存储"权限。v1.3.1 已自动弹窗请求权限。

### Q: 生日加速器没有设置入口？
A: 在家长端 → ⚙️ 系统设置 → 👤 个人信息 → 点击生日行选择日期。

### Q: 商城图片太大？
A: v1.3.0 起，上传图片自动压缩到最大 1280px、JPEG 75% 质量，约节省 98% 空间。

### Q: 如何添加新的词库？
A: 运行 `scripts/update_wordbank.py` 从 Excel 更新，或将 JSON 放入 `res/raw/` 目录。

---

## 更新日志

### v3.0.34 (2026-08-09)
**同步修复**
- 顶部同步按钮改为全同步（Hub+局域网+云端），原仅 WebDAV 且未配置时静默无效果
- 家长 Key 绑定成功后自动触发全同步（原只有扫码配对触发）
- 新设备全量引导：空设备加入自动拉取全部历史数据（修复增量模型对新设备盲区）
- 全同步异步化（Hub 发现阻塞调用移入子线程，避免 ANR）

### v3.0.33 (2026-08-09)
**功能新增**
- 赦免配置新增「⚡ 快速赦免今天」：一键标记今日作业免检，明天积分正常

**vivo 兼容**
- 全量排查 setMessage+setItems 组合（脚本扫描 71 个 java 文件），修复剩余 2 处：审核今日作业、备份文件选择，全部改为自定义布局

### v3.0.32 (2026-08-09)
**备份系统**
- 备份表清单 15 → 17 张（补齐 gate_config / daily_gates）
- prefs 全量枚举导出/恢复（不再硬编码 4 项）
- 头像恢复路径重定向修复

**功能新增**
- 自动备份：每天首次打开家长端静默备份，滚动保留 10 份
- 防重复发奖：作业审核「确认完成」拦截已 COMPLETED 状态

**vivo 兼容**
- 作业管理对话框改为自定义布局（setMessage+setItems 列表不渲染）

### v3.0.31 (2026-08-09)
**Bug 修复**
- 商城上架/下架/删除改为原地刷新（原重开对话框需退出两次）

### v3.0.30 (2026-08-09)
**功能新增**
- 复习错词循环：答错只重学错词，全对即通关并加分（原整组重来永远无法通关）
- 审批全部批准后显示净积分变化汇总（收入-支出）
- 赦免配置：外出/旅行/生病日期范围免检不打折（DB v13→v14）
- 积分账单：家长中心新增近 100 条流水明细 + 收支汇总
- 作业管理重构：状态显示 + 可操作项分离

### v3.0.29 (2026-08-07)
**工程质量**
- 单元测试从 0 到 11 个（EarningService 6 + DataMerger 5），接入 CI 每次提交自动执行
- 新代码隔离规则：新功能一律独立类，ParentActivity 只留委托调用
- 超时保护核查：LanSync/HubSync/RemoteSync 均已有超时配置

### v3.0.22 (2026-08-06)
**Bug 修复**
- 修复审批中心列表无法上下滑动（移除 rv_approval_hub 的 nestedScrollingEnabled=false，恢复嵌套滚动）

### v3.0.21 (2026-08-06)
**Bug 修复**
- 学习模式随机取词：先 shuffle 词池再取词，修复永远从 A 开头取词的问题

### v3.0.20 (2026-08-06)
**Bug 修复**
- 「我不会」学习弹窗 setCancelable(false)：禁止点空白处/返回键关闭跳过单词

### v3.0.19 (2026-08-06)
**功能新增**
- 商品编辑/添加对话框新增「类型(常驻/限量)+库存数量」字段，支持设置限量商品库存

### v3.0.18 (2026-08-06)
**Bug 修复**
- 审批列表空白根治：rvApprovalHub 从未 setLayoutManager，补 LinearLayoutManager

### v3.0.17 (2026-08-06)
**Bug 修复**
- 购物车条全局显示：所有 tab（全部/限量/心愿单）底部均显示购物车条
- 金币不足改为 AlertDialog 醒目弹窗；加购后 Toast 提示提交入口

### v3.0.16 (2026-08-06)
**Bug 修复**
- 编辑商品保存后不再重开管理对话框（消除叠栈，无需反复退出）

### v3.0.15 (2026-08-06)
**体验优化**
- 审批列表高度 320dp → 160dp（用户反馈占视觉空间）

### v3.0.14 (2026-08-06)
**布局调整**
- 删除洗衣审核独立按钮（已并入审批中心）
- 作业管理并入操作行（5 个按钮一排），删除高频快捷入口行

### v3.0.13 (2026-08-06)
**界面重构**
- 审批中心整体卡片化：绿色边框 + 浅绿底 + 圆角，移至页面顶部
- 列表区域固定高度、框内独立滚动，不影响主界面布局

### v3.0.12 (2026-08-06)
**Bug 修复**
- 审批列表塌缩根治：rv 固定高度替代 wrap_content，列表直接显示在主页

### v3.0.11 (2026-08-06)
**功能新增**
- 一键全部批准 / 一键全部拒绝
- 勾选视觉修复（CheckedTextView setChecked）

### v3.0.10 (2026-08-06)
**布局调整**
- 删除「作业管理」右侧重复的审批中心按钮

### v3.0.8 (2026-08-05)
**功能新增**
- 审批中心集成到主页：任务完成 + 积分申请 + 兑换申请统一列表，勾选/批量操作

### v2.x 系列 (2026-07-29 ~ 08-05)
- 单词学习模块：分级词库（小学→初中→高中）、学习/复习双模式、艾宾浩斯复习队列、单词发音
- 商城系统：商品分类 tab、链接导入、心愿单、购物车、兑换审批闭环
- 抽奖机：概率设置、奖品管理、老虎机动效
- 家长管理：数据总览、经济参数、加速器、假期折扣、WebDAV/局域网同步、QR 配对
- 备份系统：AES-256-GCM 加密 ZIP 备份、SAF 自定义位置

### v1.6.0 (2026-07-29)

**架构重构**
- MenuHelper 路由表模式替代 switch/case，消除索引脆弱性
- 备份系统重写：ZIP 格式 + 全 12 表 + 图片 + Preferences

**Bug 修复**
- 修复备份导入缺少 READ_EXTERNAL_STORAGE 权限
- 修复学习管理→每日限额/奖励错跳经济参数
- 修复生日加速器未设置时仍显示
- 修复今日预计积分显示逻辑不清晰

**功能新增**
- 个人信息页生日设置（DatePicker）
- 任务模板库 20+ 预设任务
- 积分审批独立入口
- 商城图片上传自动压缩

### v1.2.0 (2026-07-26)
- 新手引导页
- 固定调试签名
- 词库存档系统
- 五种任务类型

### v1.1.0
- 孩子端单词学习
- 积分商城兑换
- 家长审批流程

### v1.0.0
- 首次发布
- 基础打卡功能
- 双模式切换

---

## 许可证

MIT License © 2026 Cuiyi-Srama

---

<p align="center">
  <sub>用 ❤️ 为家人打造 | Built with love for family</sub>
</p>
