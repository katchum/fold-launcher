# Contributing to Fold8 Launcher

Thank you for contributing to **Fold8 Launcher** (`com.xsun.foldlauncher`)! This project follows the **VC Agent CI & Integration Playbook** so multiple specialists and the Integration Agent can ship cleanly through Git, GitHub Actions CI, and live device verification.

---

## How a Change Reaches `main` (VC CI Playbook)

1. **The Author** works on a branch or feature module, pushes it to GitHub (`katchum/fold-launcher`), and opens a PR with auto-merge enabled:
   ```bash
   gh pr merge <PR> --auto --merge
   ```
2. **`check`** ([`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml)) runs on every push to the PR and on `main`. Changes touching only `docs/**/*.md` pass through quickly via `scripts/ci-scope.sh` so required status checks never hang.
3. **Acceptance & Integration (`Integration Agent`)** reviews the exact 40-hex head commit, verifies cross-module compilation and CI artifacts (`app-debug.apk` and R8-minified `app-release.apk`), and records the verdict as the commit status `acceptance`:
   ```bash
   scripts/acceptance-status.sh <40-hex sha> approve "<what was reviewed>"
   scripts/acceptance-status.sh <40-hex sha> reject "<why>"
   ```
4. **Device Release Gate**: The single **Integration Agent** downloads the built APK artifacts and installs/verifies them on the live test device (e.g., wireless ADB `100.68.53.123:40919`, Samsung Galaxy S23 Ultra `SM_S918U1` running One UI 8.5 / Android 16, or Galaxy Z Fold 8) using `--user 0`.

### When `main` is Red
If `check` fails on `main` after a merge, nothing else merges until `main` is green again:
1. Revert the breaking merge first (`git revert -m 1 <merge>`). Never force-push `main`.
2. Fix the issue forward on a branch with a regression test.

---

## Development Environment

- **JDK**: Java 17 (Temurin 17; matches CI)
- **Gradle**: Gradle `8.10.2` via `./gradlew` wrapper
- **Android Gradle Plugin (AGP)**: `8.7.2`
- **Kotlin**: `2.0.21` (with `org.jetbrains.kotlin.plugin.compose`)
- **Android SDK**: `compileSdk = 35`, `targetSdk = 35`, `minSdk = 29` (Android 10+)

---

## Build, Test & Lint Commands

The full CI verification command is:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

### Individual Tasks

1. **Unit Tests (`testDebugUnitTest`)**:
   ```bash
   ./gradlew testDebugUnitTest
   ```
   - Exercises pure JVM tests under `app/src/test/java/com/xsun/foldlauncher/`:
     - `PostureClassifierTest`: Verifies `COMPACT` (cover screen / slab), `FLEX_TABLETOP` (half-opened horizontal fold), and `EXPANDED` (unfolded 7.6"+ inner display / book posture) classification and grid column recommendations.
     - `SearchEngineTest`: Verifies Spotlight app ranking (exact, prefix, acronym, substring, subsequence, launch frequency boost) and `evaluateInlineMath`.
     - `VcBridgeParserTest`: Verifies `/api/attention` full & delta parsing, `/api/snapshot` fallback, and background notification diffing (`computeNewBackgroundAlerts`).
     - `OmniCommandEngineTest`: Verifies Spotlight Omni-Command Palette parsing (shell commands, deep links, clipboard transforms, and system quick actions).
     - `SpatialMathTest`: Verifies 120Hz spatial physics, reachability cursor mapping, and ergonomic thumb-arc geometry.

2. **Android Lint (`lintDebug`)**:
   ```bash
   ./gradlew lintDebug
   ```
   - HTML report output: `app/build/reports/lint-results-debug.html`

3. **Debug APK (`assembleDebug`)**:
   ```bash
   ./gradlew assembleDebug
   ```
   - Output: `app/build/outputs/apk/debug/app-debug.apk` (`com.xsun.foldlauncher.debug`)

4. **Release APK (`assembleRelease`)**:
   ```bash
   ./gradlew assembleRelease
   ```
   - Runs R8 full-mode code shrinking (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`).
   - Output: `app/build/outputs/apk/release/app-release.apk` (`com.xsun.foldlauncher`)

---

## Live Device ADB Verification (`--user 0` Policy)

When installing or testing on a connected Samsung device (such as the Galaxy S23 Ultra slab device at `100.68.53.123:40919` or Galaxy Z Fold 8):
- Always target `--user 0` (personal profile only; Work Profile installs are prohibited by host policy):
  ```bash
  adb -s 100.68.53.123:40919 install --user 0 -r app/build/outputs/apk/debug/app-debug.apk
  ```
- Run `./scripts/adb-setup-fold8.sh` to grant `android.permission.WRITE_SECURE_SETTINGS`, enable lock screen rotation, and configure DeX Gboard auto-guard.

---

## Working Rules & Performance Guidelines

1. **Where You Work**:
   - Work in the project tree or your `.agent_dirs/<agent-id>/` scratch directory (never put large files in `/tmp`).
   - Guard chained `cd` commands (`cd <dir> || exit 1`) and stop only processes you started by PID.
2. **Waiting on CI / Long Tasks**:
   - Register background waits with `project_control({ action: "watch" })` and end your turn so the completion event wakes you at 0 token cost.
3. **Zero Background Bloat**:
   - Do not add background polling services, network analytics, or wake-locks.
   - Keep `FoldAccessibilityService` event filtering strictly scoped to `TYPE_WINDOW_STATE_CHANGED` and `ContentObserver` callbacks on `Settings.Secure.DEFAULT_INPUT_METHOD`.
4. **120Hz Compose Smoothness & Foldable/Slab Parity**:
   - Keep app icon loading off the main thread (`Dispatchers.IO`).
   - Every UI screen must gracefully support `DevicePosture.COMPACT` (narrow cover screen or S23 Ultra / slab phone), `DevicePosture.FLEX_TABLETOP`, and `DevicePosture.EXPANDED` (unfolded inner tablet & DeX).
