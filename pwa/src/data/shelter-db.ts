/**
 * IndexedDB wrapper for shelter data using the idb library.
 * Ported from Room database in the Android app.
 */

import { openDB, type IDBPDatabase } from 'idb';
import type { Shelter } from '../types';

const DB_NAME = 'tilfluktsrom';
const DB_VERSION = 1;
const SHELTER_STORE = 'shelters';
const META_STORE = 'metadata';

const META_KEY_LAST_UPDATE = 'lastUpdate';

type TilfluktsromDB = IDBPDatabase;

let dbPromise: Promise<TilfluktsromDB> | null = null;

function getDb(): Promise<TilfluktsromDB> {
  if (!dbPromise) {
    dbPromise = openDB(DB_NAME, DB_VERSION, {
      upgrade(db) {
        if (!db.objectStoreNames.contains(SHELTER_STORE)) {
          db.createObjectStore(SHELTER_STORE, { keyPath: 'lokalId' });
        }
        if (!db.objectStoreNames.contains(META_STORE)) {
          db.createObjectStore(META_STORE);
        }
      },
    });
  }
  return dbPromise;
}

/** Get all cached shelters. */
export async function getAllShelters(): Promise<Shelter[]> {
  const db = await getDb();
  return db.getAll(SHELTER_STORE);
}

/** Check if any shelter data is cached. */
export async function hasCachedData(): Promise<boolean> {
  const db = await getDb();
  const count = await db.count(SHELTER_STORE);
  return count > 0;
}

/** Replace all shelter data (clear + insert). */
export async function replaceShelters(shelters: Shelter[]): Promise<void> {
  const db = await getDb();
  const tx = db.transaction(SHELTER_STORE, 'readwrite');
  await tx.store.clear();
  for (const shelter of shelters) {
    await tx.store.put(shelter);
  }
  await tx.done;

  // Update last-update timestamp
  const metaTx = db.transaction(META_STORE, 'readwrite');
  await metaTx.store.put(Date.now(), META_KEY_LAST_UPDATE);
  await metaTx.done;
}

/** Check if cached data is older than the given max age (default 7 days). */
export async function isDataStale(
  maxAgeMs = 7 * 24 * 60 * 60 * 1000,
): Promise<boolean> {
  const db = await getDb();
  const lastUpdate = await db.get(META_STORE, META_KEY_LAST_UPDATE);
  if (lastUpdate == null) return true;
  return Date.now() - (lastUpdate as number) > maxAgeMs;
}
