/**
 * Leaflet map component with shelter markers and user location.
 *
 * Tracks user interaction: if the user manually pans or zooms, auto-fitting
 * is suppressed until they explicitly reset the view.
 */

import L from 'leaflet';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';
import type { Shelter, ShelterWithDistance, LatLon } from '../types';
import { t } from '../i18n/i18n';

// Fix Leaflet default icon paths (broken by bundlers) — use bundled assets
// eslint-disable-next-line @typescript-eslint/no-explicit-any
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconUrl: markerIcon,
  iconRetinaUrl: markerIcon2x,
  shadowUrl: markerShadow,
});

const DEFAULT_ZOOM = 14;
const DEFAULT_CENTER: L.LatLngExpression = [59.9, 10.7]; // Central Norway

const shelterIcon = L.divIcon({
  className: 'shelter-marker',
  html: `<svg viewBox="0 0 24 24" width="28" height="28">
    <path d="M12 2L2 12h3v8h14v-8h3L12 2z" fill="#FF6B35" stroke="#fff" stroke-width="1.5"/>
    <text x="12" y="17" text-anchor="middle" fill="#fff" font-size="8" font-weight="bold">T</text>
  </svg>`,
  iconSize: [28, 28],
  iconAnchor: [14, 28],
  popupAnchor: [0, -28],
});

const selectedIcon = L.divIcon({
  className: 'shelter-marker selected',
  html: `<svg viewBox="0 0 24 24" width="36" height="36">
    <path d="M12 2L2 12h3v8h14v-8h3L12 2z" fill="#FFC107" stroke="#fff" stroke-width="1.5"/>
    <text x="12" y="17" text-anchor="middle" fill="#1A1A2E" font-size="8" font-weight="bold">T</text>
  </svg>`,
  iconSize: [36, 36],
  iconAnchor: [18, 36],
  popupAnchor: [0, -36],
});

let map: L.Map | null = null;
let userMarker: L.CircleMarker | null = null;
let shelterMarkers: L.Marker[] = [];
let selectedMarkerId: string | null = null;

// Track whether user has manually interacted with the map
let userHasInteracted = false;

// Callbacks
let onShelterSelect: ((shelter: Shelter) => void) | null = null;

/** Initialize the Leaflet map in the given container. */
export function initMap(
  container: HTMLElement,
  onSelect: (shelter: Shelter) => void,
): L.Map {
  onShelterSelect = onSelect;

  map = L.map(container, {
    zoomControl: true,
    attributionControl: true,
  }).setView(DEFAULT_CENTER, DEFAULT_ZOOM);

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution:
      '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    maxZoom: 19,
  }).addTo(map);

  // Track user interaction (pan/zoom by hand)
  map.on('dragstart', () => {
    userHasInteracted = true;
  });
  map.on('zoomstart', (e: L.LeafletEvent) => {
    // Only flag as user interaction if it's not programmatic
    // Leaflet doesn't distinguish, so we use a flag set before programmatic calls
    if (!programmaticMove) {
      userHasInteracted = true;
    }
  });

  return map;
}

// Flag to distinguish programmatic moves from user moves
let programmaticMove = false;

/** Update the user's location marker on the map. */
export function updateUserLocation(location: LatLon): void {
  if (!map) return;

  const latlng = L.latLng(location.latitude, location.longitude);

  if (userMarker) {
    userMarker.setLatLng(latlng);
  } else {
    userMarker = L.circleMarker(latlng, {
      radius: 8,
      fillColor: '#4285F4',
      fillOpacity: 1,
      color: '#fff',
      weight: 3,
    }).addTo(map);
  }
}

/** Add markers for all shelters. */
export function updateShelterMarkers(shelters: Shelter[]): void {
  if (!map) return;

  // Remove old markers
  for (const m of shelterMarkers) {
    map.removeLayer(m);
  }
  shelterMarkers = [];
  selectedMarkerId = null;

  for (const shelter of shelters) {
    const marker = L.marker([shelter.latitude, shelter.longitude], {
      icon: shelterIcon,
    })
      .bindPopup(
        `<strong>${shelter.adresse}</strong><br>${t('shelter_capacity', shelter.plasser)} &middot; ${t('shelter_room_nr', shelter.romnr)}`,
      )
      .on('click', () => {
        onShelterSelect?.(shelter);
      });

    marker.addTo(map);
    // Store shelter ID on the marker for highlighting
    (marker as L.Marker & { _shelterLokalId: string })._shelterLokalId =
      shelter.lokalId;
    shelterMarkers.push(marker);
  }
}

/** Highlight the selected shelter and optionally fit the view. */
export function selectShelter(
  selected: ShelterWithDistance,
  userLocation: LatLon | null,
): void {
  if (!map) return;

  // Update marker icons
  for (const m of shelterMarkers) {
    const mid = (m as L.Marker & { _shelterLokalId: string })._shelterLokalId;
    if (mid === selected.shelter.lokalId) {
      m.setIcon(selectedIcon);
      selectedMarkerId = mid;
    } else if (mid === selectedMarkerId || selectedMarkerId === null) {
      m.setIcon(shelterIcon);
    }
  }

  // Auto-fit view to show user + shelter, unless user has manually panned/zoomed
  if (!userHasInteracted) {
    fitToShelter(selected, userLocation);
  }
}

/** Fit the map view to show both user location and selected shelter. */
export function fitToShelter(
  selected: ShelterWithDistance,
  userLocation: LatLon | null,
): void {
  if (!map) return;

  programmaticMove = true;
  const shelterLatLng = L.latLng(
    selected.shelter.latitude,
    selected.shelter.longitude,
  );

  if (userLocation) {
    const userLatLng = L.latLng(userLocation.latitude, userLocation.longitude);
    const bounds = L.latLngBounds([userLatLng, shelterLatLng]);
    map.fitBounds(bounds.pad(0.3), { animate: true });
  } else {
    map.setView(shelterLatLng, DEFAULT_ZOOM, { animate: true });
  }

  // Reset the flag after the animation completes
  setTimeout(() => {
    programmaticMove = false;
  }, 500);
}

/**
 * Reset the view: clear user interaction flag and re-fit to
 * show user + selected shelter.
 */
export function resetView(
  selected: ShelterWithDistance | null,
  userLocation: LatLon | null,
): void {
  userHasInteracted = false;
  if (selected) {
    fitToShelter(selected, userLocation);
  } else if (userLocation) {
    programmaticMove = true;
    map?.setView(
      L.latLng(userLocation.latitude, userLocation.longitude),
      DEFAULT_ZOOM,
      { animate: true },
    );
    setTimeout(() => {
      programmaticMove = false;
    }, 500);
  }
}

/** Get the Leaflet map instance (for cache manager). */
export function getMap(): L.Map | null {
  return map;
}

/** Destroy the map. */
export function destroyMap(): void {
  map?.remove();
  map = null;
  userMarker = null;
  shelterMarkers = [];
}
