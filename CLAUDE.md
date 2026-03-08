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
- **Background work**: WorkManager (works without Play Services via built-in scheduler)

### Offline-First
This is an emergency app. Assume internet and infrastructure may be degraded or unavailable. All core functionality (finding nearest shelter, compass navigation, sharing location) must work offline after initial data cache. Avoid solutions that depend on external servers being reachable.

## Architecture
- **Language**: Kotlin, targeting Android API 26+ (Android 8.0+)
- **Build**: Gradle 8.7, AGP 8.5.2, KSP for Room annotation processing
- **Maps**: OSMDroid (offline-capable OpenStreetMap)
- **Database**: Room (SQLite) for shelter data cache
- **HTTP**: OkHttp for data downloads
- **Location**: FusedLocationProviderClient (Play Services) with LocationManager fallback
- **Background**: WorkManager for periodic widget updates
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
├── widget/        # Home screen widget, WorkManager periodic updater
└── util/          # Coordinate conversion (UTM→WGS84), distance calculations
```

## Building
```bash
./gradlew assembleDebug
```

## i18n
- Default (English): `res/values/strings.xml`
- Norwegian Bokmål: `res/values-nb/strings.xml`
- Norwegian Nynorsk: `res/values-nn/strings.xml`
