# 🎯 好习惯养成 (HabitTracker)

> **一个专为亲子设计的习惯养成与英语学习 Android 应用**
>
> Java 原生开发 · Room 本地数据库 · Material Design 3 · 离线可用

[![构建状态](https://github.com/Cuiyi-Srama/HabitTracker/actions/workflows/build.yml/badge.svg)](https://github.com/Cuiyi-Srama/HabitTracker/actions)
[![版本](https://img.shields.io/badge/版本-v2.2.0-blue)](https://github.com/Cuiyi-Srama/HabitTracker/releases)
[![许可](https://img.shields.io/badge/许可-MIT-green)](LICENSE)

---

## 📖 目录

- [项目简介](#项目简介)
- [核心功能](#核心功能)
- [系统架构](#系统架构)
- [快速开始](#快速开始)
- [项目结构](#项目结构)
- [经济系统](#经济系统)
- [备份与恢复](#备份与恢复)
- [开发指南](#开发指南)
- [常见问题](#常见问题)
- [更新日志](#更新日志)

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
| ✅ **审批中心** | 批量确认/拒绝：任务完成 + 积分申请 + 兑换申请 |
| 🎰 **抽奖管理** | 设价格、百分比概率（≤100%）、从商城添加奖品、完整编辑 |
| 📚 **学习管理** | 词库年级切换、每日学习限额、学习奖励参数 |
| 🏪 **商城管理** | 添加/编辑商品、🔗 淘宝/京东/拼多多链接自动导入（标题/价格/封面图） |
| 📋 **任务管理** | 创建任务、模板库（20+ 预设多选批量添加）、洗衣类型 |
| ⚙️ **系统设置** | 个人信息、经济参数、假期与折扣、加速器、备份（SAF自定义位置） |
| 📡 **设备同步** | Hub中枢 / 局域网P2P / ☁️ WebDAV远程同步 / QR配对 |

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
│  Room Database (v13)                       │
│  ├─ tasks          ├─ coin_earnings       │
│  ├─ shop_items     ├─ economy_config      │
│  ├─ word_records   ├─ checkin_records     │
│  └─ ... (共 12 张表)                       │
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
- ✅ 全部 12 张数据库表（`data.json`）
- ✅ 商城商品图片 + 头像（`images/`）
- ✅ Profile 偏好设置（`prefs.json`）
- ✅ 词库学习进度
- ✅ 积分审批记录

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

### 代码规范

- 所有 DAO 操作允许主线程（`allowMainThreadQueries`）
- 图片上传必须使用 `BitmapFactory.Options.inSampleSize` 压缩
- 敏感操作（删除、覆盖）必须经家长密码验证
- 临时文件使用 `/data/local/tmp/`，用完即删

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
