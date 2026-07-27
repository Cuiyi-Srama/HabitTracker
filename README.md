<div align="center">

# 🏆 好习惯养成 — HabitTracker

**专为小朋友设计的好习惯养成助手 · A Habit-Building Assistant for Kids**

[![Android](https://img.shields.io/badge/Android-14-3DDC84?logo=android)](https://www.android.com)
[![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk)](https://openjdk.org)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![minSdk](https://img.shields.io/badge/minSdk-24-brightgreen?logo=android)](https://developer.android.com/studio)
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

- 👶 **孩子模式** — 每日打卡、多邻国式单词选择题、艾宾浩斯记忆曲线复习、积分兑换奖品
- 👨‍👩‍👧 **家长模式** — 习惯管理、商品配置、数据统计、设备锁保护、局域网多端同步
- 🔒 **本地优先** — Room 本地数据库，离线可用，AES-256-GCM 加密备份

### English

**HabitTracker** is a native Android application designed for families, helping children (around 3rd grade) build good habits through a gamified positive-reinforcement loop of **daily check-ins ✅, word learning 📚, and reward shop 🏪**.

- 👶 **Child Mode** — Daily check-ins, Duolingo-style word quizzes, Ebbinghaus spaced repetition reviews, and reward redemptions
- 👨‍👩‍👧 **Parent Mode** — Habit management, reward configuration, statistics dashboard, device-lock protection, and LAN multi-device sync
- 🔒 **Local-First** — Offline-capable with Room local database and AES-256-GCM encrypted backups

---

## ✨ 功能特性 · Features

### 👶 孩子乐园模式 · Child Mode

| 中文 | English | Emoji |
|------|---------|-------|
| ✅ **每日打卡** — 自定义习惯列表，完成即打卡，获得积分奖励，支持连续天数激励 | **Daily Check-ins** — Custom habit list with streak rewards | ✅ |
| 📚 **单词学习** — 内置单词库，多邻国式选择题，艾宾浩斯复习曲线 | **Word Learning** — Built-in word bank, Duolingo-style quizzes, Ebbinghaus review curve | 📚 |
| 🏪 **积分商城** — 用积累的积分兑换家长设定的奖品/特权 | **Reward Shop** — Redeem points for parent-configured rewards | 🏪 |
| 📊 **进度看板** — 可视化本周/本月打卡完成率，成长曲线一目了然 | **Progress Dashboard** — Weekly/monthly completion rates and growth trends | 📊 |
| 🎨 **儿童友好 UI** — 色彩活泼、大按钮、液态背景动画 + 弹性反馈 | **Kid-Friendly UI** — Vibrant colors, big buttons, liquid background animation | 🎨 |

### 👨‍👩‍👧 家长管理模式 · Parent Mode

| 中文 | English | Emoji |
|------|---------|-------|
| ⚙️ **习惯管理** — 添加/编辑/删除待养成的习惯项目 | **Habit Management** — Add/edit/delete habits to track | ⚙️ |
| 🎁 **商品配置** — 设定商城可兑换的奖品名称、图标及所需积分 | **Reward Config** — Set reward names, icons, and point costs | 🎁 |
| 📈 **数据统计** — 查看孩子打卡记录、积分流水与趋势分析 | **Statistics** — View check-in history, point transactions, trends | 📈 |
| 🔒 **家长验证** — 进入管理区需生物识别/设备锁验证 | **Parent Auth** — Biometric/device-lock verification for parent zone | 🔒 |
| 🌐 **局域网同步** — 基于 NanoHTTPD 的零配置多设备数据同步 | **LAN Sync** — Zero-config multi-device sync via NanoHTTPD | 🌐 |
| 📷 **QR 码连接** — 扫码快速配对同步设备 | **QR Pairing** — Quick device pairing via QR code scan | 📷 |

### 🔧 通用能力 · Core Capabilities

- 🔐 **设备锁保护** — Android Keyguard 集成，敏感操作需验证身份
- 💾 **本地持久化** — Room 数据库存储，离线可用，数据不丢失
- 🔄 **局域网多端同步** — 家庭成员间数据合并，无缝共享进度
- 🔒 **加密备份** — AES-256-GCM 加密导出，保障隐私安全

---

## 🛠️ 技术栈 · Tech Stack

| 层级 · Layer | 技术 · Technology | 用途 · Purpose |
|-------------|-------------------|----------------|
| 📱 语言 · Language | **Java 17** | 主要开发语言 |
| 🏗️ 构建 · Build | **Gradle 8.1 + AGP 8.x** | 项目构建与依赖管理 |
| 🗄️ 数据库 · Database | **Room 2.5.2** (SQLite) | 本地持久化，编译时注解处理器 |
| 🖼️ UI | **Material Design 3 / ViewBinding / RecyclerView / ViewPager2 / CardView** | 界面组件 |
| 🌐 局域网服务 · LAN | **NanoHTTPD 2.3.1** | 轻量级 HTTP 服务，零配置多端同步 |
| 📷 二维码 · QR | **ZXing 3.5.1 + zxing-android-embedded 4.3.0** | QR 码生成与扫码配对 |
| 🔐 加密 · Crypto | **AES-256-GCM** (`javax.crypto` + PBKDF2) | 加密备份文件保护 |
| 🧬 生物识别 · Biometric | **AndroidX Biometric 1.2.0-alpha05** | 指纹/面部识别解锁 |
| 📝 序列化 · Serialization | **Gson 2.10.1** | JSON 序列化/反序列化 |
| 🔥 可选云端 · Cloud (opt) | **Firebase Realtime Database + Auth** | 云端远程同步（默认关闭） |
| 🧭 架构 · Architecture | **Activity + Fragment + ViewModel + LiveData** | 经典 MVVM 分层架构 |

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
└── parent_settings.png     # ⚙️ 习惯管理 / Habit Settings
```

---

## 🚀 快速开始 · Quick Start

### 📲 获取 APK · Download APK

> ⚠️ **项目维护者依赖 CI 编译，无法在本地编译 APK。**
> *The project maintainer relies on GitHub Actions for APK builds.*

**推荐方式：从 GitHub Actions 下载编译好的 APK**

1. 前往 [Actions 页面](https://github.com/Cuiyi-Srama/HabitTracker/actions)
2. 选择最新的成功 Workflow Run（绿色 ✅ 状态）
3. 在 **Artifacts** 部分下载 `HabitTracker-Debug-APK`
4. 解压后安装 `app-debug.apk` 到 Android 设备（API 24+）

> 💡 **普通用户**：点击上方 Actions 链接 → 选择最新成功的 build → 下载 Artifacts 即可。

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
- 👤 **昵称** → 家长模式 → ⚙️ 设置 → 个人信息 → 孩子昵称
- 📝 **App 标题** → 家长模式 → ⚙️ 设置 → 个人信息 → App 内标题
- 🖼️ **头像** → 家长模式 → ⚙️ 设置 → 个人信息 → 选择头像图片

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
| 🏠 **主页 / Home** | 今日打卡概览、连续天数、积分余额 |
| ✅ **打卡 / Check-in** | 展示所有习惯列表，点击即完成打卡 |
| 📚 **学习 / Learning** | 每日单词学习卡片，完成后同步打卡 |
| 🛒 **商城 / Shop** | 浏览可兑换商品，消耗积分兑换 |
| 📊 **进度 / Progress** | 周/月度打卡完成率、积分获取趋势 |

### 家长管理 · Parent Mode

| 界面 · Screen | 说明 · Description |
|--------------|-------------------|
| ⚙️ **习惯管理 / Habits** | 新增/编辑/删除习惯，设置名称与图标 |
| 🎁 **商品管理 / Rewards** | 新增/编辑/删除商城商品，设定积分价格 |
| 📈 **数据总览 / Stats** | 打卡日历、积分流水、各习惯完成统计 |
| 🌐 **同步设置 / Sync** | 启动/关闭局域网同步，扫码连接设备 |

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
├── app/                                          # 📱 主应用模块 / Main app module
│   ├── build.gradle                              # 模块级构建配置
│   ├── proguard-rules.pro                        # ProGuard 混淆规则
│   └── src/main/
│       ├── AndroidManifest.xml                   # 应用清单 / Manifest
│       ├── assets/                               # 📦 静态资源（单词库 JSON 等）
│       ├── java/com/sister/habits/
│       │   ├── HabitApp.java                     # 🔌 Application 入口
│       │   ├── MainActivity.java                 # 🚪 主入口/模式选择
│       │   ├── child/                            # 👶 孩子乐园 / Child Mode
│       │   │   ├── ChildActivity.java
│       │   │   ├── ChildHomeFragment.java
│       │   │   ├── CheckInFragment.java
│       │   │   ├── LearningFragment.java
│       │   │   └── ShopFragment.java
│       │   ├── parent/                           # 👨‍👩‍👧 家长管理 / Parent Mode
│       │   │   ├── ParentActivity.java
│       │   │   ├── HabitManageFragment.java
│       │   │   ├── RewardManageFragment.java
│       │   │   └── StatsFragment.java
│       │   ├── data/                             # 🗄️ 数据层 / Data Layer
│       │   │   ├── AppDatabase.java              # Room Database
│       │   │   ├── dao/                          # DAO 接口
│       │   │   ├── entity/                       # 实体类 / Entities
│       │   │   └── repository/                   # Repository 仓库
│       │   ├── sync/                             # 🔄 同步模块 / Sync
│       │   │   ├── LanSyncService.java           # NanoHTTPD HTTP 服务
│       │   │   ├── SyncManager.java              # 同步管理器
│       │   │   └── QRCodeHelper.java             # ZXing 二维码工具
│       │   ├── model/                            # 📦 业务模型 / Models
│       │   ├── viewmodel/                        # 🧠 ViewModel
│       │   └── util/                             # 🔧 工具类 / Utilities
│       │       └── CryptoHelper.java             # 🔐 AES-256-GCM 加密
│       └── res/
│           ├── drawable/                         # 🎨 图标/背景/形状
│           ├── layout/                           # 📐 XML 布局文件
│           ├── values/                           # 📝 字符串/颜色/主题
│           ├── values-night/                     # 🌙 夜间模式资源
│           └── xml/                              # ⚙️ 配置 XML
├── .github/                                      # 🐙 GitHub 配置
│   └── workflows/
│       └── build.yml                             # 🤖 CI 构建工作流
├── gradle/wrapper/                               # 📦 Gradle Wrapper
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
├── app/                                          # 📱 主应用模块
├── build.gradle                                  # 🏗️ 项目级构建配置
├── settings.gradle                               # 📋 项目设置
├── gradle.properties                             # ⚙️ Gradle 全局属性
├── gradlew / gradlew.bat                         # 🏃 Gradle Wrapper 脚本
├── HubServer.py                                  # 🐍 局域网 HUB 服务器脚本
├── PROJECT_SNAPSHOT.md                           # 📸 项目快照文档
├── FIREBASE_SETUP.md                             # 🔥 Firebase 配置指南
├── README.md                                     # 📄 本文件 / This file
├── LICENSE                                       # ⚖️ GPL-3.0 许可证
├── CONTRIBUTING.md                               # 🤝 贡献指南
├── CODE_OF_CONDUCT.md                            # 📜 行为准则
├── CHANGELOG.md                                  # 📋 变更日志
├── .gitignore                                    # 🚫 Git 忽略规则
└── screenshots/                                  # 🖼️ 截图目录
```

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