/**
 * Repository that manages shelter data: fetches pre-processed JSON,
 * caches in IndexedDB, and handles staleness checks.
 *
 * Unlike the Android app, no ZIP handling or coordinate conversion
 * is needed at runtime — the data is pre-processed at build time.
 */

import type { Shelter } from '../types';
import {
  getAllShelters,
  hasCachedData,
  replaceShelters,
  isDataStale,
} from './shelter-db';

const SHELTERS_JSON_PATH = './data/shelters.json';

/** Fetch shelters.json and cache in IndexedDB. Returns success. */
export async function refreshData(): Promise<boolean> {
  try {
    const response = await fetch(SHELTERS_JSON_PATH);
    if (!response.ok) return false;

    const shelters: Shelter[] = await response.json();
    await replaceShelters(shelters);
    return true;
  } catch {
    return false;
  }
}

export { getAllShelters, hasCachedData, isDataStale };
