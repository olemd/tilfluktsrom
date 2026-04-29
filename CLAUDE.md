# Tilfluktsrom - Norwegian Emergency Shelter Finder

## Project Overview
Android app (Kotlin) that helps find the nearest public shelter (tilfluktsrom) in Norway during emergencies. Offline-first design: must work without internet after initial data cache.

## Design Principles

### De-Google Compatibility
The app must work on devices without Google Play Services (e.g. LineageOS, GrapheneOS, /e/OS). Every feature that uses a Google-specific API must have a fallback that works without it. Use Google Play Services when available for better accuracy/performance, but never as a hard dependency.

**Pattern**: Check for Play Services at runtime, fall back to AOSP/standard APIs.
- **Location**: Prefer FusedLocationProviderClient (Play Services) → fall back to LocationManager (AOSP)
- **Maps**: OSMDroid (no Google dependency)
- **Database**: Room/SQLite (no Google dependency)

### Offline-First
This is an emergency app. Assume internet and infrastructure may be degraded or unavailable. All core functionality (finding nearest shelter, compass navigation, sharing location) must work offline after initial data cache. Avoid solutions that depend on external servers being reachable.

## Architecture
- **Language**: Kotlin, targeting Android API 26+ (Android 8.0+)
- **Build**: Gradle 8.7, AGP 8.5.2, KSP for Room annotation processing
- **Maps**: OSMDroid (offline-capable OpenStreetMap)
- **Database**: Room (SQLite) for shelter data cache
- **HTTP**: OkHttp for data downloads
- **Location**: FusedLocationProviderClient (Play Services) with LocationManager fallback
- **UI**: Traditional Views with ViewBinding

## Key Data Flow
1. Shelter data downloaded as GeoJSON ZIP from Geonorge (EPSG:25833 UTM33N)
2. Coordinates converted to WGS84 (lat/lon) via `CoordinateConverter`
3. Stored in Room database for offline access
4. Nearest shelters found using Haversine distance calculation
5. Direction arrow uses device compass bearing minus shelter bearing

## Data Sources
- **Shelter data**: `https://nedlasting.geonorge.no/geonorge/Samfunnssikkerhet/TilfluktsromOffentlige/GeoJSON/...`
- **Map tiles**: OpenStreetMap via OSMDroid (auto-cached for offline use)

## Package Structure
```
no.naiv.tilfluktsrom/
├── data/          # Room entities, DAO, repository, GeoJSON parser, map cache
├── location/      # GPS location provider, nearest shelter finder
├── ui/            # Custom views (DirectionArrowView), adapters
└── util/          # Coordinate conversion (UTM→WGS84), distance calculations
```

## Building
```bash
./gradlew assembleDebug
```

## Build Variants
- **standard**: Includes Google Play Services for better GPS accuracy
- **fdroid**: AOSP-only, no Google dependencies

## Distribution
- **Forgejo** (primary): `kode.naiv.no/olemd/tilfluktsrom` — releases with both APK variants + PWA tarball
- **GitHub** (mirror): `github.com/olemd/tilfluktsrom` — automatically mirrored from Forgejo, do not push manually
- **F-Droid**: Metadata maintained in a separate fdroiddata repo (GitLab fork). F-Droid builds from source using the `fdroid` variant and signs with the F-Droid key.

## Release Process
When creating a new release:
1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`
2. Update User-Agent string in `ShelterRepository.kt` to match new version
3. Build Android APKs: `./gradlew assembleStandardRelease assembleFdroidRelease`
4. Build PWA: `cd pwa && bun scripts/fetch-shelters.ts && bun run build && tar -czf /tmp/tilfluktsrom-vX.Y.Z-pwa.tar.gz -C dist .`
5. Commit, push, then create Forgejo release with all three artifacts:
   ```bash
   fj release create --create-tag vX.Y.Z --branch main \
     --attach "/tmp/tilfluktsrom-vX.Y.Z-standard.apk:tilfluktsrom-vX.Y.Z-standard.apk" \
     --attach "/tmp/tilfluktsrom-vX.Y.Z-fdroid.apk:tilfluktsrom-vX.Y.Z-fdroid.apk" \
     --body "release notes" "vX.Y.Z"
   fj release asset create vX.Y.Z /tmp/tilfluktsrom-vX.Y.Z-pwa.tar.gz tilfluktsrom-vX.Y.Z-pwa.tar.gz
   ```
6. Install on phone: `adb install -r app/build/outputs/apk/standard/release/app-standard-release.apk`

## Screenshots

Use the Android emulator and Maestro to take screenshots for the README and fastlane metadata.

### Emulator setup
- AVD: `tilfluktsrom` (Pixel 6, API 35, google_apis/x86_64)
- Start headless: `~/android-sdk/emulator/emulator -avd tilfluktsrom -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &`
- When both a physical device and emulator are connected, use `-s emulator-5554` with adb
- Set fake GPS: `adb -s emulator-5554 emu geo fix <longitude> <latitude>` (note: longitude first)
- Grant permissions before launch: `adb -s emulator-5554 shell pm grant no.naiv.tilfluktsrom android.permission.ACCESS_FINE_LOCATION`
- Always cache map tiles ("Lagre kart") — never skip caching for screenshots

### Maestro (v2.3.0)
- Installed at `~/.maestro/bin/maestro`
- Use Maestro flows for repeatable screenshot sequences instead of manual `adb shell input tap` coordinates
- Maestro can target a specific device: `maestro --device emulator-5554 test flow.yaml`
- Place flows in a `maestro/` directory if creating reusable screenshot workflows

### Screenshot destinations
Screenshots go in all three fastlane locale directories:
- `fastlane/metadata/android/en-US/images/phoneScreenshots/`
- `fastlane/metadata/android/nb-NO/images/phoneScreenshots/`
- `fastlane/metadata/android/nn-NO/images/phoneScreenshots/`

Current screenshots:
1. `1_map_view.png` — Map with shelter markers
2. `2_shelter_selected.png` — Selected shelter with direction arrow
3. `3_compass_view.png` — Compass navigation with north indicator
4. `4_civil_defense_info.png` — Civil defense instructions
5. `5_about.png` — About page with privacy statement

## i18n
- Default (English): `res/values/strings.xml`
- Norwegian Bokmål: `res/values-nb/strings.xml`
- Norwegian Nynorsk: `res/values-nn/strings.xml`


<!-- BEGIN BEADS INTEGRATION v:1 profile:minimal hash:ca08a54f -->
## Beads Issue Tracker

This project uses **bd (beads)** for issue tracking. Run `bd prime` to see full workflow context and commands.

### Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --claim  # Claim work
bd close <id>         # Complete work
```

### Rules

- Use `bd` for ALL task tracking — do NOT use TodoWrite, TaskCreate, or markdown TODO lists
- Run `bd prime` for detailed command reference and session close protocol
- Use `bd remember` for persistent knowledge — do NOT use MEMORY.md files

## Session Completion

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until `git push` succeeds.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **PUSH TO REMOTE** - This is MANDATORY:
   ```bash
   git pull --rebase
   bd dolt push
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Clean up** - Clear stashes, prune remote branches
6. **Verify** - All changes committed AND pushed
7. **Hand off** - Provide context for next session

**CRITICAL RULES:**
- Work is NOT complete until `git push` succeeds
- NEVER stop before pushing - that leaves work stranded locally
- NEVER say "ready to push when you are" - YOU must push
- If push fails, resolve and retry until it succeeds
<!-- END BEADS INTEGRATION -->
