/**
 * Geolocation wrapper using navigator.geolocation.watchPosition.
 * Provides a callback-based API for continuous location updates.
 */

import type { LatLon } from '../types';

export type LocationCallback = (location: LatLon) => void;
export type ErrorCallback = (error: GeolocationPositionError) => void;

let watchId: number | null = null;

/** Start watching the user's location. */
export function startWatching(
  onLocation: LocationCallback,
  onError?: ErrorCallback,
): void {
  if (watchId !== null) stopWatching();

  watchId = navigator.geolocation.watchPosition(
    (pos) => {
      onLocation({
        latitude: pos.coords.latitude,
        longitude: pos.coords.longitude,
      });
    },
    (err) => {
      onError?.(err);
    },
    {
      enableHighAccuracy: true,
      maximumAge: 10_000,
      timeout: 30_000,
    },
  );
}

/** Stop watching location. */
export function stopWatching(): void {
  if (watchId !== null) {
    navigator.geolocation.clearWatch(watchId);
    watchId = null;
  }
}

/** Check if geolocation is available. */
export function isGeolocationAvailable(): boolean {
  return 'geolocation' in navigator;
}
