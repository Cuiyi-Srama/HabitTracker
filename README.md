<div align="center">
# 🏆 好习惯养成 — HabitTracker
**专为小朋友设计的好习惯养成助手 · A Habit-Building Assistant for Kids**
[![Android](https://img.shields.io/badge/Android-14-3DDC84?logo=android)](https://www.android.com)
[![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk)](https://openjdk.org)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![minSdk](https://img.shields.io/badge/minSdk-24-brightgreen?logo=android)](https://developer.android.com/studio)
[![version](https://img.shields.io/badge/version-1.5.0-orange)](https://github.com/Cuiyi-Srama/HabitTracker/releases)
[![CI](https://github.com/Cuiyi-Srama/HabitTracker/actions/workflows/build.yml/badge.svg)](https://github.com/Cuiyi-Srama/HabitTracker/actions)
[![GitHub Stars](https://img.shields.io/github/stars/Cuiyi-Srama/HabitTracker?style=social)](https://github.com/Cuiyi-Srama/HabitTracker)
> 🎯 **播种一个行动，收获一种习惯；播种一种习惯，收获一种性格。**
> *"Sow an action, reap a habit; sow a habit, reap a character."*
>
> 用游戏化机制让好习惯的养成变得有趣、可量化、有即时反馈。
> A gamified positive-reinforcement loop for building life-long good habits.
</div>
---
## 📖 项目简介 · About
### 中文
**好习惯养成（HabitTracker）** 是一款面向家庭的 Android 原生 App，旨在帮助 **小学三年级** 左右的小朋友通过 **每日打卡 ✅ · 单词学习 📚 · 积分商城 🏪** 的正向激励闭环培养好习惯。
- 👶 **孩子模式** — 每日打卡、多邻国式单词选择题、组批复习+错误率加权、积分商城、心愿单⭐、任务系统
- 👨‍👩‍👧 **家长模式** — 审批中心（带小红点+通知直达）、6类层级菜单、经济参数微调（18项可调）、多词库切换、限时任务DatePicker
- 💰 **经济系统** — 签到分级奖励、单词/任务积分、娱乐时间兑换、每日上限防刷、家长端经济仪表盘
- 🆔 **设备标识** — SHA-256+多重熵源生成16位唯一Key，为多用户数据隔离奠定基础
- 🔒 **本地优先** — Room 本地数据库（v4），离线可用，AES-256-GCM 加密备份
### English
**HabitTracker** is a native Android application designed for families, helping children (around 3rd grade) build good habits through a gamified positive-reinforcement loop.
- 👶 **Child Mode** — Daily check-ins, Duolingo-style word quizzes, batch review with error weighting, reward shop, wishlist ⭐, task system
- 👨‍👩‍👧 **Parent Mode** — Approval center (badge + notification deep-link), 6-category hierarchical menu, 18 economic parameters, multi-wordbank switching, timed task DatePicker
- 💰 **Economy System** — Tiered check-in streaks, word/task coins, screen-time exchange, daily cap, economic dashboard
- 🆔 **Device Identity** — SHA-256 + multi-entropy 16-char unique key, foundation for multi-device isolation
- 🔒 **Local-First** — Offline-capable with Room local database (v4) and AES-256-GCM encrypted backups
---
## ✨ 功能特性 · Features
### 👶 孩子乐园模式 · Child Mode
| 中文 | English | Emoji |
|------|---------|-------|
| ✅ **每日打卡** — 自定义习惯列表，完成即打卡，获得积分奖励，支持连续天数激励（3/7/14/30天额外奖励） | **Daily Check-ins** — Custom habit list with tiered streak rewards | ✅ |
| 📚 **单词学习** — 内置/外部多词库，多邻国式选择题，学习模式答对得2金币，答错立即进复习队列 | **Word Learning** — Multi-wordbank, Duolingo-style quizzes, +2 coins per correct answer, wrong answers go to review queue | 📚 |
| 🔄 **组批复习** — 当日待复习词随机排列为一组，全答对一次性得积分，答错整组重排直到100%通过，错误率加权复现 | **Batch Review** — Random-order batch review, all-correct earns one-time reward, wrong answers reshuffle entire batch, error-weighted reappearance | 🔄 |
| 🏪 **积分商城** — 用积累积分兑换奖品，⭐心愿单收藏，全部/心愿单双Tab切换 | **Reward Shop** — Redeem points for rewards, wishlist favorites, All/Wishlist tabs | 🏪 |
| 📋 **任务系统** — 查看家长发布的任务，完成后等待确认获得金币 | **Task System** — View assigned tasks, earn coins upon parent approval | 📋 |
| 🎨 **儿童友好 UI** — 色彩活泼、大按钮、液态背景动画 + 弹性反馈、TTS朗读 | **Kid-Friendly UI** — Vibrant colors, big buttons, liquid background animation, TTS | 🎨 |
### 👨‍👩‍👧 家长管理模式 · Parent Mode
| 中文 | English | Emoji |
|------|---------|-------|
| 📊 **数据总览** — 统计看板 + 经济仪表盘（今日收入/今日消费/储蓄率） | **Dashboard** — Stats panel + economic dashboard | 📊 |
| ✅ **审批中心** — 兑换审批/任务确认统一入口（一级菜单），带小红点待处理数，通知直达 | **Approval Center** — Unified redemptions & tasks, badge count, notification deep-link | ✅ |
| 📚 **学习管理** — 多词库切换、外部词库下载（小学至考研6个源）、每日学习限额、奖励参数 | **Learning Management** — Multi-wordbank switching, 6 external sources, daily limits | 📚 |
| 🏪 **商城管理** — 上架/编辑/下架商品（不含孩子心愿单，家长不干涉） | **Shop Management** — Add/edit/delist items (wishlist is child's private space) | 🏪 |
| 📋 **任务管理** — 发布日常/挑战任务，DatePicker+TimePicker精确到分钟 | **Task Management** — Daily/challenge tasks with DatePicker & TimePicker | 📋 |
| ⚙️ **系统设置** — 个人信息、启动模式 & Hub中枢、经济参数微调（18项可调） | **System Settings** — Profile, startup mode, 18 economic parameters | ⚙️ |
### 🔧 通用能力 · Core Capabilities
- 🔐 **设备锁保护** — Android Keyguard + BiometricPrompt 集成，敏感操作需验证身份
- 🆔 **设备唯一标识** — SHA-256 + 多重熵源生成16位可读Key（格式：ABCD-EFGH-IJKL-MNOP），多设备数据隔离
- 💾 **本地持久化** — Room 数据库（v4），离线可用，数据不丢失
- 🔄 **局域网多端同步** — NanoHTTPD 零配置多设备同步
- 🔒 **加密备份** — AES-256-GCM 加密导出，保障隐私安全
- 💰 **经济系统** — 18项可调参数，签到/单词/任务三收入渠道，娱乐时间三档定价，每日收入上限
---
## 🛠️ 技术栈 · Tech Stack
| 层级 · Layer | 技术 · Technology | 用途 · Purpose |
|-------------|-------------------|----------------|
| 📱 语言 · Language | **Java 17** | 主要开发语言 |
| 🏗️ 构建 · Build | **Gradle 8.1 + AGP 8.x** | 项目构建与依赖管理 |
| 🗄️ 数据库 · Database | **Room 2.5.2** (SQLite v4) | 本地持久化，编译时注解处理器 |
| 🖼️ UI | **Material Design 3 / ViewBinding / RecyclerView / ViewPager2 / CardView** | 界面组件 |
| 🌐 局域网服务 · LAN | **NanoHTTPD 2.3.1** | 轻量级 HTTP 服务，零配置多端同步 |
| 📷 二维码 · QR | **ZXing 3.5.1 + zxing-android-embedded 4.3.0** | QR 码生成与扫码配对 |
| 🔐 加密 · Crypto | **AES-256-GCM** (`javax.crypto` + PBKDF2) | 加密备份文件保护 |
| 🧬 生物识别 · Biometric | **AndroidX Biometric 1.2.0-alpha05** | 指纹/面部识别解锁 |
| 📝 序列化 · Serialization | **Gson 2.10.1** | JSON 序列化/反序列化 |
| 🔥 可选云端 · Cloud (opt) | **Firebase Realtime Database + Auth** | 云端远程同步（默认关闭） |
| 🧭 架构 · Architecture | **Activity + Fragment + RecyclerView.Adapter** | 经典分层架构 |
---
## 📸 截图 · Screenshots
> 🖼️ 截图占位 — 请将截图放入 `screenshots/` 目录
> *Screenshot placeholders — place images into the `screenshots/` directory*
```
screenshots/
├── child_home.png          # 👶 孩子主页 / Child Home
├── child_checkin.png       # ✅ 打卡界面 / Check-in
├── child_learning.png      # 📚 单词学习 / Word Learning
├── child_shop.png          # 🏪 积分商城 / Reward Shop
├── parent_dashboard.png    # 👨‍👩‍👧 家长面板 / Parent Dashboard
└── parent_approval.png     # ✅ 审批中心 / Approval Center
```
---
## 🚀 快速开始 · Quick Start
### 📲 获取 APK · Download APK
> ⚠️ **项目维护者依赖 CI 编译，无法在本地编译 APK。**
> *The project maintainer relies on GitHub Actions for APK builds.*

**推荐方式：下载最新 Release**
👉 [前往 Releases 页面下载](https://github.com/Cuiyi-Srama/HabitTracker/releases/latest)

**备用方式：从 GitHub Actions 下载**
1. 前往 [Actions 页面](https://github.com/Cuiyi-Srama/HabitTracker/actions)
2. 选择最新的成功 Workflow Run（绿色 ✅ 状态）
3. 在 **Artifacts** 部分下载 `HabitTracker-Debug-APK`
4. 解压后安装 `app-debug.apk` 到 Android 设备（API 24+）
### 🛠️ 本地编译（适用于贡献者）· Build Locally (for Contributors)
#### 环境要求 · Prerequisites
| 工具 · Tool | 版本 · Version |
|------------|----------------|
| JDK | **17+**（推荐 Amazon Corretto 17 / Oracle JDK 17） |
| Android Studio | **Hedgehog (2023.1.1+)** 或更新版本 |
| Android SDK | **API 34** (compileSdk) |
| Gradle | **8.1+**（使用项目 Gradle Wrapper 自动管理） |
| 目标设备 | **Android 7.0+ (API 24)** |
#### 编译步骤 · Build Steps
```bash
# 1. 克隆仓库 / Clone
git clone https://github.com/Cuiyi-Srama/HabitTracker.git
cd HabitTracker
# 2. 编译 Debug APK
chmod +x gradlew
./gradlew assembleDebug --no-daemon
# APK 输出路径：
# app/build/outputs/apk/debug/app-debug.apk
# 3. 可选：直接安装到连接的设备
./gradlew installDebug
```
#### Android Studio 打开
1. **File → Open** → 选择项目根目录
2. 等待 Gradle 同步完成（首次需下载依赖，约 2~5 分钟）
3. 点击 **Run ▶** 选择设备运行
---
### 🎨 开源自定义 · Make It Yours
想把这个 App 改成你自己的版本？**一行命令搞定：**
```bash
# 克隆后运行
python3 scripts/customize_app.py --app-name "小明的日常" --nickname "小明"
# 也可以同时替换图标和包名
python3 scripts/customize_app.py --app-name "Baby Daily" --nickname "Alice" --icon my_icon.png --package com.yourname.babydaily
```
**App 内也可以随时修改：**
- 👤 **昵称** → 家长模式 → ⚙️ 设置 → 系统设置 → 个人信息
- 📝 **App 标题** → 家长模式 → ⚙️ 设置 → 系统设置 → 个人信息
- 🖼️ **头像** → 家长模式 → ⚙️ 设置 → 系统设置 → 个人信息
修改后保存，主页标题和按钮文字会自动更新 🎉
---
## 👨‍👩‍👧 双模式说明 · Dual Mode
```
应用启动 → MainActivity（模式选择界面 / Mode Selection）
                ├── 👆 点击"我是孩子" → ChildActivity（孩子乐园主页）
                └── 👆 点击"我是家长" → 设备锁验证 → ParentActivity（家长管理）
```
### 孩子乐园 · Child Mode
| 界面 · Screen | 说明 · Description |
|--------------|-------------------|
| 🏠 **主页 / Home** | 今日打卡概览、连续天数、积分余额、每日一句 |
| ✅ **打卡 / Check-in** | 一键打卡，连续天数激励金币 |
| 🏪 **商城 / Shop** | 浏览商品/心愿单⭐，提交兑换申请 |
| 📋 **任务 / Tasks** | 查看任务列表，完成后标记待家长确认 |
| 📚 **单词 / Words** | 每日新词学习（+2🪙/词）+ 组批复习（错误率加权） |
### 家长管理 · Parent Mode
| 界面 · Screen | 说明 · Description |
|--------------|-------------------|
| 📊 **数据总览 / Dashboard** | 统计看板+经济仪表盘+手动同步 |
| ✅ **审批中心 / Approval** | 兑换审批+任务确认+小红点+通知直达 |
| 📚 **学习管理 / Learning** | 多词库切换、外部下载、每日限额 |
| 🏪 **商城管理 / Shop** | 上架/编辑/下架商品 |
| 📋 **任务管理 / Tasks** | 发布日常/挑战任务（含DatePicker限时任务） |
| ⚙️ **系统设置 / Settings** | 个人信息、启动模式、经济参数（18项可调） |
---
## 💰 经济系统 · Economy System
### 收入渠道
| 渠道 | 默认奖励 | 说明 |
|:-----|:---------|:-----|
| ✅ **签到** | 基础10🪙，连续3/7/14/30天额外+5/+15/+30/+100🪙 | 每日一次 |
| 📚 **单词学习** | 2🪙/词（答对） | 答错不扣分，进复习区 |
| 🔄 **复习通关** | 2🪙/词（整组全对） | 答错整组重排，100%通过才得积分 |
| 📋 **日常任务** | 5~15🪙 | 家长可调范围 |
| 🎮 **挑战任务** | 20~50🪙 | 高难度高回报 |
### 消费渠道
| 项目 | 价格 | 说明 |
|:-----|:-----|:-----|
| 🎮 **娱乐时间15分钟** | 10🪙 | 游戏/屏幕时间 |
| 🎮 **娱乐时间30分钟** | 18🪙（9折） | 批量购买优惠 |
| 🎮 **娱乐时间60分钟** | 30🪙（75折） | 更优惠 |
| 🎁 **商城商品** | 家长自定义 | 实物/虚拟奖品 |
### 防刷机制
- 🔒 **每日收入上限**：500🪙（默认）
- 📖 **每日新词上限**：10个（默认）
- 🔄 **每日复习上限**：30个（默认）
- 🏦 **家长可调所有参数**，经济仪表盘实时监控
---
## ⚙️ 配置说明 · Configuration
### Firebase 远程同步（可选）
1. 在 [Firebase Console](https://console.firebase.google.com/) 创建项目
2. 下载 `google-services.json` 放入 `app/` 目录
3. 在 `app/build.gradle` 中取消注释 Firebase 相关依赖和插件
4. 详细步骤参见 [`FIREBASE_SETUP.md`](FIREBASE_SETUP.md)
### 局域网同步 · LAN Sync
- 默认 HTTP 端口由 `LanSyncService` 自动分配（通常为 8080）
- 所有设备需处于同一 WiFi 网络
- 支持 QR 码扫码快速配对，无需手动输入 IP
- 数据采用 JSON 格式传输，加密备份使用 **AES-256-GCM** 保护
### Gradle 属性参考 · Gradle Properties
| 属性 · Property | 推荐值 · Recommended | 说明 · Notes |
|-----------------|---------------------|--------------|
| `android.useAndroidX` | `true` | 启用 AndroidX |
| `android.enableJetifier` | `true` | 自动迁移旧版支持库 |
| `org.gradle.jvmargs` | `-Xmx2048m -XX:MaxMetaspaceSize=512m` | Gradle JVM 参数 |
---
## 📂 项目目录结构 · Project Structure
```
HabitTracker/
├── app/                                          # 📱 主应用模块
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   └── wordbank_sources.json             # 📦 外部词库源配置
│       ├── java/com/sister/habits/
│       │   ├── HabitApp.java                     # Application（含设备标识初始化）
│       │   ├── MainActivity.java                 # 入口/模式选择
│       │   ├── child/                            # 👶 孩子模式
│       │   │   ├── ChildActivity.java            # 主Activity（ViewPager+Tab）
│       │   │   ├── ChildPagerAdapter.java        # 页面适配器
│       │   │   ├── ShopFragment.java             # 商城+心愿单+兑换通知
│       │   │   ├── TaskFragment.java             # 任务列表
│       │   │   └── WordFragment.java             # 单词学习+组批复习
│       │   ├── parent/                           # 👨‍👩‍👧 家长管理
│       │   │   └── ParentActivity.java           # 全功能管理（6项一级菜单+审批中心）
│       │   ├── data/                             # 🗄️ 数据层
│       │   │   ├── AppDatabase.java              # Room v4（含MIGRATION_3_4）
│       │   │   ├── DatabaseInitializer.java      # 数据库初始化（含默认经济参数）
│       │   │   ├── dao/                          # DAO接口
│       │   │   │   ├── CheckInDao.java
│       │   │   │   ├── CoinTransactionDao.java   # 含getTotalEarnedSince/getTotalSpentSince
│       │   │   │   ├── EconomyConfigDao.java     # 18参数updateAll
│       │   │   │   ├── RedemptionDao.java
│       │   │   │   ├── ShopItemDao.java
│       │   │   │   ├── TaskDao.java
│       │   │   │   ├── VocabularyDao.java
│       │   │   │   ├── WordReviewDao.java
│       │   │   │   ├── WordBankDao.java
│       │   │   │   └── WishlistDao.java
│       │   │   └── models/                       # 实体
│       │   │       ├── CheckIn.java
│       │   │       ├── CoinTransaction.java
│       │   │       ├── EconomyConfig.java        # 18项经济参数
│       │   │       ├── Redemption.java
│       │   │       ├── ShopItem.java
│       │   │       ├── Task.java
│       │   │       ├── Vocabulary.java
│       │   │       ├── WordReview.java           # 含getErrorRate/getWeightedInterval
│       │   │       ├── WordBank.java
│       │   │       └── WishlistItem.java
│       │   ├── sync/                             # 🔄 同步
│       │   │   ├── LanSyncService.java
│       │   │   ├── SyncManager.java              # 调用DeviceIdentity
│       │   │   └── QRCodeHelper.java
│       │   └── utils/                            # 🔧 工具
│       │       ├── CryptoHelper.java
│       │       ├── SoundHelper.java
│       │       ├── ProfileManager.java
│       │       ├── WordBankParser.java
│       │       ├── NotificationHelper.java       # 🆕 通知系统
│       │       ├── DeviceIdentity.java           # 🆕 设备唯一标识
│       │       └── DailyQuote.java
│       └── res/
│           ├── drawable/
│           ├── layout/
│           │   ├── activity_parent.xml           # 审批仪表盘布局
│           │   ├── dialog_profile_settings.xml   # 📋 独立个人信息布局
│           │   ├── dialog_economy_settings.xml   # 💰 独立经济参数布局
│           │   ├── dialog_wordbank.xml           # 词库管理
│           │   ├── dialog_add_task.xml           # 限时任务DatePicker
│           │   └── ...
│           ├── values/
│           │   └── colors.xml
│           └── xml/
│               └── network_security_config.xml
├── .github/workflows/
│   └── build.yml                                 # 🤖 CI
├── scripts/
│   └── customize_app.py                          # 🎨 自定义脚本
├── HubServer.py / HubServer.spec                 # 🐍 HUB服务器
├── CHANGELOG.md                                  # 📋 变更日志
├── README.md
├── LICENSE                                       # GPL-3.0
└── screenshots/
```
---
## 📋 版本历史 · Changelog
| 版本 | 日期 | 亮点 |
|:-----|:-----|:-----|
| **v1.5.0** | 2026-07-27 | 💰 经济系统全面升级（18项参数+签到分级+娱乐兑换+经济仪表盘）、数据库v4、单词复习重构、错误率加权复现 |
| v1.4.0 | 2026-07-27 | 🆔 设备唯一标识系统、多用户数据隔离基础、个人信息/经济参数布局修复、二级菜单返回按钮全面补齐 |
| v1.3.0 | 2026-07-27 | 🏗️ 家长界面B方案重构（审批中心独立+小红点）、通知系统、词库管理增强、商城优化 |
| v1.2.0 | 2026-07-26 | 📚 词库管理系统、外部词库下载、艾宾浩斯复习、限时任务系统、家长审批流 |
| v1.1.0 | 2026-07-25 | 📋 任务系统、⏰ 限时任务、统计数据页面 |
| v1.0.0 | 2026-07-24 | 🎉 首个可用版本：每日打卡、单词学习、积分商城、局域网同步 |
---
## 🤝 贡献指南 · Contributing
我们欢迎任何形式的贡献！无论是报告 Bug、提交新功能，还是完善文档，你的每一份努力都在帮助更多家庭培养孩子的好习惯 💪
We welcome all forms of contribution! Whether it's reporting bugs, suggesting features, or improving documentation, every effort helps families build good habits for their children.
👉 **详见 / See: [CONTRIBUTING.md](CONTRIBUTING.md)**
参与前请阅读并遵守 [行为准则](CODE_OF_CONDUCT.md)。
Please read and follow our [Code of Conduct](CODE_OF_CONDUCT.md) before contributing.
---
## 📄 许可证 · License
本项目基于 **GNU General Public License v3.0** 开源发布。
This project is licensed under the **GNU General Public License v3.0**.
```
Copyright © 2026 Cuiyi-Srama
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```
详见 / See [LICENSE](LICENSE) 文件。
---
## 📬 联系我们 · Contact & Resources
| 渠道 · Channel | 链接 · Link |
|---------------|-------------|
| 🐛 问题反馈 / Bug Reports | [GitHub Issues](https://github.com/Cuiyi-Srama/HabitTracker/issues) |
| 💡 功能建议 / Feature Requests | [GitHub Discussions](https://github.com/Cuiyi-Srama/HabitTracker/discussions) |
| 📋 项目看板 / Project Board | [GitHub Projects](https://github.com/Cuiyi-Srama/HabitTracker/projects) |
| 🤖 CI 状态 / Build Status | [GitHub Actions](https://github.com/Cuiyi-Srama/HabitTracker/actions) |
---
## ⭐ Star 趋势 · Star History
如果这个项目对你有帮助，欢迎点亮右上角 **⭐ Star**，支持我们持续迭代！
If this project helps you, please give it a **⭐ Star** to support our continuous improvement!
---
<div align="center">
  <sub>
    用 ❤️ 为下一代打造的养成工具 — 让好习惯陪伴孩子一生
    <br>
    Built with ❤️ for the next generation — making good habits last a lifetime
  </sub>
  <br><br>
  <sub>
    <a href="https://github.com/Cuiyi-Srama/HabitTracker">Cuiyi-Srama/HabitTracker</a>
  </sub>
</div>
