# HabitTracker — Good Habit Builder

**Android Native App (Java + Room)** | Built for kids — habit tracking + English vocabulary learning

![Build](https://github.com/Cuiyi-Srama/HabitTracker/actions/workflows/build.yml/badge.svg)

---

## ✨ Features

### 👶 Dual Mode
- **Child Mode** — Daily check-in, word learning, coin rewards, shop redemption
- **Parent Mode** — Dashboard, approvals, task management, word bank control, system settings

### 📦 Modules

| Module | Description |
|--------|-------------|
| **Check-in** | Daily with streak bonuses (up to +100 coins at 30 days) |
| **Word Learning** | Built-in + downloadable banks; batch review with error-weighting algorithm |
| **Economy** | 18-param system — earn coins via check-in/quiz/tasks, spend on entertainment |
| **Shop** | Custom items, parent approval gate, anti-abuse limits |
| **Tasks** | One-time / weekly / monthly / timed / permanent types |
| **Hub Sync** | P2P LAN + centralized Hub (port 18081) via NanoHTTPD |
| **QR Pairing** | Scan-to-pair, no cloud account required |
| **Encrypted Backup** | AES-256-GCM + PBKDF2, exports `.habitbak` files |
| **Update Check** | Auto-detect new version from GitHub Releases, one-tap download & install |

### 📚 Word Banks

| Bank | Words | Source |
|------|-------|--------|
| Primary (w/ phrases) | ~1,026 | ismartcoding/endict |
| Junior High | ~3,500 | KyleBing (IPA + translation) |
| Senior High | ~5,000 | KyleBing (IPA + translation) |
| CET-4 / CET-6 / Grad | ~4,000 – 6,500 | KyleBing extension |

> Grade selection: **Primary / Junior / Senior** (3 levels, backward compatible with old grade data)

---

## 🏗 Tech Stack

```
Java 17          — Core language
Room (v4)        — Local database (SQLite)
AppCompat + MDC  — Material Design 3 components
ZXing            — QR code scanning/generation
NanoHTTPD        — Lightweight HTTP server (Hub mode)
AES-256-GCM      — Encrypted backup encryption
PBKDF2           — Key derivation for backup
Gradle + AGP     — Build system
GitHub Actions   — Continuous deployment (CD)
```

---

## 🔧 Build

```bash
git clone https://github.com/Cuiyi-Srama/HabitTracker.git
cd HabitTracker
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

---

## 📥 Download

[![Release](https://img.shields.io/github/v/release/Cuiyi-Srama/HabitTracker)](https://github.com/Cuiyi-Srama/HabitTracker/releases)

Download the latest APK from [GitHub Releases](https://github.com/Cuiyi-Srama/HabitTracker/releases) and side-load on your device.

---

## 📋 Changelog

| Version | Highlights |
|---------|------------|
| **v1.5** | Word bank restructure (1026 primary words + phrases), 3-level grades, sync center rewrite, button UI fixes, **check-for-update**, Hub mode toggle |
| v1.4 | Encrypted backup (.habitbak), QR pairing, improved sync |
| v1.3 | Device identity (SHA-256 + Base32), notifications, Hub mode foundation |
| v1.2 | Batch review, error-weighting algorithm, economy system refinement |
| v1.1 | Economy system (18 params), tiered check-in bonuses, shop |
| v1.0 | Check-in, basic word learning, child/parent modes |

---

## 🔒 Privacy

- **No cloud accounts** — all data stored locally on-device
- **No telemetry** — no analytics, no crash reporters
- **Hub mode** — optional P2P sync, data never leaves your LAN
- **Encrypted backups** — AES-256-GCM with device-derived key
