# Tilfluktsrom - Norwegian Emergency Shelter Finder

## Project Overview
Android app (Kotlin) that helps find the nearest public shelter (tilfluktsrom) in Norway during emergencies. Offline-first design: must work without internet after initial data cache.

## Architecture
- **Language**: Kotlin, targeting Android API 26+ (Android 8.0+)
- **Build**: Gradle 8.7, AGP 8.5.2, KSP for Room annotation processing
- **Maps**: OSMDroid (offline-capable OpenStreetMap)
- **Database**: Room (SQLite) for shelter data cache
- **HTTP**: OkHttp for data downloads
- **Location**: Google Play Services Fused Location Provider
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

## i18n
- Default (English): `res/values/strings.xml`
- Norwegian Bokmål: `res/values-nb/strings.xml`
- Norwegian Nynorsk: `res/values-nn/strings.xml`
