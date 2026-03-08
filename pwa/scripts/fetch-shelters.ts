/**
 * Build-time script: downloads the Geonorge shelter ZIP, extracts GeoJSON,
 * converts UTM33N (EPSG:25833) → WGS84 (EPSG:4326), and outputs shelters.json.
 *
 * Run: bunx tsx scripts/fetch-shelters.ts
 *
 * Coordinate conversion ported from CoordinateConverter.kt (Karney method).
 * GeoJSON parsing ported from ShelterGeoJsonParser.kt.
 */

import { mkdirSync, existsSync } from 'fs';
import { writeFile } from 'fs/promises';
import { join, dirname } from 'path';
import { inflateRawSync } from 'zlib';

const DOWNLOAD_URL =
  'https://nedlasting.geonorge.no/geonorge/Samfunnssikkerhet/TilfluktsromOffentlige/GeoJSON/Samfunnssikkerhet_0000_Norge_25833_TilfluktsromOffentlige_GeoJSON.zip';

const OUTPUT_PATH = join(dirname(import.meta.url.replace('file://', '')), '..', 'public', 'data', 'shelters.json');

// --- UTM33N → WGS84 conversion (Karney series expansion) ---

const A = 6378137.0;                  // WGS84 semi-major axis (m)
const F = 1.0 / 298.257223563;        // flattening
const E2 = 2 * F - F * F;             // eccentricity squared
const K0 = 0.9996;                    // UTM scale factor
const FALSE_EASTING = 500000.0;
const ZONE_33_CENTRAL_MERIDIAN = 15.0; // degrees

interface LatLon {
  latitude: number;
  longitude: number;
}

function utm33nToWgs84(easting: number, northing: number): LatLon {
  const x = easting - FALSE_EASTING;
  const y = northing;

  const e1 = (1 - Math.sqrt(1 - E2)) / (1 + Math.sqrt(1 - E2));
  const m = y / K0;
  const mu = m / (A * (1 - E2 / 4 - (3 * E2 * E2) / 64 - (5 * E2 ** 3) / 256));

  // Footprint latitude via series expansion
  const phi1 =
    mu +
    (3 * e1 / 2 - 27 * e1 ** 3 / 32) * Math.sin(2 * mu) +
    (21 * e1 ** 2 / 16 - 55 * e1 ** 4 / 32) * Math.sin(4 * mu) +
    (151 * e1 ** 3 / 96) * Math.sin(6 * mu) +
    (1097 * e1 ** 4 / 512) * Math.sin(8 * mu);

  const sinPhi1 = Math.sin(phi1);
  const cosPhi1 = Math.cos(phi1);
  const tanPhi1 = Math.tan(phi1);

  const n1 = A / Math.sqrt(1 - E2 * sinPhi1 * sinPhi1);
  const t1 = tanPhi1 * tanPhi1;
  const c1 = (E2 / (1 - E2)) * cosPhi1 * cosPhi1;
  const r1 = (A * (1 - E2)) / (1 - E2 * sinPhi1 * sinPhi1) ** 1.5;
  const d = x / (n1 * K0);

  const ep2 = E2 / (1 - E2);

  const lat =
    phi1 -
    (n1 * tanPhi1 / r1) *
      (d ** 2 / 2 -
        (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * ep2) * d ** 4 / 24 +
        (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * ep2 - 3 * c1 * c1) * d ** 6 / 720);

  const lon =
    (d -
      (1 + 2 * t1 + c1) * d ** 3 / 6 +
      (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * ep2 + 24 * t1 * t1) * d ** 5 / 120) /
    cosPhi1;

  return {
    latitude: (lat * 180) / Math.PI,
    longitude: ZONE_33_CENTRAL_MERIDIAN + (lon * 180) / Math.PI,
  };
}

// --- ZIP extraction + GeoJSON parsing ---

interface GeoJsonFeature {
  geometry: { coordinates: number[] };
  properties: {
    lokalId?: string;
    romnr?: number;
    plasser?: number;
    adresse?: string;
  };
}

interface GeoJsonRoot {
  features: GeoJsonFeature[];
}

interface Shelter {
  lokalId: string;
  romnr: number;
  plasser: number;
  adresse: string;
  latitude: number;
  longitude: number;
}

