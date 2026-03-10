#!/usr/bin/env bash
# Generate F-Droid screenshots for all locales using Maestro.
#
# Prerequisites:
#   1. Install Maestro: curl -Ls https://get.maestro.mobile.dev | bash
#   2. Start an Android emulator (API 33+): emulator -avd <avd_name>
#   3. Build and install the app: ./gradlew installDebug
#   4. Run this script: .maestro/take-screenshots.sh
#
# Screenshots are saved directly into fastlane/metadata/android/<locale>/images/
#
# Locale handling:
#   - en-US and nb-NO use system locale (settings put system system_locales)
#   - nn-NO (Nynorsk) requires per-app locale since Android doesn't support
#     Nynorsk as a system locale — it falls back to Bokmål

set -euo pipefail
cd "$(dirname "$0")/.."

FLOW=".maestro/screenshots.yaml"
FLOW_NN=".maestro/screenshots-nn.yaml"

restart_framework() {
    adb shell stop 2>/dev/null
    sleep 2
    adb shell start 2>/dev/null
    sleep 8
}

ensure_root() {
    adb root 2>/dev/null || true
    sleep 1
}

echo "=== Ensuring root access ==="
ensure_root

# --- en-US ---
echo "=== Capturing screenshots for en-US ==="
mkdir -p "fastlane/metadata/android/en-US/images/phoneScreenshots"
rm -f "fastlane/metadata/android/en-US/images/.gitkeep"

adb shell "settings put system system_locales en-US"
restart_framework

sed -i 's/LOCALE: ".*"/LOCALE: "en-US"/' "$FLOW"
maestro test "$FLOW"
echo "=== Done: en-US ==="
echo ""

# --- nb-NO ---
echo "=== Capturing screenshots for nb-NO ==="
mkdir -p "fastlane/metadata/android/nb-NO/images/phoneScreenshots"
rm -f "fastlane/metadata/android/nb-NO/images/.gitkeep"

adb shell "settings put system system_locales nb-NO"
restart_framework

sed -i 's/LOCALE: ".*"/LOCALE: "nb-NO"/' "$FLOW"
maestro test "$FLOW"
sed -i 's/LOCALE: "nb-NO"/LOCALE: "en-US"/' "$FLOW"
echo "=== Done: nb-NO ==="
echo ""

# --- nn-NO (Nynorsk) ---
# Android doesn't support nn as a system locale, so we use per-app locale.
# The main flow must have run first to cache map tiles (nn flow uses clearState: false).
echo "=== Capturing screenshots for nn-NO ==="
mkdir -p "fastlane/metadata/android/nn-NO/images/phoneScreenshots"
rm -f "fastlane/metadata/android/nn-NO/images/.gitkeep"

adb shell "am force-stop no.naiv.tilfluktsrom"
adb shell "cmd locale set-app-locales no.naiv.tilfluktsrom --locales nn"
sleep 2

maestro test "$FLOW_NN"
echo "=== Done: nn-NO ==="
echo ""

# Restore en-US
adb shell "settings put system system_locales en-US"
adb shell "cmd locale set-app-locales no.naiv.tilfluktsrom --locales en"
restart_framework

echo "All screenshots captured."
echo "Check: fastlane/metadata/android/*/images/phoneScreenshots/"
