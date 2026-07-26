# 📋 变更日志 — HabitTracker / 好习惯养成

> **格式规范：** 本文件遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) 格式，
> 版本号遵循 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/)。
>
> 类型说明：
> - `Added` — 新增功能
> - `Changed` — 功能变更
> - `Deprecated` — 即将弃用
> - `Removed` — 已移除功能
> - `Fixed` — Bug 修复
> - `Security` — 安全修复

---

## [未发布] — Unreleased

### Added
- 初始项目结构与基础框架搭建
- Room 数据库实体、DAO、Repository 层实现
- 孩子乐园模式：每日打卡、单词学习、积分商城
- 家长管理模式：习惯管理、商品配置、数据统计
- 双模式切换与设备锁验证（Android Keyguard）
- 局域网同步功能（NanoHTTPD + ZXing QR 码配对）
- AES-256-GCM 加密备份/导出
- GitHub Actions CI 自动编译工作流
- 本项目开源社区规范文件（README、LICENSE、CONTRIBUTING 等）

### Changed
- （待发布后填写）

### Fixed
- （待发布后填写）

---

## [1.0.0] — 待发布

### Added
- 首个正式版本发布
- （发布前在此补充完整变更记录）

---

> 📌 版本记录格式示例：
>
> ## [1.1.0] — 2026-03-15
>
> ### Added
> - ✨ 新增艾宾浩斯记忆曲线单词复习模块
> - 🌐 添加多语言支持（英文界面）
>
> ### Changed
> - ♻️ 重构打卡模块数据流，引入 ViewModel + LiveData
> - ⚡ 优化数据库查询性能，引入索引
>
> ### Fixed
> - 🐛 修复跨天打卡时积分重复计算的问题
> - 🐛 修复商城商品库存为负数的边界情况
>
> ### Security
> - 🔒 修复加密备份中 IV 重复使用的潜在漏洞
