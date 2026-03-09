# F-Droid submission notes

## Anti-features: Play Services dependency

The app includes `com.google.android.gms:play-services-location:21.3.0` for better location accuracy via `FusedLocationProviderClient`. However, this is **not a hard dependency**:

- The app checks for Play Services at runtime via `GoogleApiAvailability`
- If unavailable, it falls back to `LocationManager` (standard AOSP API)
- All core functionality (finding shelters, compass navigation, offline maps) works without Play Services

This was specifically designed to support degoogled devices (LineageOS, GrapheneOS, /e/OS).

### F-Droid build options

**Option A: Accept as-is with `NonFreeDep` anti-feature**
The app works fully without Play Services. Mark with `NonFreeDep` anti-feature.

**Option B: Build flavor without Play Services (recommended)**
Create a `fdroid` product flavor that excludes the Play Services dependency entirely. The fallback code paths already handle the absence — only the dependency and the Fused provider code need to be conditionally included.

## Metadata structure

```
fastlane/metadata/android/
├── en-US/          # English (default)
├── nb-NO/          # Norwegian Bokmål
└── nn-NO/          # Norwegian Nynorsk
```

Each locale contains `title.txt`, `short_description.txt`, `full_description.txt`, and `changelogs/` with per-versionCode files.

## Screenshots

Screenshots still need to be added to `images/` directories:
- `phoneScreenshots/` — at least 3 phone screenshots
- `featureGraphic.png` — 1024x500 feature graphic

## Build instructions

Standard Gradle build, no custom steps needed:

```
./gradlew assembleRelease
```
