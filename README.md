# Tilfluktsrom

Finn nærmeste offentlige tilfluktsrom i Norge. Appen er bygd for nødsituasjoner og fungerer uten internett etter første gangs bruk.

<p align="center">
  <img src="fastlane/metadata/android/nb-NO/images/phoneScreenshots/1_map_view.png" width="200" alt="Kartvisning med tilfluktsrom i Bergen sentrum" />
  <img src="fastlane/metadata/android/nb-NO/images/phoneScreenshots/2_shelter_selected.png" width="200" alt="Valt tilfluktsrom med avstand og kapasitet" />
  <img src="fastlane/metadata/android/nb-NO/images/phoneScreenshots/3_compass_view.png" width="200" alt="Kompassnavigasjon mot tilfluktsrom" />
  <img src="fastlane/metadata/android/nb-NO/images/phoneScreenshots/4_civil_defense_info.png" width="200" alt="Sivilforsvarsinfo: kva du skal gjere om alarmen går" />
</p>

## Slik fungerer appen

**Kartvisning** — Appen viser alle 556 offentlige tilfluktsrom i Norge på eit OpenStreetMap-kart. Dei tre nærmaste tilfluktsromma visast i botnen med avstand, kapasitet og romnummer. Trykk på ei kart-markering eller eit listelement for å velje eit tilfluktsrom.

**Kompassnavigasjon** — Trykk på kompassknappen for å byte til retningspil-visning. Ein stor pil peikar mot det valde tilfluktsrommet, med avstand i meter eller kilometer. Fungerer utan internett — berre GPS og kompassensor.

**Sivilforsvarsinfo** — Trykk på info-knappen for å sjå trinn-for-trinn-rettleiing frå DSB om kva du skal gjere når alarmen går: viktig melding-signal, flyalarm, finn dekning, lytt til NRK på DAB-radio, og faren over.

## Funksjoner

- **Finn nærmaste tilfluktsrom** — viser dei tre nærmaste med avstand og kapasitet
- **Kompassnavigasjon** — retningspil som peikar mot valt tilfluktsrom
- **Frakobla kart** — kartfliser lagrast automatisk for bruk utan nett
- **Velg fritt** — trykk på ei markering i kartet for å navigere dit
- **Del tilfluktsrom** — send adresse, kapasitet og koordinatar til andre
- **Sivilforsvarsinfo** — rettleiing frå DSB om kva du skal gjere når alarmen går
- **Hjemmeskjerm-widget** — viser nærmaste tilfluktsrom utan å opne appen
- **Fleirspråkleg** — engelsk, bokmål og nynorsk

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

## Personvern

Appen samlar ikkje inn persondata og har ingen analyse- eller sporingstenester. Posisjonsdata vert berre brukt lokalt på eininga for å finne nærmaste tilfluktsrom. Sjå [PRIVACY.md](PRIVACY.md) for fullstendig personvernerklæring.

## Opphavsrett

Copyright (c) Ole-Morten Duesund <olemd@glemt.net>

## Lisens

Kildekoden er lisensiert under [Mozilla Public License 2.0](LICENSE).

Appen bruker opne data og tenester frå fleire kjelder. Sjå [SOURCES.md](SOURCES.md) for ei fullstendig oversikt over datakjelder, URL-ar og lisensar.

## Sjå også

- [STANDING_ON_SHOULDERS.md](STANDING_ON_SHOULDERS.md) — estimat over dei ~116 000 menneska som har gjort denne appen mogleg
