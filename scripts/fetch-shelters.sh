#!/usr/bin/env bash
# Downloads shelter data from Geonorge, converts UTM33N → WGS84 via the PWA
# script, and copies the result to both Android assets and PWA public dirs.
#
# Usage: ./scripts/fetch-shelters.sh
# Requires: bun (or node + tsx)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

PWA_SCRIPT="$PROJECT_ROOT/pwa/scripts/fetch-shelters.ts"
PWA_OUTPUT="$PROJECT_ROOT/pwa/public/data/shelters.json"
ANDROID_ASSETS="$PROJECT_ROOT/app/src/main/assets"
ANDROID_OUTPUT="$ANDROID_ASSETS/shelters.json"

# Ensure output dirs exist
mkdir -p "$(dirname "$PWA_OUTPUT")"
mkdir -p "$ANDROID_ASSETS"

echo "==> Fetching shelter data from Geonorge..."

# Run the PWA conversion script (download ZIP, convert UTM→WGS84, output JSON)
bun run "$PROJECT_ROOT/pwa/scripts/fetch-shelters.ts"

if [ ! -f "$PWA_OUTPUT" ]; then
    echo "ERROR: PWA script did not produce $PWA_OUTPUT"
    exit 1
fi

SHELTER_COUNT=$(python3 -c "import json; print(len(json.load(open('$PWA_OUTPUT'))))" 2>/dev/null || echo "?")
echo "==> Generated $SHELTER_COUNT shelters"

# Copy to Android assets
cp "$PWA_OUTPUT" "$ANDROID_OUTPUT"
echo "==> Copied to $ANDROID_OUTPUT"

echo "==> Done. Shelter data ready for both platforms."
