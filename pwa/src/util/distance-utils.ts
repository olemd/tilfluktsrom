/**
 * Haversine distance and initial bearing calculations.
 * Ported from DistanceUtils.kt in the Android app.
 */

const EARTH_RADIUS_METERS = 6371000;

/** Degrees to radians. */
function toRad(deg: number): number {
  return (deg * Math.PI) / 180;
}

/** Radians to degrees. */
function toDeg(rad: number): number {
  return (rad * 180) / Math.PI;
}

/**
 * Calculate distance in meters between two WGS84 points
 * using the Haversine formula.
 */
export function distanceMeters(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number,
): number {
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/**
 * Calculate initial bearing (0=north, clockwise) from point 1 to point 2.
 * Returns degrees in range [0, 360).
 */
export function bearingDegrees(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number,
): number {
  const phi1 = toRad(lat1);
  const phi2 = toRad(lat2);
  const dLambda = toRad(lon2 - lon1);
  const y = Math.sin(dLambda) * Math.cos(phi2);
  const x =
    Math.cos(phi1) * Math.sin(phi2) -
    Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLambda);
  return (toDeg(Math.atan2(y, x)) + 360) % 360;
}

/**
 * Format distance for display: meters if <1km, km with one decimal otherwise.
 */
export function formatDistance(meters: number): string {
  if (meters < 1000) {
    return `${Math.round(meters)} m`;
  }
  return `${(meters / 1000).toFixed(1)} km`;
}
