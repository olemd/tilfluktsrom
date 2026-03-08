# Tilfluktsrom

Finn nærmeste offentlige tilfluktsrom i Norge. Appen er bygd for nødsituasjoner og fungerer uten internett etter første gangs bruk.

## Funksjoner

- **Finn nærmeste tilfluktsrom** — viser de tre nærmeste tilfluktsrommene med avstand og kapasitet
- **Kompassnavigasjon** — retningspil som peker mot valgt tilfluktsrom
- **Frakoblet kart** — kartfliser lagres automatisk for bruk uten nett
- **Velg fritt** — trykk på en hvilken som helst markør i kartet for å navigere dit
- **Flerspråklig** — engelsk, bokmål og nynorsk

## Plattformer

### Android-app (`app/`)

Native Kotlin-app med OSMDroid-kart og Room-database.

- **Minstekrav:** Android 8.0 (API 26)
- **Bygg:** `./gradlew assembleDebug`
- **Installer:** `adb install app/build/outputs/apk/debug/app-debug.apk`

### Nettapp / PWA (`pwa/`) — **ikke testet**

> **OBS:** PWA-versjonen er under utvikling og er foreløpig ikke manuelt testet i nettleser. Koden kompilerer og enhetstester passerer, men den er ikke verifisert i praksis.

Progressiv nettapp med Vite, TypeScript og Leaflet. Kan installeres på alle enheter via nettleseren.

- **Avhengigheter:** `bun install`
- **Hent tilfluktsromdata:** `bun run fetch-shelters`
- **Utviklingsserver:** `bun run dev`
- **Bygg for produksjon:** `bun run build`
- **Kjør tester:** `bun test`

## Datakilde

Tilfluktsromdata lastes ned fra [Geonorge](https://www.geonorge.no/) som GeoJSON i UTM33N-projeksjon (EPSG:25833). Koordinatene konverteres til WGS84 (bredde-/lengdegrad) for visning i kartet.

Datasettet inneholder ca. 556 offentlige tilfluktsrom med adresse, romnummer og kapasitet (antall plasser).

## Arkitektur

```
tilfluktsrom/
├── app/                    # Android-app (Kotlin)
│   └── src/main/
│       ├── java/.../
│       │   ├── data/       # Room-database, nedlasting, GeoJSON-parser
│       │   ├── location/   # GPS, nærmeste tilfluktsrom
│       │   ├── ui/         # Retningspil, liste-adapter
│       │   └── util/       # UTM→WGS84-konvertering, avstandsberegning
│       └── res/            # Layout, strenger (en/nb/nn), ikoner
├── pwa/                    # Nettapp (TypeScript)
│   ├── src/
│   │   ├── data/           # IndexedDB-cache
│   │   ├── location/       # GPS, kompass
│   │   ├── ui/             # Kart, kompass, liste
│   │   ├── cache/          # Kartfliser for frakoblet bruk
│   │   └── i18n/           # Oversettelser
│   └── scripts/            # Bygg-tidsskript for datakonvertering
└── CLAUDE.md               # Prosjektdokumentasjon for AI-assistert utvikling
```

## Frakoblet bruk

Appen er designet etter «offline-first»-prinsippet:

1. **Tilfluktsromdata** lagres lokalt etter første nedlasting (Room / IndexedDB)
2. **Kartfliser** caches automatisk for området rundt brukeren
3. **GPS og kompass** fungerer uten internett
4. Data oppdateres automatisk i bakgrunnen når det er eldre enn 7 dager

## Sikkerhet

- All nettverkstrafikk går over HTTPS
- Tilfluktsromdata valideres ved parsing (koordinater innenfor Norge, gyldige felt)
- Databaseoppdateringer er atomiske (transaksjon) for å unngå datatap
- Ingen persondata lagres — kun tilfluktsromdata og kartfliser

## Lisens

Tilfluktsromdata er åpne data fra Geonorge / Direktoratet for samfunnssikkerhet og beredskap (DSB).
Kartfliser fra OpenStreetMap er lisensiert under [ODbL](https://opendatacommons.org/licenses/odbl/).
