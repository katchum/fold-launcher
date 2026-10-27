#!/usr/bin/env bash
# ==============================================================================
# Galaxy Z Fold 8 & Android Slab Phone — One-Time ADB Setup Script
# ==============================================================================
# Configures system settings and grants privileges to Fold8 Launcher without root:
#   1. Grants WRITE_SECURE_SETTINGS to Fold8 Launcher (debug & release packages)
#   2. Enables Lock Screen & Home Screen landscape rotation on One UI
#   3. Routes Samsung DeX on-screen keyboard to Phone Screen so Gboard works
#   4. Sets Gboard as the default IME (if installed)
#   5. Optionally disables Samsung Keyboard (Honeyboard) if --disable-honeyboard is passed
# ==============================================================================

set -euo pipefail

ADB="${ADB:-adb}"
DISABLE_HONEYBOARD=false

for arg in "$@"; do
  case "$arg" in
    --disable-honeyboard)
      DISABLE_HONEYBOARD=true
      ;;
  esac
done

echo "[*] Checking connected Android device via ADB..."
"$ADB" wait-for-device

echo "[*] Granting WRITE_SECURE_SETTINGS to Fold8 Launcher..."
for pkg in "com.xsun.foldlauncher" "com.xsun.foldlauncher.debug"; do
  if "$ADB" shell pm list packages | grep -q "^package:${pkg}$"; then
    "$ADB" shell pm grant "$pkg" android.permission.WRITE_SECURE_SETTINGS && \
      echo "    + Granted WRITE_SECURE_SETTINGS to ${pkg}"
  else
    echo "    - Package ${pkg} not installed yet (skipping grant)"
  fi
done

echo "[*] Enabling Lock Screen & Home Screen Auto-Rotation..."
"$ADB" shell settings put system accelerometer_rotation 1
"$ADB" shell settings put system lock_screen_allow_rotation 1
"$ADB" shell settings put secure lock_screen_allow_rotation 1 || true
"$ADB" shell settings put global lock_screen_allow_rotation 1 || true

echo "[*] Configuring Samsung DeX keyboard location to Phone Screen (allows Gboard)..."
"$ADB" shell settings put global dex_on_screen_keyboard_location 1 || true

GBOARD_IME="com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"
if "$ADB" shell ime list -a | grep -q "$GBOARD_IME"; then
  echo "[*] Enabling and selecting Gboard as default keyboard..."
  "$ADB" shell ime enable "$GBOARD_IME"
  "$ADB" shell ime set "$GBOARD_IME"
else
  echo "[!] Gboard ($GBOARD_IME) not found on device. Install Gboard from Play Store first."
fi

if [ "$DISABLE_HONEYBOARD" = true ]; then
  echo "[*] Disabling Samsung Keyboard (com.samsung.android.honeyboard) for user 0..."
  "$ADB" shell pm disable-user --user 0 com.samsung.android.honeyboard || true
fi

echo "[+] Done! Your Z Fold 8 is configured for Fold8 Launcher."
