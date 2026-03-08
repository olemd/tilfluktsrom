/** A public shelter (tilfluktsrom) with WGS84 coordinates. */
export interface Shelter {
  lokalId: string;
  romnr: number;
  plasser: number;
  adresse: string;
  latitude: number;
  longitude: number;
}

/** A shelter annotated with distance and bearing from a reference point. */
export interface ShelterWithDistance {
  shelter: Shelter;
  distanceMeters: number;
  bearingDegrees: number;
}

/** WGS84 latitude/longitude pair. */
export interface LatLon {
  latitude: number;
  longitude: number;
}
