# Standing on Shoulders

## How many people made this app possible?

Tilfluktsrom is a small Android app — about 900 source files — that helps
Norwegians find their nearest public shelter in an emergency. One person built
it in under a day. But that was only possible because of the accumulated work of
roughly **100,000–120,000 identifiable people**, spanning decades, countries, and
disciplines.

This document traces the human effort behind every layer of the stack.

---

## Layer 0: Physical Infrastructure — GPS & Sensors (~10,500 people)

| Component | Role | Est. people |
|---|---|---|
| GPS constellation | 31 satellites, maintained by US Space Force | ~5,000 |
| Magnetometer/compass sensors | Enable the direction arrow to point at shelters | ~500 |
| ARM architecture | The CPU instruction set running every Android device | ~5,000 |

Before a single line of code runs, hardware designed by tens of thousands of
engineers must be in orbit, in your pocket, and on the circuit board.

## Layer 1: Internet & Standards (~5,250 people)

| Component | Role | Est. people |
|---|---|---|
| TCP/IP, DNS, HTTP, TLS | The protocols that carry shelter data from server to phone | ~5,000 |
| GeoJSON specification | The format the shelter data is published in (IETF RFC 7946) | ~50 |
| EPSG / coordinate reference systems | The math behind UTM33N → WGS84 coordinate conversion | ~200 |

## Layer 2: Operating Systems & Runtimes (~27,200 people)

| Component | Role | Est. people |
|---|---|---|
| Linux kernel | Foundation of Android; ~20,000 documented unique contributors | ~20,000 |
| Android (AOSP) | The mobile OS, built on Linux by Google + community | ~5,000 |
| JVM / OpenJDK + Java | The language runtime Kotlin compiles to | ~2,000 |
| ART (Android Runtime) | Replaced Dalvik; runs the compiled bytecode | ~200 |

## Layer 3: Programming Languages (~1,200 people)

| Language | Origin | Est. people |
|---|---|---|
| Kotlin | JetBrains (Czech Republic/Russia) + community | ~500 |
| TypeScript | Microsoft + community (for the PWA) | ~500 |
| Groovy / Kotlin DSL | Gradle build scripts | ~200 |

## Layer 4: Build Tools & Dev Infrastructure (~5,400 people)

| Tool | Role | Est. people |
|---|---|---|
| Gradle | Build automation | ~500 |
| Android Gradle Plugin | Android-specific build pipeline | ~200 |
| KSP (Kotlin Symbol Processing) | Code generation for Room database | ~100 |
| R8 / ProGuard | Release minification and optimization | ~100 |
| Vite | PWA bundler | ~800 |
| Bun | Package manager and JS runtime | ~400 |
| Git | Version control | ~1,500 |
| Android Studio / IntelliJ | IDE (JetBrains + Google) | ~1,500 |
| Maven Central, Google Maven, npm | Package registry infrastructure | ~300 |

## Layer 5: Libraries — Android App (~2,550 people)

| Library | What it does | Est. people |
|---|---|---|
| AndroidX (Core, AppCompat, Activity, Lifecycle) | UI and app architecture foundation | ~800 |
| Material Design | Visual design language, research, and components | ~500 |
| ConstraintLayout | Flexible screen layouts | ~100 |
| Room | Type-safe SQLite wrapper for the shelter cache | ~200 |
| WorkManager | Periodic home screen widget updates | ~150 |
| Kotlinx Coroutines | Async data loading without blocking the UI | ~200 |
| OkHttp (Square) | Downloads the GeoJSON ZIP from Geonorge | ~200 |
| OSMDroid | Offline OpenStreetMap rendering | ~150 |
| Play Services Location | FusedLocationProvider for precise GPS | ~200 |
| SQLite | The embedded database engine | ~50 |

## Layer 6: Libraries — PWA (~1,350 people)

| Library | Role | Est. people |
|---|---|---|
| Leaflet | Interactive web maps (created in Ukraine) | ~800 |
| leaflet.offline | Offline tile caching | ~20 |
| idb | IndexedDB wrapper for offline storage | ~30 |
| vite-plugin-pwa | Service worker and Workbox integration | ~100 |
| Vitest | Test framework | ~400 |

## Layer 7: Data — The Content That Makes It Useful (~56,000 people)

| Source | Role | Est. people |
|---|---|---|
| OpenStreetMap | Global map data; ~2M registered mappers, ~10,000+ active in Norway | ~50,000 |
| Kartverket / Geonorge | Norwegian Mapping Authority; national geodata infrastructure | ~800 |
| DSB (Direktoratet for samfunnssikkerhet og beredskap) | Created and maintains the public shelter registry | ~200 |
| The shelter builders | Construction, engineering, civil defense planning since the Cold War | ~5,000+ |

The app's data exists because of Cold War civil defense planning. The shelters
were built in the 1950s–80s, digitized by DSB, published via Geonorge's open
data mandate — a chain of decisions spanning 70 years that now fits in a 320 KB
GeoJSON file.

## Layer 8: AI-Assisted Development (~6,000 people)

| Component | Role | Est. people |
|---|---|---|
| Anthropic / Claude | Researchers, engineers, safety team | ~1,000 |
| ML research lineage | Transformers, attention, RLHF, scaling laws — across academia & industry | ~5,000 |
| Training data | The collective written output of humanity | incalculable |

## Layer 9: Distribution (~500 people)

| Component | Role | Est. people |
|---|---|---|
| F-Droid | Open-source app store infrastructure and review | ~300 |
| Fastlane | Metadata and screenshot tooling | ~200 |

---

## Summary

| Layer | People |
|---|---|
| Physical infrastructure (GPS, ARM, sensors) | ~10,500 |
| Internet & standards | ~5,250 |
| Operating systems & runtimes | ~27,200 |
| Programming languages | ~1,200 |
| Build tools & dev infrastructure | ~5,400 |
| Direct libraries (Android) | ~2,550 |
| Direct libraries (PWA) | ~1,350 |
| Data (maps, shelters, geodesy) | ~56,000 |
| AI-assisted development | ~6,000 |
| Distribution | ~500 |
| **Conservative total** | **~116,000** |

This is conservative. It excludes:

- The millions of OSM mappers globally whose edits feed the tile rendering pipeline
- Hardware manufacturing (semiconductor fabs, device assembly — millions of workers)
- The educators who taught all these people their craft
- The civil defense planners who decided Norway needed public shelters
- The mathematicians behind Haversine, UTM projections, and geodesy going back centuries

Including OpenStreetMap's full contributor base and hardware, the number crosses
**2 million** easily.

---

## Perspective

For every line of application code, roughly 100,000 people made the tools, data,
and infrastructure that line depends on. No single company, country, or
organization could have built this stack alone. Linux (Finland → global), Kotlin
(Czech Republic/Russia → JetBrains), OSM (UK → global), GPS (US military →
civilian), Leaflet (Ukraine), SQLite (US, public domain) — this emergency app is
a product of genuine global cooperation.

The fact that one person can build a working, offline-capable emergency app in
under a day is arguably one of the most remarkable expressions of accumulated
human cooperation — and almost none of it was coordinated by any central
authority.