async function downloadAndExtractZip(url: string): Promise<string> {
  console.log(`Downloading ${url}...`);
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Download failed: ${response.status} ${response.statusText}`);
  }

  const buffer = Buffer.from(await response.arrayBuffer());

  return extractGeoJsonFromZipBuffer(buffer);
}

function extractGeoJsonFromZipBuffer(zipBuffer: Buffer): string {
  // ZIP local file header signature: PK\x03\x04
  // We need to find and extract the .geojson/.json file.
  // Since Node has no built-in ZIP reader, let's parse the central directory.

  let offset = 0;
  const files: { name: string; compressedData: Buffer; method: number; uncompressedSize: number }[] = [];

  while (offset < zipBuffer.length - 4) {
    const sig = zipBuffer.readUInt32LE(offset);
    if (sig !== 0x04034b50) break; // Not a local file header

    const method = zipBuffer.readUInt16LE(offset + 8);
    const compressedSize = zipBuffer.readUInt32LE(offset + 18);
    const uncompressedSize = zipBuffer.readUInt32LE(offset + 22);
    const nameLen = zipBuffer.readUInt16LE(offset + 26);
    const extraLen = zipBuffer.readUInt16LE(offset + 28);
    const name = zipBuffer.subarray(offset + 30, offset + 30 + nameLen).toString('utf8');
    const dataStart = offset + 30 + nameLen + extraLen;
    const compressedData = zipBuffer.subarray(dataStart, dataStart + compressedSize);

    files.push({ name, compressedData, method, uncompressedSize });
    offset = dataStart + compressedSize;
  }

  const geoFile = files.find(
    (f) => f.name.endsWith('.geojson') || f.name.endsWith('.json'),
  );
  if (!geoFile) {
    throw new Error('No GeoJSON file found in ZIP archive');
  }

  console.log(`Extracting ${geoFile.name} (${geoFile.compressedData.length} bytes compressed)...`);

  if (geoFile.method === 0) {
    // Stored (no compression)
    return geoFile.compressedData.toString('utf8');
  } else if (geoFile.method === 8) {
    // Deflated
    const inflated = inflateRawSync(geoFile.compressedData);
    return inflated.toString('utf8');
  } else {
    throw new Error(`Unsupported compression method: ${geoFile.method}`);
  }
}

function parseGeoJson(json: string): Shelter[] {
  const root: GeoJsonRoot = JSON.parse(json);
  const shelters: Shelter[] = [];

  for (let i = 0; i < root.features.length; i++) {
    const feature = root.features[i];
    const coords = feature.geometry.coordinates;
    const props = feature.properties;

    const easting = coords[0];
    const northing = coords[1];
    const latLon = utm33nToWgs84(easting, northing);

    shelters.push({
      lokalId: props.lokalId ?? `unknown-${i}`,
      romnr: props.romnr ?? 0,
      plasser: props.plasser ?? 0,
      adresse: props.adresse ?? '',
      latitude: Math.round(latLon.latitude * 1e6) / 1e6,
      longitude: Math.round(latLon.longitude * 1e6) / 1e6,
    });
  }

  return shelters;
}

// --- Main ---

async function main() {
  const outputDir = dirname(OUTPUT_PATH);
  if (!existsSync(outputDir)) {
    mkdirSync(outputDir, { recursive: true });
  }

  const geoJson = await downloadAndExtractZip(DOWNLOAD_URL);
  const shelters = parseGeoJson(geoJson);

  console.log(`Parsed ${shelters.length} shelters`);

  // Validate: check a few coordinates are in Norway's range
  const invalid = shelters.filter(
    (s) => s.latitude < 57 || s.latitude > 72 || s.longitude < 4 || s.longitude > 32,
  );
  if (invalid.length > 0) {
    console.warn(`WARNING: ${invalid.length} shelters have coordinates outside Norway's range`);
  }

  await writeFile(OUTPUT_PATH, JSON.stringify(shelters, null, 2), 'utf8');
  console.log(`Wrote ${OUTPUT_PATH} (${shelters.length} shelters)`);
}

main().catch((err) => {
  console.error('Fatal error:', err);
  process.exit(1);
});
