# Fold8 Launcher (`com.xsun.foldlauncher`) — `v0.9.0` (Minimal One UI + Side-by-Side Max Edition)

**Ultra-Fast, Zero-Bloat 120Hz One UI Home Launcher (`com.xsun.foldlauncher`) with QWERTY Typo-Tolerant Instant Search, Mutually Exclusive Horizontal/Vertical Gesture Axis Lock, 3-Page `Main`/`Work`/`Dev` Horizontal Pager, Tailscale & Wireless Debugging Switches + Side-by-Side Experimental Max Feature Edition (`com.xsun.foldlauncher.max`) for Samsung Galaxy Z Fold 8 and Android Slab Phones**

[![Android CI](https://github.com/katchum/fold-launcher/actions/workflows/android-ci.yml/badge.svg)](https://github.com/katchum/fold-launcher/actions/workflows/android-ci.yml)

Fold8 Launcher is a lightweight, 120Hz-native Jetpack Compose home screen replacement and system companion engineered specifically for the **Samsung Galaxy Z Fold 8** (`SM-F976U1` / `SM-F971U1`, One UI 9.0 / Android 16) and **Android Slab Phones** (Samsung Galaxy S23 Ultra `SM-S918U1`, One UI 8.5 / Android 16).

> **Live Project & Architecture Health**: See [`docs/PROJECT-HEALTH.md`](docs/PROJECT-HEALTH.md) and [`docs/vc-launcher-integration.md`](docs/vc-launcher-integration.md).

---

## What's New in `v0.9.0`

### 1. QWERTY-Aware Typo-Tolerant App Search (`SearchEngine.kt`)
- **Keyboard-Proximity & Transposition Typo Tolerance**: Upgraded `SearchEngine` with QWERTY adjacency-weighted Damerau-Levenshtein prefix & token edit distance (`qwertyPrefixTypoScore` + `qwertyAdjacent` map):
  - Typing `"uver"` (`v` adjacent to `b` on QWERTY) ranks **Uber** (`#1`) and **Uber Eats** (`#2`) ahead of sparse subsequence matches like **Userscripts** (`#3`).
  - Adjacent character transpositions (`"ubre"` → **Uber**, `"chorme"` → **Chrome**) and single-character omissions (`"trmux"` → **Termux**, `"tailscle"` → **Tailscale**) resolve cleanly with sub-millisecond zero-heap-churn evaluation.
- **Tight Contiguous Subsequence Gate**: Non-contiguous subsequence matching now requires `>= 4` query characters and tight match spans (`maxSpan <= query.length + 3`) so scattered 3-letter coincidences never outrank prefix or QWERTY-adjacent typo matches.

### 2. Mutually Exclusive Horizontal vs. Vertical Home Gesture Axis Lock (`LauncherScreens.kt`)
- **Strict 1D Axis Lock (`PointerEventPass.Initial`)**: Home Screen touch input in `MinimalOneUiHomeScreen` now locks to a single axis as soon as movement exceeds `16px`:
  - **Horizontal Axis (`|dx| > |dy| * 1.15f`)**: Locks exclusively to `HorizontalPager` (`Main` / `Work` / `Dev` page swipe) and suppresses vertical swipe-up/swipe-down triggers for the entire gesture.
  - **Vertical Axis (`|dy| > |dx| * 1.15f`)**: Locks exclusively to vertical gestures (Swipe Up -> Bottom-Keyboard Search; Swipe Down -> System Notification Shade) and consumes pointer changes on `PointerEventPass.Initial` so `HorizontalPager` never drifts horizontally during a vertical swipe.

---

## Highlights from `v0.8.0`

### 1. 3-Page `Main` / `Work` / `Dev` Horizontal Home Pager (`MinimalOneUiHomeScreen`)
- **Swipe Left/Right Between 3 Dedicated Non-Scrolling Pages**:
  - **Page 0 (`Main`)**: Daily communication, browser, camera, media, and personal apps (`4x2` on Cover/Slab, `6x2` on Unfolded Inner).
  - **Page 1 (`Work`)**: Productivity, calendar, email, notes, and workspace apps.
  - **Page 2 (`Dev`)**: **Tailscale & Wireless Debugging Switch Card (`OneUiConnectivityPillsRow`)** + developer tools (VC, Termux, GitHub, Settings, ADB utilities).
- **Interactive Page Customizer (`+ Edit Page`)**: Tap the page pill (`Main` / `Work` / `Dev`) or `+ Edit` to customize which apps appear on each page (`togglePageApp`).

### 2. Bottom-Keyboard Search Overlay (`OneUiMinimalAppSearchOverlay`)
- **Zero-Clutter Empty State (`0` Apps Shown Before Typing)**: Opening search via upward swipe anywhere on the Home Screen immediately focuses the search input at the bottom (right above the IME keyboard) with **zero initial app grid clutter**.
- **Top `4–8` Matches Only (`1–2` Rows)**: As soon as you type, displays only the top `4–8` highest-ranked matches right above the bottom search bar (`Enter`/`Search` launches the #1 match immediately).
- **Swipe Down or Tap Backdrop to Exit**: Swiping down anywhere on the search overlay or tapping the translucent backdrop closes search and hides the keyboard (`0ms`).

### 3. Dual Side-by-Side Product Flavors (`minimal` vs `max`)
- **`Fold8 Launcher` (`minimal` flavor, `com.xsun.foldlauncher`)**: Clean, distraction-free One UI 9.0 daily driver (`BuildConfig.IS_MAX_EDITION == false`).
- **`Fold8 Launcher Max` (`max` flavor, `com.xsun.foldlauncher.max`)**: Installs **side-by-side** on the same phone (`BuildConfig.IS_MAX_EDITION == true`) and unlocks the full experimental cockpit (VC Command Center, Omni-Bar, Radial Thumb Arc, Flex Trackpad, Tent Standby HUD, and Workspace Profiles).

---

## Highlights from `v0.7.0` (Fresh Minimal One UI Edition)

### 1. Fixed, Non-Scrolling Single-Screen Home Layout (`LauncherScreens.kt`)
- **Zero Vertical Home Scrolling**: Removed scrollable grids and legacy demo cards from the Home Screen so the Home Screen is a fixed, non-scrolling single canvas over your live Samsung One UI wallpaper (`windowShowWallpaper = true`).
- **Instant Swipe-Up App Search (`0ms`)**: Swiping up anywhere on the Home Screen (or tapping the bottom `"Search apps"` pill) immediately opens the clean App Search Drawer and focuses the software keyboard (`FocusRequester` + `LocalSoftwareKeyboardController`).
- **Static Favorite Apps + Bottom Dock**: Renders 2 non-scrolling rows of favorite apps (`4x2` on Cover/Slab, `6x2` on Unfolded Inner) above the persistent 1-row Bottom Favorites Dock.

### 2. Clean 2-Row Tailscale & Wireless Debugging Switch Card (`OneUiConnectivityPillsRow`)
- **Row 1 — Tailscale VPN**: Displays connection status (`Connected` / `Disconnected`), live Tailscale CGNAT IP (`100.x.y.z`), 1-tap copy, and a Material3 `Switch` toggle (`CONNECT_VPN` / `DISCONNECT_VPN`).
- **Row 2 — Wireless Debugging (`adb_wifi_enabled`)**: Displays live `ip:port` endpoint (`100.x.y.z:port` or LAN IP), 1-tap copy, Developer Options shortcut, and a Material3 `Switch` toggle (`Settings.Global.putInt(contentResolver, "adb_wifi_enabled", ...)`).

### 3. Silent Background DeX-to-Gboard Guard (`FoldAccessibilityService`, `SystemTweaker`)
- Automatically enforces Gboard (`com.google.android.inputmethod.latin/.LatinIME`) on the phone screen when Samsung DeX connects (`dexonpc_connection_state` / `UI_MODE_TYPE_DESK`) in the background (`ensureDeXGboardPreferred`) with zero floating HUD overlays or lock-screen hijacking.

---

## Highlights from `v0.6.0` (Streamlined One UI-Matched Edition)

### 1. Real System Wallpaper & Zero Lock-Screen Hijacking (`Theme.FoldLauncher`, `MainActivity`, `FoldAccessibilityService`)
- **Seamless Real System Wallpaper (`windowShowWallpaper = true`, `windowBackground = @android:color/transparent`)**: Renders the user's real Samsung One UI wallpaper directly behind the launcher with a subtle translucent tint (`Color.Black.copy(alpha = 0.16f)`) for zero visual flash when unlocking from the One UI Lock Screen.
- **Zero Lock-Screen Hijacking**: Removed `setShowWhenLocked(true)` / `setTurnScreenOn(true)` and disabled lock-screen accessibility overlays so the native Samsung One UI Lock Screen remains 100% untouched.
- **Native System Status Bar Only**: Respects `statusBarsPadding()` and uses Android's native top status bar/clock with zero duplicate launcher clock header.

### 2. One UI 9.0 Wireless Debugging & Tailscale Quick-Toggle Pills (`OneUiConnectivityPillsRow`, `WirelessAdbSnapshot`)
- **Left Pill — Wireless Debugging (`adb_wifi_enabled`)**: 1-tap toggle via `Settings.Global.putInt(contentResolver, "adb_wifi_enabled", ...)` (when `WRITE_SECURE_SETTINGS` is granted), live port & IP subtitle (`On • :42595`), 1-tap `📋` endpoint copy (`<ip>:<port>`), and 1-tap `⚙️` shortcut to Developer Options.
- **Right Pill — Tailscale Mesh VPN (`com.tailscale.ipn`)**: 1-tap VPN toggle (`CONNECT_VPN` / `DISCONNECT_VPN`), live Tailscale CGNAT IPv4 subtitle (`100.x.y.z`), 1-tap `📋` IP copy, and 1-tap `↗` shortcut to the Tailscale app.

### 3. Minimal Single-Screen One UI Home Layout & Instant Swipe-Up App Search (`LauncherScreens.kt`)
- **Streamlined Single-Screen Home Layout**:
  1. Top Row: `OneUiConnectivityPillsRow` (Wireless Debugging + Tailscale Quick-Toggle Pills).
  2. Compact 1-Row VC Bar: Live VC status dot + 1-tap `Open VC` + `VC + Termux` split + expandable `VcCommandCenterCard` chevron.
  3. Clean One UI Home App Grid (`4` columns on Cover/Slab, `6` columns on Unfolded Inner) + Bottom Favorites Dock.
- **0ms Swipe-Up App Search + Auto-Focused Keyboard**: Swiping up anywhere on the Home Screen (or tapping the `"Swipe up for apps & search"` pill) immediately opens the App Search Drawer, focuses the search input (`FocusRequester`), and brings up the software keyboard (`LocalSoftwareKeyboardController`).

---

## Highlights from `v0.5.0`

### 1. Unfold-Open Auto-Split Continuity (`UnfoldActionMode` & `isUnfoldOpenTransition`)
- **Automatic Unfold-to-Workspace Handoff (`isUnfoldOpenTransition`)**: Detects when the Galaxy Z Fold 8 hinge opens from closed/flex (`<= 120°`) across the `155°` inner display threshold and automatically triggers your configured `UnfoldActionMode`:
  - **`INNER_DASHBOARD`**: Opens the 120Hz dual-hemisphere Expanded Inner Dashboard.
  - **`AUTO_SPLIT_VC_TERMUX`**: Automatically launches **VC + Termux** side-by-side (`FLAG_ACTIVITY_LAUNCH_ADJACENT`) as soon as you unfold the phone.
  - **`AUTO_SPLIT_PROD_COMPARE`**: Automatically launches **VC Prod (`dev.agentconversation.vci`) + VC Compare (`dev.agentconversation.compare`)** side-by-side.
  - **`AUTO_SPLIT_VC_BROWSER`**: Automatically launches **VC + Chrome** side-by-side.
- Configurable in 1 tap from the System Tweaker card or via Omni-Bar (`> unfold <dashboard|split|vc+termux|compare|browser>`).

### 2. Live Tailscale Mesh VPN & LAN IP Telemetry (`NetworkTelemetrySnapshot`, `isTailscaleCgnatIpv4`, `TailscaleMeshTelemetryPill`)
- **Zero-Latency Network & Tailscale Mesh Detection (`NetworkTelemetrySnapshot`)**: Inspects active network interfaces (`tun0` / `tailscale0` / `wlan0` + `ConnectivityManager.TRANSPORT_VPN`) and identifies RFC 6598 CGNAT Tailscale IPv4 addresses (`100.64.0.0/10` via `isTailscaleCgnatIpv4`) alongside local Wi-Fi/LAN IPv4 addresses.
- **1-Tap IP Copy (`TailscaleMeshTelemetryPill`)**: Displays live `TAILSCALE <100.x.y.z>` and `LAN <192.168.x.y>` pills in the Top-Right Hero Header, Floating Telemetry HUD, and System Tweaker with 1-tap clipboard copy for instant wireless ADB (`adb connect`) and SSH/VC pairing.

### 3. Persistent Scratchpad & 1-Tap Device Telemetry Context Injector (`VcScratchpadPacket` & `buildTelemetryEnrichedPrompt`)
- **Collapsible `📝 Quick Scratchpad & Telemetry Injector` in `VcCommandCenterCard`**:
  - Persist quick notes, ADB endpoints, or prompt drafts in `LauncherPreferences` across app restarts and posture changes.
  - **1-Tap Device Telemetry Context Injector (`buildTelemetryEnrichedPrompt`)**: Optionally attaches a structured live hardware/posture/network context footer (`[Device Context: Z Fold 8 | Posture=... | Hinge=...° | Battery=...% (...°C, ...mA) | Tailscale=... | LAN=...]`) when dispatching your scratchpad note or prompt to VC agents.

### 4. Omni-Bar Unfold Continuity & Scratchpad Commands (`OmniCommandEngine`)
- **Unfold-Open Continuity Commands**:
  - `> unfold <dashboard|split|vc+termux|compare|browser>`: Switches the active `UnfoldActionMode`.
- **Scratchpad & VC Note Dispatch Commands**:
  - `note: <text>` / `memo: <text>`: Saves `<text>` directly to the persistent launcher scratchpad.
  - `note-vc: <text>`: Formats `<text>` with live device telemetry (`buildTelemetryEnrichedPrompt`), saves it to the scratchpad, and dispatches it immediately to the pinned or active VC session.

---

## Highlights from `v0.4.0`, `v0.3.0`, `v0.2.0` & `v0.1.0`

- **Mechanical Watch-Crown Hinge Haptic Detents (`HingeDetentTick`, `computeHingeDetentCrossed`, `triggerHingeDetentHaptic`)**: Fires `EFFECT_CLICK` at `15°`/`75°`/`155°` posture thresholds and `EFFECT_TICK` at `15°` increments.
- **Configurable Fold-Close Action Router (`FoldCloseActionMode`: `STAY_AWAKE_COVER`, `AUTO_VC_VOICE`, `TENT_STANDBY`, `INSTANT_LOCK` + `isFoldCloseTransition`)**.
- **Interactive Custom Dock Pinning (`pinnedDockPackages` + 1-tap `📌`/`📍` Pin-to-Dock button on App Drawer tiles)**.
- **Multi-Agent Squad Telemetry Bar (`VcSquadTelemetry`, `VcSquadTelemetryBar`) & 1-Tap Markdown Status Copy (`buildMarkdownStatusSnippet`, `📋 Copy Status`)**.
- **Z Fold 8 Secondary Display OLED Companion (`CoverScreenPresentation.kt` & `DualScreenPresentationController`)**.
- **Dedicated Lock-Screen Ambient Cockpit Card (`LockScreenCockpitCard`, `KeyguardManager.requestDismissKeyguard` 1-tap `Unlock & Split VC + Termux`, inline VC approval)**.
- **Zero-Permission Hardware Torch (`CameraManager.setTorchMode`), Workspace Profiles (`ALL`, `VC_DEV`, `FOCUS`, `OLED_BLACK`), and 3:2 Lissajous Burn-In Pixel-Shift (`computeOledPixelShiftOffset`)**.
- **Samsung DeX Gboard Auto-Guard (`FoldAccessibilityService`) & Non-Root Landscape Rotation**.

---

## One-Time Non-Root ADB Setup & Debloat Scripts

```bash
chmod +x scripts/adb-setup-fold8.sh scripts/adb-debloat-fold8.sh
./scripts/adb-setup-fold8.sh
./scripts/adb-debloat-fold8.sh
```

---

## Building & Installing

```bash
# Run all 5 unit test suites (PostureClassifierTest, SearchEngineTest, VcBridgeParserTest, OmniCommandEngineTest, SpatialMathTest)
./gradlew testDebugUnitTest

# Run Android Lint checks
./gradlew lintDebug

# Build Debug & R8-Minified Release APKs
./gradlew assembleDebug assembleRelease
```
