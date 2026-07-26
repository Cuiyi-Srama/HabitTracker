# 🤝 贡献指南 — Contributing to HabitTracker

> **好习惯养成（HabitTracker）** 是一款面向家庭的开源 Android 应用。
> 感谢你考虑为项目做出贡献！你的每一份努力都在帮助更多家庭培养孩子的好习惯 💪

[English Version ↓](#-contributing-guidelines-english)

---

## 📋 目录

- [📜 行为准则](#-行为准则)
- [🐛 报告 Bug](#-报告-bug)
- [💡 提出新功能](#-提出新功能)
- [🔀 提交 Pull Request](#-提交-pull-request)
- [🛠️ 开发环境要求](#️-开发环境要求)
- [🤖 关于 GitHub Actions 编译](#-关于-github-actions-编译)
- [📐 代码规范](#-代码规范)
  - [Java 命名规范](#java-命名规范)
  - [布局文件规范](#-布局文件规范)
  - [数据库规范](#-数据库规范)
  - [Commit Message 规范](#-commit-message-规范)
- [🌿 分支管理](#-分支管理)
- [📁 项目结构速览](#-项目结构速览)

---

## 📜 行为准则

参与本项目前，请阅读并遵守我们的 [行为准则](CODE_OF_CONDUCT.md)。
我们致力于为所有贡献者提供友好、包容、尊重他人的环境。

---

## 🐛 报告 Bug

报告 Bug 前请先搜索 [Issues](https://github.com/Cuiyi-Srama/HabitTracker/issues) 是否已有相同反馈。

### 提交流程

1. 点击 [New Issue](https://github.com/Cuiyi-Srama/HabitTracker/issues/new)
2. 选择 **Bug Report** 模板
3. 填写以下信息：

```markdown
**描述 Bug**
清晰简洁地描述问题是什么。

**复现步骤**
1. 打开应用 → 点击「...」
2. 进入「...」界面
3. 执行「...」操作
4. 看到错误

**期望行为**
正常情况下应该发生什么。

**截图/日志**
如有截图或崩溃日志，请附上。

**环境信息**
- 设备型号: [例如 Pixel 7]
- Android 版本: [例如 Android 14]
- 应用版本: [例如 1.0.0]
```

---

## 💡 提出新功能

1. 先搜索 Issues 确认没有重复建议
2. 使用 **Feature Request** 模板
3. 说明：
   - 这个功能解决了什么问题
   - 预期的使用场景
   - 可选的实现思路

---

## 🔀 提交 Pull Request

### 流程概览

```
Fork → Clone → Branch → Commit → Push → PR
```

### 详细步骤

1. **Fork 本仓库** 到你的 GitHub 账号

2. **Clone 到本地**
   ```bash
   git clone https://github.com/你的用户名/HabitTracker.git
   cd HabitTracker
   ```

3. **添加上游仓库**
   ```bash
   git remote add upstream https://github.com/Cuiyi-Srama/HabitTracker.git
   ```

4. **创建功能分支**
   ```bash
   git checkout -b feat/your-feature-name
   # 分支命名规范见下方
   ```

5. **编码并测试**
   - 确保代码在本地能通过编译（见下方开发环境要求）
   - 新增功能请添加对应测试

6. **提交代码**（Commit Message 规范见后文）
   ```bash
   git add .
   git commit -m ":sparkles: feat(xxx): 添加xxx功能"
   ```

7. **保持与上游同步**
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

8. **Push 到你的 Fork**
   ```bash
   git push origin feat/your-feature-name
   ```

9. **创建 Pull Request**
   - 回到 GitHub 页面创建 PR
   - 目标分支：`main`
   - 清楚描述改动内容和动机

### PR 检查清单

- [ ] 代码通过编译（CI 中的 `./gradlew assembleDebug` ✅）
- [ ] 遵循了代码规范（`./gradlew lint` 无警告）
- [ ] Commit message 符合格式
- [ ] 更新了相关文档（README 等）
- [ ] 新增功能有对应测试
- [ ] 如果是 UI 变更，附上了截图

### PR 合并后

- 删除你的本地/远程功能分支
- 同步上游 main 分支到你的 fork

---

## 🛠️ 开发环境要求

| 工具 | 版本 | 说明 |
|------|------|------|
| JDK | **17+** | 推荐 Amazon Corretto 17 或 Oracle JDK 17 |
| Android Studio | **Hedgehog (2023.1.1+)** | 最新稳定版 |
| Android SDK | **API 34** | compileSdk 目标版本 |
| Gradle | **8.1+** | 使用项目 Gradle Wrapper 自动管理 |
| 最低 SDK | **API 24** | Android 7.0 Nougat 起 |
| 目标 SDK | **API 34** | Android 14 |

### 推荐配置

```bash
# 设置 JAVA_HOME（示例）
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# 验证
java -version
# openjdk version "17.0.x"

./gradlew --version
# Gradle 8.1+
```

---

## 🤖 关于 GitHub Actions 编译

> ⚠️ **注意：项目维护者依赖 CI 编译，无法在本地编译 APK。**
> 但贡献者仍建议在本地搭建开发环境进行代码测试。

### 本地编译验证

```bash
# 编译 Debug APK
./gradlew assembleDebug

# 运行 Lint 检查
./gradlew lint

# 运行单元测试
./gradlew test
```

### CI 编译产物

- 每次 push 到 `main` / `master` 分支或提交 PR 时，GitHub Actions 自动触发编译
- 编译产物（APK）可在对应 Workflow Run 的 **Artifacts** 中下载
- 前往 [Actions 页面](https://github.com/Cuiyi-Srama/HabitTracker/actions) 查看最新构建状态

![CI Status](https://github.com/Cuiyi-Srama/HabitTracker/actions/workflows/build.yml/badge.svg)

---

## 📐 代码规范

### Java 命名规范

遵循 **Google Java Style Guide** 的基础上补充项目约定：

| 类别 | 规范 | 示例 |
|------|------|------|
| 类名 | **UpperCamelCase** | `HabitManager`, `ChildActivity` |
| 方法名 | **lowerCamelCase** | `checkIn()`, `loadHabits()` |
| 常量 | **UPPER_SNAKE_CASE** | `MAX_HABIT_COUNT`, `DB_NAME` |
| 变量 | **lowerCamelCase** | `habitList`, `userScore` |
| 包名 | **全小写** | `com.sister.habits.data.dao` |
| XML id | **snake_case** | `btn_check_in`, `tv_score` |
| 资源文件 | **snake_case** | `activity_child.xml` |

### 布局文件规范

- 每个 Activity/Fragment 对应一个 XML 布局文件
- 布局文件命名：`activity_<名称>.xml` / `fragment_<名称>.xml`
- 使用 **ViewBinding** 访问视图（已在 `build.gradle` 启用）
- 字符串资源统一放在 `res/values/strings.xml`
- 颜色值统一放在 `res/values/colors.xml`
- 避免硬编码 dp/sp 值，优先使用 `dimens.xml`

### 数据库规范

- 每个实体类放在 `data/entity/` 包下
- DAO 接口放在 `data/dao/` 包下
- 实体类使用 `@Entity` 注解，表名使用 **snake_case**
- 字段名使用 **lowerCamelCase**，Room 自动映射为 snake_case
- 索引命名：`index_<表名>_<字段名>`

```java
@Entity(tableName = "habit_records")
public class HabitRecord {
    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "habit_name")
    private String habitName;

    @ColumnInfo(name = "check_in_time")
    private long checkInTime;

    // Getter & Setter（必须）
}
```

### 代码风格检查

```bash
# 运行 Lint 检查
./gradlew lint

# 运行单元测试
./gradlew test
```

---

## 💬 Commit Message 规范

### 格式

```
<emoji> <类型>(<作用域>): <简短描述>

<可选详细说明>
```

### 类型与 Emoji 对照表

| Emoji | 类型 | 说明 |
|-------|------|------|
| ✨ `:sparkles:` | feat | 新功能 |
| 🐛 `:bug:` | fix | 修复 Bug |
| 📝 `:memo:` | docs | 文档更新 |
| 🎨 `:art:` | style | 代码风格（不影响功能） |
| ♻️ `:recycle:` | refactor | 重构 |
| ✅ `:white_check_mark:` | test | 测试相关 |
| ⚡ `:zap:` | perf | 性能优化 |
| 🔧 `:wrench:` | chore | 构建/工具配置 |
| 🚀 `:rocket:` | ci | CI/CD 配置 |
| 🏗️ `:building_construction:` | structure | 项目结构调整 |
| 💄 `:lipstick:` | ui | UI/UX 调整 |
| 🔒 `:lock:` | security | 安全修复 |
| 🌐 `:globe_with_meridians:` | i18n | 国际化/本地化 |
| 📱 `:iphone:` | responsive | 适配不同屏幕 |
| 🗑️ `:wastebasket:` | remove | 删除代码/文件 |

### 示例

```
✨ feat(habit): 添加习惯打卡自动连续天数计算
🐛 fix(checkin): 修复跨天打卡时积分重复计算的问题
📝 docs: 更新 README 中的项目结构树
♻️ refactor(database): 抽取 BaseDao 减少重复代码
✅ test(shop): 添加商品兑换积分扣减单元测试
🔧 chore: 升级 Room 版本至 2.5.2
🔒 security: 修复加密备份中 IV 重复使用的漏洞
```

---

## 🌿 分支管理

| 分支 | 用途 | 来源 | 合并目标 |
|------|------|------|----------|
| `main` | 稳定发布分支 | — | — |
| `develop` | 开发集成分支 | `main` | `main` |
| `feat/*` | 功能开发 | `develop` | `develop` |
| `fix/*` | Bug 修复 | `develop` 或 `main` | 对应分支 |
| `docs/*` | 文档更新 | `main` 或 `develop` | 对应分支 |

> 简单贡献可直接基于 `main` 创建分支，复杂功能请先与维护者沟通。

---

## 📁 项目结构速览

```
HabitTracker/
├── app/src/main/java/com/sister/habits/
│   ├── child/          # 👶 孩子模式（打卡/学习/商城）
│   ├── parent/         # 👨‍👩‍👧 家长管理（习惯/商品/统计）
│   ├── data/           # 🗄️ Room 数据库 + DAO + Entity
│   ├── sync/           # 🔄 局域网同步（NanoHTTPD + ZXing）
│   ├── viewmodel/      # 🧠 ViewModel
│   └── util/           # 🔧 工具类（CryptoHelper AES-256-GCM）
```

---

## ❓ 需要帮助？

- 查看 [Issues](https://github.com/Cuiyi-Srama/HabitTracker/issues) 寻找可以参与的任务
- 在本仓库 [Discussions](https://github.com/Cuiyi-Srama/HabitTracker/discussions) 发起讨论
- 阅读已有文档：`README.md` / `PROJECT_SNAPSHOT.md` / `FIREBASE_SETUP.md`

---

# 🤝 Contributing Guidelines (English)

Thank you for considering contributing to **HabitTracker (好习惯养成)**! ❤️

## Quick Summary

1. **Code of Conduct**: Please read our [Code of Conduct](CODE_OF_CONDUCT.md) before contributing.
2. **Report Bugs**: Search [Issues](https://github.com/Cuiyi-Srama/HabitTracker/issues) first, then use the Bug Report template.
3. **Suggest Features**: Use the Feature Request template and explain the problem & use case.
4. **Pull Requests**: Fork → Clone → Branch → Commit → Push → PR

## Development Environment

| Tool | Version | Notes |
|------|---------|-------|
| JDK | **17+** | Amazon Corretto 17 or Oracle JDK 17 |
| Android Studio | **Hedgehog (2023.1.1+)** | Latest stable |
| Android SDK | **API 34** | compileSdk target |
| Gradle | **8.1+** | Managed via Gradle Wrapper |
| minSdk | **API 24** | Android 7.0+ |
| targetSdk | **API 34** | Android 14 |

> ⚠️ **CI Build**: The project maintainer relies on GitHub Actions for APK builds. Please use `./gradlew assembleDebug` locally for testing.

## Branch Strategy

| Branch | Purpose | Base | Merge to |
|--------|---------|------|----------|
| `main` | Stable release | — | — |
| `develop` | Integration branch | `main` | `main` |
| `feat/*` | Feature development | `develop` | `develop` |
| `fix/*` | Bug fixes | `develop` or `main` | Corresponding |
| `docs/*` | Documentation | `main` or `develop` | Corresponding |

## Commit Message Format

```
<emoji> <type>(<scope>): <short description>

<optional detailed description>
```

Types: `feat` ✨, `fix` 🐛, `docs` 📝, `refactor` ♻️, `test` ✅, `chore` 🔧, `ci` 🚀, `ui` 💄, `security` 🔒

---

再次感谢你的贡献！🎉  Your contributions are greatly appreciated!