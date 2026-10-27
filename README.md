# Fold8 Launcher (`com.xsun.foldlauncher`)

**Ultra-Fast, Zero-Bloat Adaptive Home Launcher & System Companion for Samsung Galaxy Z Fold 8 and Android Slab Phones**

[![Android CI](https://github.com/katchum/fold-launcher/actions/workflows/android-ci.yml/badge.svg)](https://github.com/katchum/fold-launcher/actions/workflows/android-ci.yml)

Fold8 Launcher is a lightweight, 120Hz-native Jetpack Compose home screen replacement engineered specifically to eliminate Samsung One UI & foldables friction while staying lean and responsive on both foldable displays and standard Android slab phones.

> **Live Project & Architecture Health**: See [`docs/PROJECT-HEALTH.md`](docs/PROJECT-HEALTH.md) and [`docs/vc-launcher-integration.md`](docs/vc-launcher-integration.md).

---

## Key Features

### 1. Adaptive Posture Engine (Galaxy Z Fold 8 & Slab Phones)
Powered by `androidx.window:window` (`WindowInfoTracker` & `FoldingFeature`) and `PostureClassifier`, Fold8 Launcher adapts seamlessly in real time without activity restarts:
- **Compact / Cover / Slab Mode (`COMPACT`)**: One-handed 4-column thumb-zone dock and scannable favorite list optimized for narrow cover screens and standard slab phones.
- **Flex / Tabletop Mode (`FLEX_TABLETOP`)**: Detects half-opened horizontal hinge postures (`FoldingFeature.State.HALF_OPENED`), splitting the UI across the hinge crease with the clock & status header above the fold and interactive controls below.
- **Expanded Inner Tablet / DeX Mode (`EXPANDED`)**: Dual-pane workspace (5–6 columns) with side-by-side favorites, quick-action cards, and one-tap **Split-Screen App Pair** launching (`FLAG_ACTIVITY_LAUNCH_ADJACENT`).

### 2. 0ms Swipe-Up Spotlight Search + Inline Math
- **Instant Gesture Trigger**: Swipe up anywhere on the home screen (or tap the search pill) to immediately open the Spotlight overlay and focus the keyboard with zero animation lag.
- **Smart Scoring & Shorthand Ranking (`SearchEngine`)**: Ranks installed apps using exact match, prefix match, multi-word acronym matching (e.g., `yt` → *YouTube*, `gm` → *Google Maps*), word-boundary matching, substring matching, fuzzy subsequence matching, and launch frequency boosting.
- **Auto-Launch Top Hit**: Option to immediately launch the top result on pressing IME Search/Enter.
- **Inline Calculator**: Evaluates mathematical expressions directly inside the search bar (e.g., `120 * 4.5`, `(2560 - 1080) / 2`) with one-tap copy/insert.

### 3. Oversized Top-Right Hero Clock
- High-contrast, glanceable **Oversized Top-Right Clock & Date Header** designed to eliminate the cramped top-left carrier/notification bar clutter on One UI.
- Displays live posture badges (`COVER / SLAB`, `FLEX TABLETOP`, `UNFOLDED 7.6"+`) and battery/system status at a glance.

### 4. System Tweaker & Companion `FoldAccessibilityService`
Solves the biggest daily annoyances on Samsung One UI without requiring root:
- **Samsung DeX Gboard Auto-Guard**: Samsung DeX forcibly resets the active IME to Samsung Keyboard (`com.samsung.android.honeyboard`). When granted `WRITE_SECURE_SETTINGS`, `FoldAccessibilityService` monitors `Settings.Secure.DEFAULT_INPUT_METHOD` and display configuration changes, automatically restoring **Gboard** (or SwiftKey) and routing the DeX virtual keyboard to the phone screen (`dex_on_screen_keyboard_location = 1`).
- **Lock Screen & Home Screen Landscape Rotation**: One-tap toggle to write `lock_screen_allow_rotation = 1` and `accelerometer_rotation = 1` across `Settings.System`, `Settings.Secure`, and `Settings.Global`.
- **Zero-Drain Home Gestures**:
  - **Swipe Down Anywhere**: Expands the system notification shade (`GLOBAL_ACTION_NOTIFICATIONS` / `StatusBarManager`).
  - **Double-Tap Empty Space**: Immediately locks the screen (`GLOBAL_ACTION_LOCK_SCREEN`) without wearing out the physical power button.

---

## One-Time Non-Root ADB Setup & Debloat Scripts

Fold8 Launcher includes two standalone, non-root scripts in [`scripts/`](scripts/) to configure your Galaxy Z Fold 8 or Android slab phone in seconds.

### 1. Grant Permissions & Configure DeX / Rotation (`scripts/adb-setup-fold8.sh`)
Grants `android.permission.WRITE_SECURE_SETTINGS` to both debug and release builds, enables lock screen landscape rotation, configures DeX keyboard routing for Gboard, and sets Gboard as the default IME:

```bash
chmod +x scripts/adb-setup-fold8.sh
./scripts/adb-setup-fold8.sh

# Optional: also disable Samsung Honeyboard for user 0 so One UI can never fall back to it
./scripts/adb-setup-fold8.sh --disable-honeyboard
```

Or run the raw ADB grant command directly:
```bash
adb shell pm grant com.xsun.foldlauncher android.permission.WRITE_SECURE_SETTINGS
adb shell pm grant com.xsun.foldlauncher.debug android.permission.WRITE_SECURE_SETTINGS
```

### 2. Safe Non-Root Samsung Bloatware Removal (`scripts/adb-debloat-fold8.sh`)
Removes background battery hogs (Bixby wakeup agents, Samsung Free/SPage, Customization Service telemetry, AR Zone stickers, GOS/Game Launcher throttling, preinstalled Meta/Microsoft stubs) for `user 0` while **strictly preserving Samsung DeX, Knox/Wallet/Pass, Galaxy AI, S Pen, Foldable Continuity, and Good Lock**:

```bash
chmod +x scripts/adb-debloat-fold8.sh
./scripts/adb-debloat-fold8.sh
```

> **Reversibility**: Any package removed by `adb-debloat-fold8.sh` can be restored anytime without a factory reset via:
> ```bash
> adb shell cmd package install-existing <package_name>
> ```

---

## Building & Installing

### Prerequisites
- **JDK 17** (Eclipse Temurin 17 recommended)
- **Android SDK Platform 35** (`compileSdk = 35`, `minSdk = 29`)

### Build Commands
```bash
# Run unit tests (PostureClassifierTest, SearchEngineTest)
./gradlew testDebugUnitTest

# Run Android Lint checks
./gradlew lintDebug

# Build Debug APK (package: com.xsun.foldlauncher.debug)
./gradlew assembleDebug

# Build R8-minified, resource-shrunk Release APK (package: com.xsun.foldlauncher)
./gradlew assembleRelease
```

### Output APK Locations
- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK (R8 Minified, ADB-installable)**: `app/build/outputs/apk/release/app-release.apk`

---

## Project Architecture

```text
fold-launcher/
├── .github/workflows/
│   └── android-ci.yml            # GitHub Actions CI: unit tests, lint, debug & R8 release APKs
├── app/
│   ├── build.gradle.kts          # AGP 8.7.2, Kotlin 2.0.21, Compose BOM, WindowManager 1.3.0
│   ├── proguard-rules.pro        # R8 optimization & keep rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   └── java/com/xsun/foldlauncher/
│       │       ├── MainActivity.kt               # HOME intent handler & lifecycle integration
│       │       ├── data/                         # Fast async LauncherApps loader & preferences
│       │       ├── hud/                          # Live hardware telemetry HUD (CPU/RAM/Battery/Hz)
│       │       ├── model/                        # Installed app metadata & SplitPair models
│       │       ├── omni/                         # Spotlight Omni-Command Palette & quick actions
│       │       ├── posture/                      # FoldingFeature tracker & PostureClassifier
│       │       ├── search/                       # Spotlight ranking & inline math parser
│       │       ├── service/                      # DeX IME guard, SystemTweaker & global gestures
│       │       ├── spatial/                      # 120Hz spatial physics, reachability & thumb-arc math
│       │       ├── ui/                           # Adaptive Compose UI (theme, animation, components, screens)
│       │       ├── vc/                           # VC non-foreground bridge, monitor & home command center
│       │       └── viewmodel/                    # Unidirectional state flow & launcher orchestration
│       └── test/java/com/xsun/foldlauncher/
│           ├── omni/OmniCommandEngineTest.kt     # Unit tests for Omni-Command Palette & actions
│           ├── posture/PostureClassifierTest.kt  # Unit tests for Z Fold 8 & slab postures
│           ├── search/SearchEngineTest.kt        # Unit tests for Spotlight search & math parser
│           ├── spatial/SpatialMathTest.kt        # Unit tests for 120Hz spatial physics & thumb-arc geometry
│           └── vc/VcBridgeParserTest.kt          # Unit tests for VC attention feed & delta state parser
├── docs/
│   ├── PROJECT-HEALTH.md         # Live module ownership, feature checklist, test & CI status
│   └── vc-launcher-integration.md # VC non-foreground background bridge architecture
└── scripts/
    ├── acceptance-status.sh      # Commit status acceptance gate script for Integration Agent
    ├── adb-debloat-fold8.sh      # Safe non-root Z Fold 8 bloatware removal script
    ├── adb-setup-fold8.sh        # Non-root ADB setup for WRITE_SECURE_SETTINGS, DeX Gboard, rotation
    └── ci-scope.sh               # Fast CI scope detector (docs-only bypass)
```
