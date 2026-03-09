# Data Sources

Centralized list of all external data sources used by the app. This is the authoritative reference for attribution, licensing, and usage context.

## Sources

| Source | Provider | URL | Usage | License | Used in |
|--------|----------|-----|-------|---------|---------|
| Shelter data (GeoJSON) | Geonorge / DSB | [nedlasting.geonorge.no/...](https://nedlasting.geonorge.no/geonorge/Samfunnssikkerhet/TilfluktsromOffentlige/GeoJSON/Samfunnssikkerhet_0000_Norge_25833_TilfluktsromOffentlige_GeoJSON.zip) | Shelter locations, capacity, addresses (~556 shelters) | [NLOD 2.0](https://data.norge.no/nlod/no/2.0) | `app/.../data/ShelterRepository.kt`, `pwa/scripts/fetch-shelters.ts` |
| Map tiles | OpenStreetMap | [tile.openstreetmap.org](https://tile.openstreetmap.org) | Offline-capable map display | [ODbL](https://opendatacommons.org/licenses/odbl/) | `app/` via OSMDroid, `pwa/src/ui/map-view.ts` via Leaflet |
| Civil defense guidelines | DSB (Direktoratet for samfunnssikkerhet og beredskap) | [dsb.no/sikkerhverdag/egenberedskap](https://www.dsb.no/sikkerhverdag/egenberedskap/) | Emergency instructions shown in the civil defense info dialog | Norwegian public sector information | `app/.../res/values/strings.xml` (civil_defense_* strings) |

## Notes

- **Shelter data** is downloaded as a ZIP containing GeoJSON in EPSG:25833 (UTM33N) projection. The app converts coordinates to WGS84 at parse time.
- **Map tiles** are cached locally by OSMDroid (Android) and the service worker (PWA) for offline use. OpenStreetMap's [tile usage policy](https://operations.osmfoundation.org/policies/tiles/) applies.
- **Civil defense guidelines** are adapted from official DSB recommendations, not quoted verbatim. Content is available in English, Bokmal, and Nynorsk.
