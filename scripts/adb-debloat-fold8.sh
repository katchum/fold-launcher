#!/usr/bin/env bash
# ==============================================================================
# Safe Non-Root Samsung Bloatware Removal Script for Galaxy Z Fold 8
# ==============================================================================
# Uses `pm uninstall -k --user 0 <pkg>` to strip background battery hogs and
# duplicate Samsung/carrier bloatware while strictly preserving:
#   - Samsung DeX
#   - Samsung Knox / Wallet / Pass
#   - Galaxy AI / S Pen / Continuity / Foldable Display Services
#   - Good Lock compatibility
#
# To restore any package later:
#   adb shell cmd package install-existing <package_name>
# ==============================================================================

set -euo pipefail

ADB="${ADB:-adb}"

SAFE_DEBLOAT_PACKAGES=(
  # Bixby & Voice Wakeup (frees continuous background mic/assistant wakeups)
  "com.samsung.android.bixby.agent"
  "com.samsung.android.bixby.wakeup"
  "com.samsung.android.visionintelligence"
  "com.samsung.android.bixbyvision.framework"

  # Samsung Free / Minus-One Page / Marketing Feeds
  "com.samsung.android.app.spage"
  "com.samsung.android.service.peoplestripe"
  "com.samsung.android.rubin.app"            # Customization Service telemetry
  "com.samsung.android.mds"

  # AR Zone / Emoji Stickers Bloat
  "com.samsung.android.arzone"
  "com.samsung.android.aremoji"
  "com.samsung.android.aremojieditor"
  "com.samsung.android.livestickers"
  "com.sec.android.mimage.avatarstickers"

  # Game Booster / GOS (throttles CPU/GPU & runs background telemetry)
  "com.samsung.android.game.gamehome"
  "com.samsung.android.game.gametools"
  "com.samsung.android.game.gos"

  # Preinstalled Meta / Microsoft / Carrier Stubs
  "com.facebook.katana"
  "com.facebook.system"
  "com.facebook.appmanager"
  "com.facebook.services"
  "com.microsoft.skydrive"
)

echo "[*] Waiting for Z Fold 8 via ADB..."
"$ADB" wait-for-device

for pkg in "${SAFE_DEBLOAT_PACKAGES[@]}"; do
  if "$ADB" shell pm list packages | grep -q "^package:${pkg}$"; then
    echo "[-] Removing ${pkg} for user 0..."
    "$ADB" shell pm uninstall -k --user 0 "$pkg" || true
  else
    echo "[.] Already absent: ${pkg}"
  fi
done

echo "[*] Trimming background caches..."
"$ADB" shell cmd package bg-dexopt-job || true

echo "[+] Safe debloat complete! Reboot your Z Fold 8 for optimal idle battery consumption."
