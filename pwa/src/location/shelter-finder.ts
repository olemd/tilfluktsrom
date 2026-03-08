/**
 * Finds the N nearest shelters to a given location.
 * Ported from ShelterFinder.kt in the Android app.
 */

import type { Shelter, ShelterWithDistance } from '../types';
import { distanceMeters, bearingDegrees } from '../util/distance-utils';

/**
 * Find the N nearest shelters to the given location.
 * Returns results sorted by distance (nearest first).
 */
export function findNearest(
  shelters: Shelter[],
  latitude: number,
  longitude: number,
  count = 3,
): ShelterWithDistance[] {
  return shelters
    .map((shelter) => ({
      shelter,
      distanceMeters: distanceMeters(
        latitude,
        longitude,
        shelter.latitude,
        shelter.longitude,
      ),
      bearingDegrees: bearingDegrees(
        latitude,
        longitude,
        shelter.latitude,
        shelter.longitude,
      ),
    }))
    .sort((a, b) => a.distanceMeters - b.distanceMeters)
    .slice(0, count);
}
