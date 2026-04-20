# Architecture

This document describes the architecture of Tilfluktsrom, a Norwegian emergency shelter finder available as an Android app and a Progressive Web App (PWA). Both platforms share the same design philosophy — offline-first, no hard external dependencies — but are implemented independently.

## Table of Contents

- [Design Principles](#design-principles)
- [Data Pipeline](#data-pipeline)
- [Android App](#android-app)
  - [Package Structure](#package-structure)
  - [Data Layer](#data-layer)
  - [Location & Navigation](#location--navigation)
  - [Compass System](#compass-system)
  - [Map & Tile Caching](#map--tile-caching)
  - [Build Variants](#build-variants)
  - [Deep Linking](#deep-linking)
- [Progressive Web App](#progressive-web-app)
  - [Module Structure](#module-structure)
  - [Build System](#build-system)
  - [Data Layer (PWA)](#data-layer-pwa)
  - [Location & Compass (PWA)](#location--compass-pwa)
  - [Map & Offline Tiles (PWA)](#map--offline-tiles-pwa)
  - [Service Worker](#service-worker)
  - [UI Components](#ui-components)
- [Shared Algorithms](#shared-algorithms)
  - [UTM33N → WGS84 Conversion](#utm33n--wgs84-conversion)
  - [Haversine Distance & Bearing](#haversine-distance--bearing)
- [Offline Capability Summary](#offline-capability-summary)
- [Security & Privacy](#security--privacy)
- [Internationalization](#internationalization)

---

## Design Principles

### Offline-First
This is an emergency app. Core functionality — finding the nearest shelter, compass navigation, distance display — must work without internet after initial setup. Network is used only for initial data download, periodic refresh, and map tile caching.

### De-Google Compatibility (Android)
The Android app runs on devices without Google Play Services (LineageOS, GrapheneOS, /e/OS). Every Google-specific API has an AOSP fallback. Play Services improve accuracy and battery life when available, but are never required.

### Minimal Dependencies
Both platforms use few, well-chosen libraries. No heavy frameworks, no external CDNs at runtime. The PWA bundles everything locally; the Android app uses only OSMDroid, Room, and OkHttp.

### Data Sovereignty
Shelter data comes directly from Geonorge (the Norwegian mapping authority). No intermediate servers. The app fetches, converts, and caches the data locally.

---

## Data Pipeline

Both platforms consume the same upstream data source:

```
Geonorge ZIP (EPSG:25833 UTM33N)
  ↓ Download (~320KB)
  ↓ Extract GeoJSON from ZIP
  ↓ Parse features
  ↓ Convert UTM33N → WGS84 (Karney method)
  ↓ Validate (Norway bounding box, required fields)
  ↓ Store locally
```

**Source URL:** `https://nedlasting.geonorge.no/geonorge/Samfunnssikkerhet/TilfluktsromOffentlige/GeoJSON/Samfunnssikkerhet_0000_Norge_25833_TilfluktsromOffentlige_GeoJSON.zip`

**Fields per shelter:**

| Field      | Description                          |
|------------|--------------------------------------|
| `lokalId`  | Unique identifier (UUID)             |
| `romnr`    | Shelter room number                  |
| `plasser`  | Capacity (number of people)          |
| `adresse`  | Street address                       |
| `latitude` | WGS84 latitude (converted from UTM)  |
| `longitude`| WGS84 longitude (converted from UTM) |

**When conversion happens:**
- **Android:** At runtime during data download (`ShelterGeoJsonParser`)
- **PWA:** At build time (`scripts/fetch-shelters.ts`), output is pre-converted `shelters.json`

---

## Android App

**Language:** Kotlin · **Min SDK:** 26 (Android 8.0) · **Target SDK:** 35
**Build:** Gradle 8.7, AGP 8.5.2, KSP for Room annotation processing

### Package Structure

```
no.naiv.tilfluktsrom/
├── TilfluktsromApp.kt      # Application class (OSMDroid config)
├── MainActivity.kt          # Central UI controller (~870 lines)
├── data/
│   ├── Shelter.kt           # Room entity
│   ├── ShelterDatabase.kt   # Room database singleton
│   ├── ShelterDao.kt        # Data access object (Flow queries)
│   ├── ShelterRepository.kt # Repository: bundled seed + network refresh
│   ├── ShelterGeoJsonParser.kt  # GeoJSON ZIP → Shelter list
│   └── MapCacheManager.kt   # Offline map tile pre-caching
├── location/
│   ├── LocationProvider.kt  # GPS provider (flavor-specific)
│   └── ShelterFinder.kt     # Nearest N shelters by Haversine
├── ui/
│   ├── DirectionArrowView.kt    # Custom compass arrow View
│   ├── ShelterListAdapter.kt    # RecyclerView adapter for shelter list
│   ├── CivilDefenseInfoDialog.kt # Emergency instructions
│   └── AboutDialog.kt           # Privacy and copyright
└── util/
    ├── CoordinateConverter.kt   # UTM33N → WGS84 (Karney method)
    └── DistanceUtils.kt         # Haversine distance and bearing
```

Files under `location/` have separate implementations per build variant:
- `app/src/standard/java/` — Google Play Services variant
- `app/src/fdroid/java/` — AOSP-only variant

### Data Layer

**Storage:** Room (SQLite) with a single `shelters` table.

**Loading strategy (three-layer fallback):**

1. **Bundled asset** (`assets/shelters.json`): Pre-converted WGS84 data, loaded on first launch via `ShelterRepository.seedFromAsset()`. Marked as stale (timestamp=0) so a network refresh is attempted when possible.

2. **Room database**: ~556 shelters cached locally. Reactive updates via Kotlin Flow. Atomic refresh: `deleteAll()` + `insertAll()` in a single transaction.

3. **Network refresh**: Downloads the Geonorge ZIP via OkHttp, parses with `ShelterGeoJsonParser`. Staleness threshold: 7 days. Runs in the background; failure does not block the UI.

**GeoJSON parsing pipeline:**
```
OkHttp response stream
  → ZipInputStream (find .geojson entry, 10MB size limit)
  → JSON parsing (features array)
  → Per feature:
      → Extract UTM33N coordinates
      → CoordinateConverter.utm33nToWgs84()
      → Validate: lokalId not blank, plasser ≥ 0, within Norway bounds
      → Create Shelter entity (skip malformed features with warning)
```

### Location & Navigation

**LocationProvider** abstracts GPS access with two flavor implementations:

| Flavor    | Primary                          | Fallback          |
|-----------|----------------------------------|-------------------|
| standard  | FusedLocationProviderClient      | LocationManager   |
| fdroid    | LocationManager                  | —                 |

Both emit location updates via Kotlin Flow. Update interval: 5 seconds, fastest 2 seconds.

**ShelterFinder** takes the user's position and all shelters, computes Haversine distance and initial bearing for each, sorts by distance, and returns the N nearest (default: 3) as `ShelterWithDistance` objects.

### Compass System

**Sensor priority:**
1. Rotation Vector sensor (`TYPE_ROTATION_VECTOR`) — most accurate, single sensor
2. Accelerometer + Magnetometer — low-pass filtered (α=0.25) for smoothing
3. No compass available — error message shown

**Direction calculation:**
```
arrowAngle = shelterBearing − deviceHeading
```

**DirectionArrowView** is a custom View that draws:
- A large arrow rotated by `arrowAngle`, pointing toward the shelter
- An optional north indicator on the perimeter for compass calibration

### Map & Tile Caching

**Map library:** OSMDroid (OpenStreetMap, no Google dependency).

**Tile caching:** OSMDroid's built-in `SqlTileWriter` passively caches every tile loaded. `MapCacheManager` supplements this with active pre-caching:

- Pans the MapView across a 3×3 grid at zoom levels 10, 12, 14, 16
- 300ms delay between pans (respects OSM tile usage policy)
- Covers ~15km radius around the user
- Progress reported via callback for UI display

Tile cache stored in app-specific internal storage (`osmdroidBasePath`).

### Build Variants

```
productFlavors {
    standard { }   // Google Play Services + AOSP fallback
    fdroid   { }   // AOSP only
}
```

The `standard` flavor adds `com.google.android.gms:play-services-location`. Runtime detection via `GoogleApiAvailability` determines which code path runs.

Both flavors produce identical user experiences — `standard` achieves faster GPS fixes and better battery efficiency when Play Services are present.

### Deep Linking

**HTTPS App Links:** `https://tilfluktsrom.naiv.no/shelter/{lokalId}`

The domain is configured in one place: `DEEP_LINK_DOMAIN` in `build.gradle.kts` (exposed as `BuildConfig.DEEP_LINK_DOMAIN` and manifest placeholder `${deepLinkHost}`).

- `autoVerify="true"` on the HTTPS intent filter triggers Android's App Links verification at install time
- Verification requires `/.well-known/assetlinks.json` to be served by the PWA (in `pwa/public/.well-known/`)
- If the app is installed and verified, `/shelter/*` links open the app directly (no disambiguation dialog)
- If not installed, the link opens in the browser, where the PWA handles it

Share messages include the HTTPS URL, which SMS apps auto-link as a tappable URL.

---

## Progressive Web App

**Stack:** TypeScript, Vite 5, Leaflet, idb (IndexedDB wrapper), vite-plugin-pwa
**Package manager:** bun

### Module Structure

```
pwa/
├── index.html                    # SPA shell, CSP headers, semantic layout
├── vite.config.ts                # Build config, PWA plugin, tile caching rules
├── manifest.webmanifest          # PWA metadata and icons
├── scripts/
│   └── fetch-shelters.ts         # Build-time: download + convert shelter data
├── public/
│   └── data/shelters.json        # Pre-processed shelter data (build artifact)
└── src/
    ├── main.ts                   # Entry point, SW registration, locale init
    ├── app.ts                    # Main controller (~400 lines)
    ├── types.ts                  # Shelter, ShelterWithDistance, LatLon interfaces
    ├── data/
    │   ├── shelter-repository.ts # Fetch + IndexedDB storage
    │   └── shelter-db.ts         # IndexedDB wrapper (idb library)
    ├── location/
    │   ├── location-provider.ts  # navigator.geolocation wrapper
    │   ├── compass-provider.ts   # DeviceOrientationEvent (iOS/Android)
    │   └── shelter-finder.ts     # Haversine nearest-N calculation
    ├── ui/
    │   ├── map-view.ts           # Leaflet map, custom SVG markers
    │   ├── compass-view.ts       # Canvas-based direction arrow
    │   ├── shelter-list.ts       # Bottom sheet shelter list
    │   ├── loading-overlay.ts    # Modal spinner / cache prompt
    │   ├── about-dialog.ts       # Privacy, data info, cache clear
    │   ├── civil-defense-dialog.ts # DSB 5-step emergency guide
    │   └── status-bar.ts         # Status text and refresh button
    ├── cache/
    │   └── map-cache-manager.ts  # Tile pre-caching via programmatic panning
    ├── i18n/
    │   ├── i18n.ts               # Locale detection, string substitution
    │   ├── en.ts                 # English strings
    │   ├── nb.ts                 # Bokmål strings
    │   └── nn.ts                 # Nynorsk strings
    ├── util/
    │   └── distance-utils.ts     # Haversine distance, bearing, formatting
    └── main.css                  # Dark theme, Leaflet overrides, dialogs
```

### Build System

**Vite** bundles the app with content-hashed filenames for cache busting.

**Key configuration:**
- Base path: `./` (relative, deployable anywhere)
- `__BUILD_REVISION__` define: ISO timestamp injected at build time, used to invalidate service worker caches
- **vite-plugin-pwa**: Generates the service worker with Workbox. Precaches all static assets. Runtime-caches OSM tile requests with CacheFirst strategy (30-day expiry, max 5000 entries).

**Build-time data preprocessing:**
`scripts/fetch-shelters.ts` downloads the Geonorge ZIP, extracts the GeoJSON, converts UTM33N→WGS84, validates, and writes `public/data/shelters.json`. This means coordinate conversion is a build step, not a runtime cost.

```bash
bun run fetch-shelters  # Download and convert shelter data
bun run build           # TypeScript check + Vite build + SW generation
```

### Data Layer (PWA)

**Storage:** IndexedDB via the `idb` library.

**Schema:**
- Object store `shelters` (keyPath: `lokalId`) — full shelter records
- Object store `metadata` — `lastUpdate` timestamp

**Loading strategy:**
1. Check IndexedDB for cached shelters
2. If empty or stale (>7 days): fetch `shelters.json` (precached by service worker)
3. Replace all records in a single transaction
4. Reactive: UI reads from IndexedDB after load

Unlike the Android app, the PWA does not perform coordinate conversion at runtime — `shelters.json` is pre-converted at build time and served as a static asset.

### Location & Compass (PWA)

**Location:** `navigator.geolocation.watchPosition()` with high accuracy enabled, 10s maximum age, 30s timeout.

**Compass:** DeviceOrientationEvent with platform-specific handling:

| Platform       | API                          | Heading Source           |
|----------------|------------------------------|--------------------------|
| iOS Safari     | `deviceorientation`          | `webkitCompassHeading`   |
| Android Chrome | `deviceorientationabsolute`  | `(360 − alpha) % 360`   |

iOS 13+ requires an explicit permission request (`DeviceOrientationEvent.requestPermission()`), triggered by a user gesture.

Low-pass filter (smoothing factor 0.3) with 0/360° wraparound handling ensures fluid rotation.

### Map & Offline Tiles (PWA)

**Map library:** Leaflet with OpenStreetMap tiles. No CDN — Leaflet is bundled from `node_modules`.

**Custom markers:** SVG-based `divIcon` elements — orange house with "T" for shelters, blue circle for user location. Selected shelter uses a larger yellow marker.

**Auto-fit logic:** When a shelter is selected, the map fits bounds to show both user and shelter (30% padding), unless the user has manually panned or zoomed. A "reset view" button appears after manual interaction.

**Tile pre-caching:** `MapCacheManager` uses the same approach as the Android app — programmatically pans the map across a 3×3 grid at 4 zoom levels. The service worker's runtime cache intercepts and stores the tile requests. Cache location stored in localStorage (rounded to ~11km precision for privacy).

### Service Worker

Generated by vite-plugin-pwa (Workbox):

- **Precaching:** All static assets (JS, CSS, HTML, JSON, images) with content-hash versioning
- **Runtime caching:** OSM tiles from `{a,b,c}.tile.openstreetmap.org` with CacheFirst strategy, 30-day TTL, max 5000 entries
- **Navigation fallback:** Serves `index.html` for all navigation requests (SPA behavior)
- **Auto-update:** New service worker activates automatically; `controllerchange` event notifies the user

`main.ts` injects the build revision into the service worker context, ensuring each build invalidates stale caches. It also requests persistent storage (`navigator.storage.persist()`) to prevent the browser from evicting cached data.

### UI Components

The PWA uses no framework — all UI is vanilla TypeScript manipulating the DOM.

| Component                | Description                                              |
|--------------------------|----------------------------------------------------------|
| `app.ts`                 | Main controller: wires components, manages state         |
| `map-view.ts`            | Leaflet map with custom markers, auto-fit, interaction tracking |
| `compass-view.ts`        | Canvas-rendered arrow with north indicator, requestAnimationFrame |
| `shelter-list.ts`        | Bottom sheet with 3 nearest shelters, click selection     |
| `loading-overlay.ts`     | Modal: spinner during load, OK/Skip for cache prompt      |
| `about-dialog.ts`        | Privacy statement, data sources, "clear cache" button     |
| `civil-defense-dialog.ts`| DSB emergency instructions (5 steps)                     |
| `status-bar.ts`          | Data freshness indicator, refresh button                  |

Dialogs are created dynamically and implement focus management (save/restore previous focus, trap focus inside modal).

---

## Shared Algorithms

Both platforms implement these algorithms independently (no shared code), ensuring each platform has zero runtime dependencies on the other.

### UTM33N → WGS84 Conversion

**Algorithm:** Karney series expansion method.

Converts EUREF89 / UTM zone 33N (EPSG:25833) to WGS84 (EPSG:4326).

**Constants:**
- Semi-major axis: 6,378,137 m
- Flattening: 1/298.257223563
- UTM zone 33 central meridian: 15° E
- Scale factor: 0.9996
- False easting: 500,000 m

**Steps:**
1. Remove false easting/northing
2. Compute footprint latitude using series expansion
3. Apply iterative corrections for latitude and longitude
4. Add central meridian offset

**Validation:** Reject results outside Norway bounding box (57–72°N, 3–33°E).

**Implementations:**
- Android: `util/CoordinateConverter.kt` (runtime)
- PWA: `scripts/fetch-shelters.ts` (build-time)

### Haversine Distance & Bearing

**Distance** between two WGS84 points:
```
a = sin²(Δφ/2) + cos(φ₁) · cos(φ₂) · sin²(Δλ/2)
d = 2R · atan2(√a, √(1−a))
```
Where R = 6,371 km (Earth mean radius).

**Initial bearing** from point 1 to point 2:
```
θ = atan2(sin(Δλ) · cos(φ₂), cos(φ₁) · sin(φ₂) − sin(φ₁) · cos(φ₂) · cos(Δλ))
```
Result in degrees: 0° = north, 90° = east, 180° = south, 270° = west.

**Implementations:**
- Android: `util/DistanceUtils.kt`
- PWA: `util/distance-utils.ts`

---

## Offline Capability Summary

| Capability              | Android                          | PWA                                |
|-------------------------|----------------------------------|------------------------------------|
| Shelter data            | Room DB + bundled asset seed     | IndexedDB + precached JSON         |
| Map tiles               | OSMDroid SqlTileWriter + active pre-cache | Service worker CacheFirst + active pre-cache |
| GPS                     | Device hardware (no network)     | Device hardware (no network)       |
| Compass                 | Device sensors                   | DeviceOrientationEvent             |
| Distance/bearing math   | Local computation                | Local computation                  |
| Network required for    | Initial refresh, new tiles       | Initial page load, new tiles       |
| Staleness threshold     | 7 days                           | 7 days                             |

---

## Security & Privacy

- **No tracking:** No analytics, no telemetry, no external requests beyond Geonorge data and OSM tiles
- **No user accounts:** No registration, no login, no server-side storage
- **Location stays local:** GPS coordinates are used only for distance calculation and never transmitted
- **HTTPS enforced:** Android network security config restricts to TLS; PWA CSP headers restrict connections
- **ZIP bomb protection:** 10MB limit on uncompressed GeoJSON (Android parser)
- **CSP headers (PWA):** Restrictive Content-Security-Policy blocks external scripts; `img-src` allows only OSM tile servers
- **Input validation:** Shelter data validated at parse time (bounds check, required fields, non-negative capacity)
- **Minimal permissions:** Location only; no contacts, camera, storage (except legacy tile cache on Android ≤9)

---

## Internationalization

Both platforms support three languages:

| Language            | Android              | PWA            |
|---------------------|----------------------|----------------|
| English (default)   | `values/strings.xml` | `i18n/en.ts`   |
| Norwegian Bokmål    | `values-nb/strings.xml` | `i18n/nb.ts` |
| Norwegian Nynorsk   | `values-nn/strings.xml` | `i18n/nn.ts` |

**Android:** Uses Android's built-in locale resolution. Per-app language selection supported on Android 13+ via `locales_config.xml`.

**PWA:** Detects browser language (`navigator.language`). String substitution supports `%d` (number) and `%s` (string) placeholders. Falls back to English for unsupported locales.
