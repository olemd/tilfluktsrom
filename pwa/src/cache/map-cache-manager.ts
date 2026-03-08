/**
 * Map tile cache manager.
 * Seeds tiles by programmatically panning a Leaflet map across a grid.
 * Tiles are cached by the service worker (CacheFirst strategy on OSM URLs).
 *
 * Ported from MapCacheManager.kt — same 3×3 grid × 4 zoom levels approach.
 */

import type L from 'leaflet';

const CACHE_RADIUS_DEGREES = 0.15; // ~15km
const CACHE_ZOOM_LEVELS = [10, 12, 14, 16];
const GRID_SIZE = 3;
const PAN_DELAY_MS = 400; // Slightly longer than Android (300ms) for web

const STORAGE_KEY = 'mapCache';

interface CacheMeta {
  lat: number;
  lon: number;
  radius: number;
  complete: boolean;
}

function getCacheMeta(): CacheMeta | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function saveCacheMeta(meta: CacheMeta): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(meta));
}

/** Check if we have a tile cache covering the given location. */
export function hasCacheForLocation(lat: number, lon: number): boolean {
  const meta = getCacheMeta();
  if (!meta?.complete) return false;

  const margin = meta.radius * 0.3;
  return (
    Math.abs(lat - meta.lat) < meta.radius - margin &&
    Math.abs(lon - meta.lon) < meta.radius - margin
  );
}

/**
 * Seed the tile cache by panning the map across the surrounding area.
 * The service worker's CacheFirst strategy on OSM tile URLs handles storage.
 *
 * Reports progress via callback (0 to 1).
 */
export async function cacheMapArea(
  map: L.Map,
  lat: number,
  lon: number,
  onProgress?: (progress: number) => void,
): Promise<boolean> {
  try {
    const totalSteps = CACHE_ZOOM_LEVELS.length * GRID_SIZE * GRID_SIZE;
    let step = 0;

    const originalZoom = map.getZoom();
    const originalCenter = map.getCenter();

    for (const zoom of CACHE_ZOOM_LEVELS) {
      map.setZoom(zoom);

      for (let row = 0; row < GRID_SIZE; row++) {
        for (let col = 0; col < GRID_SIZE; col++) {
          const panLat =
            lat -
            CACHE_RADIUS_DEGREES +
            (2 * CACHE_RADIUS_DEGREES * row) / (GRID_SIZE - 1);
          const panLon =
            lon -
            CACHE_RADIUS_DEGREES +
            (2 * CACHE_RADIUS_DEGREES * col) / (GRID_SIZE - 1);

          map.setView([panLat, panLon], zoom, { animate: false });
          map.invalidateSize();

          step++;
          onProgress?.(step / totalSteps);

          await sleep(PAN_DELAY_MS);
        }
      }
    }

    // Restore original view
    map.setView(originalCenter, originalZoom, { animate: false });

    saveCacheMeta({
      lat,
      lon,
      radius: CACHE_RADIUS_DEGREES,
      complete: true,
    });

    return true;
  } catch {
    return false;
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
