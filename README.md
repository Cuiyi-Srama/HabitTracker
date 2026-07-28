# HabitTracker - Good Habit Builder

**Android Native App (Java + Room)** | Built for kids - habit tracking + English learning

## Features

### Dual Mode
- **Child Mode**: daily check-in, word learning, coin rewards, shop redemption
- **Parent Mode**: dashboard, approvals, task management, word bank control, system settings

### Modules
- Daily check-in with streak bonuses (up to +100 coins at 30 days)
- Word learning with built-in + downloadable banks, batch review (error-weighting algorithm)
- Economy system (18 params): earn coins via check-in/quiz/tasks, spend on entertainment
- Shop system: custom items, parent approval gate, anti-abuse limits
- Task system: one-time / weekly / monthly / timed / permanent types
- Hub sync: P2P LAN + centralized Hub (port 18081) via NanoHTTPD
- QR code pairing: scan-to-pair, no cloud account required
- Encrypted backup: AES-256-GCM + PBKDF2, exports .habitbak files

### Word Banks
| Bank | Words | Source |
|------|-------|--------|
| Primary (w/ phrases) | ~1026 | ismartcoding/endict |
| Junior High | ~3500 | KyleBing (IPA+translation) |
| Senior High | ~5000 | KyleBing (IPA+translation) |
| CET4/CET6/Grad Exam | ~4-6.5K | KyleBing extension |
| Custom Import | Any | JSON with bankId isolation |

Grade selection: Primary / Junior / Senior (3 levels, backward compatible with old grade data).

## Tech Stack
Java, Room (SQLite v4), AppCompat, Material Components, ZXing, NanoHTTPD, AES-256-GCM, PBKDF2, Gradle, GitHub Actions CD

## Build
```bash
git clone https://github.com/Cuiyi-Srama/HabitTracker.git
cd HabitTracker
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/
```

## Download
[GitHub Releases](https://github.com/Cuiyi-Srama/HabitTracker/releases) - download APK directly, side-load on device.

## Changelog
| Ver | Highlights |
|-----|------------|
| v1.0 | Check-in + basic learning |
| v1.1 | Economy system, tiered check-in |
| v1.2 | Batch review, error-weighting |
| v1.3 | Device identity, notifications |
| v1.4 | Encrypted backup, QR pairing |
| v1.5 | Word bank restructure (1026 primary), 3-level grades, sync center rewrite, button UI fixes |

## Privacy
No cloud accounts. All data stored locally, synced device-to-device.
