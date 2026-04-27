/**
 * Main app controller — wires together all components.
 * Ported from MainActivity.kt.
 *
 * Key UX decision: selecting a shelter (initial or alternate) auto-fits the
 * map to show both user and shelter. Manual pan/zoom overrides this. A "reset
 * view" button re-fits when the user wants to return.
 */

import type { Shelter, ShelterWithDistance, LatLon } from './types';
import { t } from './i18n/i18n';
import { formatDistance, distanceMeters, bearingDegrees } from './util/distance-utils';
import { findNearest } from './location/shelter-finder';
import { DEEP_LINK_DOMAIN } from './config';
import * as repo from './data/shelter-repository';
import * as locationProvider from './location/location-provider';
import * as compassProvider from './location/compass-provider';
import * as mapView from './ui/map-view';
import * as compassView from './ui/compass-view';
import * as shelterList from './ui/shelter-list';
import * as statusBar from './ui/status-bar';
import * as loading from './ui/loading-overlay';
import * as mapCache from './cache/map-cache-manager';
import * as civilDefenseDialog from './ui/civil-defense-dialog';

const NEAREST_COUNT = 3;

let allShelters: Shelter[] = [];
let nearestShelters: ShelterWithDistance[] = [];
let selectedShelterIndex = 0;
let currentLocation: LatLon | null = null;
let deviceHeading = 0;
let isCompassMode = false;
let firstLocationFix = true;

// Track whether user manually selected a shelter (prevents auto-reselection
// on location updates)
let userSelectedShelter = false;
// Romnr (DSB room number) of the user-selected shelter — survives location
// updates that recompute nearestShelters (deep links, marker taps to a
// far-away shelter), and *also* survives a forceRefresh() that replaces
// every lokalId in the dataset. Romnr is the stable upstream business key;
// see ARCHITECTURE.md → "Deep link identifier" for rationale.
let selectedRomnr: number | null = null;

export async function init(): Promise<void> {
  applyA11yLabels();
  setupMap();
  setupCompass();
  setupShelterList();
  setupButtons();
  await loadData();
  handleDeepLink();
}

/** Set localized aria-labels and wire the about button. */
function applyA11yLabels(): void {
  document.getElementById('about-btn')?.setAttribute('aria-label', t('action_civil_defense_info'));
  document.getElementById('about-btn')?.addEventListener('click', () => {
    navigator.vibrate?.(10);
    civilDefenseDialog.showCivilDefenseInfo();
  });
  document.getElementById('map-container')?.setAttribute('aria-label', t('a11y_map'));
  document.getElementById('compass-container')?.setAttribute('aria-label', t('a11y_compass'));
  document.getElementById('bottom-sheet')?.setAttribute('aria-label', t('a11y_shelter_info'));
  document.getElementById('shelter-list')?.setAttribute('aria-label', t('a11y_nearest_shelters'));
  document.getElementById('refresh-btn')?.setAttribute('aria-label', t('action_refresh'));
  document.getElementById('share-btn')?.setAttribute('aria-label', t('action_share'));
  document.getElementById('toggle-fab')?.setAttribute('aria-label', t('action_toggle_view'));
}

function setupMap(): void {
  const container = document.getElementById('map-container')!;
  mapView.initMap(container, (shelter: Shelter) => {
    // Marker click — select this shelter (route through selectShelterByData
    // so a tap on a far-away marker also survives location updates).
    selectShelterByData(shelter);
  });
}

function setupCompass(): void {
  const container = document.getElementById('compass-container')!;
  compassView.initCompass(container);
}

function setupShelterList(): void {
  const container = document.getElementById('shelter-list')!;
  shelterList.initShelterList(container, (index: number) => {
    userSelectedShelter = true;
    selectedShelterIndex = index;
    selectedRomnr = nearestShelters[index]?.shelter.romnr ?? null;
    updateSelectedShelter(true);
  });
}

function setupButtons(): void {
  // Toggle map/compass
  const toggleFab = document.getElementById('toggle-fab')!;
  toggleFab.addEventListener('click', async () => {
    navigator.vibrate?.(10);
    isCompassMode = !isCompassMode;

    const mapContainer = document.getElementById('map-container')!;
    const compassContainer = document.getElementById('compass-container')!;

    if (isCompassMode) {
      // Request compass permission on first toggle (iOS requirement).
      // On denial we stay in map mode and surface a status message so the
      // user understands why nothing happened — silent reverting is the
      // failure mode the Android GPS-denied banner was designed to avoid.
      const granted = await compassProvider.requestPermission();
      if (!granted) {
        isCompassMode = false;
        statusBar.setStatus(t('compass_permission_denied'));
        return;
      }
      mapContainer.style.display = 'none';
      compassContainer.classList.add('active');
      compassView.resize();
      toggleFab.textContent = '\uD83D\uDDFA\uFE0F'; // map emoji
      compassProvider.startCompass(onHeadingUpdate);
    } else {
      compassContainer.classList.remove('active');
      mapContainer.style.display = 'block';
      toggleFab.textContent = '\uD83E\uDDED'; // compass emoji

      // Invalidate map size after showing
      const map = mapView.getMap();
      if (map) setTimeout(() => map.invalidateSize(), 100);

      compassProvider.stopCompass();
    }
  });

  // Refresh button
  statusBar.onRefreshClick(forceRefresh);

  // Share button — emits the same HTTPS deep link the Android app uses,
  // so a recipient with the app installed (and verified App Links) opens
  // the shelter natively, otherwise it opens in the PWA.
  document.getElementById('share-btn')?.addEventListener('click', () => {
    navigator.vibrate?.(10);
    shareSelectedShelter();
  });

  // Cache retry button
  const cacheRetryBtn = document.getElementById('cache-retry-btn')!;
  cacheRetryBtn.textContent = t('action_cache_now');
  cacheRetryBtn.addEventListener('click', () => {
    navigator.vibrate?.(10);
    if (currentLocation && navigator.onLine) {
      startCaching(currentLocation.latitude, currentLocation.longitude);
    }
  });

  // Reset view button
  const resetBtn = document.getElementById('reset-view-btn')!;
  resetBtn.addEventListener('click', () => {
    navigator.vibrate?.(10);
    const selected = nearestShelters[selectedShelterIndex] ?? null;
    mapView.resetView(selected, currentLocation);
    resetBtn.classList.remove('visible');
  });

  // Show reset button when user pans/zooms
  const mapContainer = document.getElementById('map-container')!;
  const map = mapView.getMap();
  if (map) {
    map.on('dragstart', showResetButton);
    map.on('zoomstart', showResetButton);
  }

  // No-cache banner text
  const noCacheText = document.getElementById('no-cache-text')!;
  noCacheText.textContent = t('warning_no_map_cache');
}

function showResetButton(): void {
  const btn = document.getElementById('reset-view-btn');
  if (btn) btn.classList.add('visible');
}

async function loadData(): Promise<void> {
  const hasData = await repo.hasCachedData();

  if (!hasData) {
    if (!navigator.onLine) {
      statusBar.setStatus(t('error_no_data_offline'));
      return;
    }
    loading.showLoading(t('loading_shelters'));
    const success = await repo.refreshData();
    loading.hideLoading();

    if (!success) {
      statusBar.setStatus(t('error_download_failed'));
      return;
    }
  }

  allShelters = await repo.getAllShelters();
  statusBar.setStatus(t('status_shelters_loaded', allShelters.length));
  mapView.updateShelterMarkers(allShelters);

  // Start location
  startLocationUpdates();

  // Background refresh if stale
  if (hasData && (await repo.isDataStale()) && navigator.onLine) {
    const success = await repo.refreshData();
    if (success) {
      allShelters = await repo.getAllShelters();
      statusBar.setStatus(t('update_success'));
      mapView.updateShelterMarkers(allShelters);
      if (currentLocation) updateNearestShelters(currentLocation);
    }
  }
}

function startLocationUpdates(): void {
  if (!locationProvider.isGeolocationAvailable()) {
    statusBar.setStatus(t('permission_denied'));
    return;
  }

  statusBar.setStatus(t('status_no_location'));

  locationProvider.startWatching(
    (location: LatLon) => {
      currentLocation = location;
      mapView.updateUserLocation(location);
      updateNearestShelters(location);

      // Cache map on first fix
      if (firstLocationFix) {
        firstLocationFix = false;
        if (
          !mapCache.hasCacheForLocation(location.latitude, location.longitude) &&
          navigator.onLine
        ) {
          promptMapCache(location.latitude, location.longitude);
        }
      }
    },
    () => {
      statusBar.setStatus(t('permission_denied'));
    },
  );
}

function updateNearestShelters(location: LatLon): void {
  if (allShelters.length === 0) return;

  nearestShelters = findNearest(
    allShelters,
    location.latitude,
    location.longitude,
    NEAREST_COUNT,
  );

  if (userSelectedShelter && selectedRomnr !== null) {
    // Preserve the user's chosen shelter (deep link, marker click, list tap)
    // even when it isn't in the geographic top-N. We re-add it with a
    // freshly computed distance/bearing so the arrow stays correct.
    // Match by romnr — survives a forceRefresh() that replaces lokalIds.
    const inList = nearestShelters.findIndex(
      (s) => s.shelter.romnr === selectedRomnr,
    );
    if (inList >= 0) {
      selectedShelterIndex = inList;
    } else {
      const shelter = allShelters.find((s) => s.romnr === selectedRomnr);
      if (shelter) {
        nearestShelters.unshift({
          shelter,
          distanceMeters: distanceMeters(
            location.latitude, location.longitude,
            shelter.latitude, shelter.longitude,
          ),
          bearingDegrees: bearingDegrees(
            location.latitude, location.longitude,
            shelter.latitude, shelter.longitude,
          ),
        });
        selectedShelterIndex = 0;
      } else {
        // Selected shelter no longer exists in the dataset (e.g. DSB
        // decommissioned it). Fall back to nearest.
        selectedRomnr = null;
        userSelectedShelter = false;
        selectedShelterIndex = 0;
      }
    }
  } else {
    selectedShelterIndex = 0;
  }

  shelterList.updateList(nearestShelters, selectedShelterIndex);
  updateSelectedShelter(false);

  statusBar.setStatus(t('status_shelters_loaded', allShelters.length));
}

/**
 * Update all UI to reflect the currently selected shelter.
 * @param isUserAction Whether this was triggered by user shelter selection
 *   (if true, we auto-fit the map; if false, only auto-fit when not panned)
 */
function updateSelectedShelter(isUserAction: boolean): void {
  if (nearestShelters.length === 0) return;

  const selected = nearestShelters[selectedShelterIndex];
  if (!selected) return;

  const dist = formatDistance(selected.distanceMeters);

  // Update bottom sheet
  const addrEl = document.getElementById('selected-shelter-address')!;
  const detailsEl = document.getElementById('selected-shelter-details')!;
  addrEl.textContent = selected.shelter.adresse;
  detailsEl.textContent = [
    dist,
    t('shelter_capacity', selected.shelter.plasser),
    t('shelter_room_nr', selected.shelter.romnr),
  ].join(' \u00B7 ');

  // Update mini arrow
  const miniArrow = document.getElementById('mini-arrow')!;
  miniArrow.setAttribute('aria-label', t('direction_arrow_description', dist));
  updateMiniArrow(selected.bearingDegrees - deviceHeading);

  // Update compass view
  document.getElementById('compass-distance')!.textContent = dist;
  document.getElementById('compass-address')!.textContent =
    selected.shelter.adresse;
  compassView.setDirection(selected.bearingDegrees - deviceHeading);
  compassView.setNorthAngle(-deviceHeading);

  // Update shelter list selection
  shelterList.updateList(nearestShelters, selectedShelterIndex);

  // Update map: highlight selected and optionally fit view
  if (isUserAction) {
    // User explicitly selected a shelter — fit view to show it
    mapView.resetView(selected, currentLocation);
    // Hide the reset button since we just fit the view
    document.getElementById('reset-view-btn')?.classList.remove('visible');
  }
  mapView.selectShelter(selected, currentLocation);
}

function onHeadingUpdate(heading: number): void {
  deviceHeading = heading;
  if (nearestShelters.length === 0) return;

  const selected = nearestShelters[selectedShelterIndex];
  const angle = selected.bearingDegrees - heading;

  compassView.setDirection(angle);
  compassView.setNorthAngle(-heading);
  updateMiniArrow(angle);
}

/** Draw the mini direction arrow in the bottom sheet. */
function updateMiniArrow(angleDeg: number): void {
  const canvas = document.getElementById('mini-arrow') as HTMLCanvasElement;
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  const w = canvas.width;
  const h = canvas.height;
  const cx = w / 2;
  const cy = h / 2;
  const size = Math.min(w, h) * 0.4;

  ctx.clearRect(0, 0, w, h);
  ctx.save();
  ctx.translate(cx, cy);
  ctx.rotate((angleDeg * Math.PI) / 180);

  ctx.beginPath();
  ctx.moveTo(0, -size);
  ctx.lineTo(size * 0.5, size * 0.3);
  ctx.lineTo(size * 0.15, size * 0.1);
  ctx.lineTo(size * 0.15, size * 0.7);
  ctx.lineTo(-size * 0.15, size * 0.7);
  ctx.lineTo(-size * 0.15, size * 0.1);
  ctx.lineTo(-size * 0.5, size * 0.3);
  ctx.closePath();

  ctx.fillStyle = '#FF6B35';
  ctx.fill();
  ctx.strokeStyle = '#FFFFFF';
  ctx.lineWidth = 2;
  ctx.stroke();

  ctx.restore();
}

function promptMapCache(lat: number, lon: number): void {
  loading.showCachePrompt(
    t('loading_map_explanation'),
    () => startCaching(lat, lon),
    () => {
      // User skipped — show warning banner
      document.getElementById('no-cache-banner')?.classList.add('visible');
    },
  );
}

async function startCaching(lat: number, lon: number): Promise<void> {
  document.getElementById('no-cache-banner')?.classList.remove('visible');
  loading.showLoading(t('loading_map'));

  const map = mapView.getMap();
  if (!map) {
    loading.hideLoading();
    return;
  }

  await mapCache.cacheMapArea(map, lat, lon, (progress) => {
    loading.updateLoadingText(
      `${t('loading_map')} (${Math.round(progress * 100)}%)`,
    );
  });

  loading.hideLoading();
  statusBar.setStatus(t('status_shelters_loaded', allShelters.length));
}

async function forceRefresh(): Promise<void> {
  if (!navigator.onLine) {
    statusBar.setStatus(t('error_download_failed'));
    return;
  }

  statusBar.setStatus(t('status_updating'));
  const success = await repo.refreshData();
  if (success) {
    allShelters = await repo.getAllShelters();
    mapView.updateShelterMarkers(allShelters);
    if (currentLocation) updateNearestShelters(currentLocation);
    statusBar.setStatus(t('update_success'));
  } else {
    statusBar.setStatus(t('update_failed'));
  }
}

/**
 * Handle /shelter/{romnr} deep links.
 * Called after loadData() so allShelters is populated.
 *
 * Path component is romnr (DSB room number), not lokalId — see
 * selectedRomnr comment above for the upstream-stability rationale.
 */
function handleDeepLink(): void {
  const match = window.location.pathname.match(/^\/shelter\/(\d+)$/);
  if (!match) return;

  const romnr = parseInt(match[1], 10);

  // Clean the URL so refresh doesn't re-trigger
  window.history.replaceState({}, '', '/');

  const shelter = allShelters.find((s) => s.romnr === romnr);
  if (!shelter) {
    statusBar.setStatus(t('error_shelter_not_found'));
    return;
  }

  selectShelterByData(shelter);
}

/**
 * Select a specific shelter, even if it's not in the current nearest-3 list.
 * Used for deep link targets, marker taps, and list taps. The selection is
 * remembered via selectedLokalId so subsequent location updates preserve it.
 */
function selectShelterByData(shelter: Shelter): void {
  userSelectedShelter = true;
  selectedRomnr = shelter.romnr;

  const existingIdx = nearestShelters.findIndex(
    (s) => s.shelter.romnr === shelter.romnr,
  );

  if (existingIdx >= 0) {
    selectedShelterIndex = existingIdx;
  } else {
    // Compute distance/bearing if we have a location, otherwise NaN signals
    // "unknown distance" — same convention as MainActivity.kt.
    const dist = currentLocation
      ? distanceMeters(
          currentLocation.latitude, currentLocation.longitude,
          shelter.latitude, shelter.longitude,
        )
      : NaN;
    const bearing = currentLocation
      ? bearingDegrees(
          currentLocation.latitude, currentLocation.longitude,
          shelter.latitude, shelter.longitude,
        )
      : NaN;

    nearestShelters.unshift({
      shelter,
      distanceMeters: dist,
      bearingDegrees: bearing,
    });
    selectedShelterIndex = 0;
    shelterList.updateList(nearestShelters, selectedShelterIndex);
  }

  updateSelectedShelter(true);
}

/**
 * Share the currently selected shelter. Uses the Web Share API when
 * available (mobile browsers, installed PWAs) and falls back to copying
 * the same body to the clipboard. Body format mirrors share_body in the
 * Android strings.xml so the two clients produce equivalent messages.
 */
async function shareSelectedShelter(): Promise<void> {
  const selected = nearestShelters[selectedShelterIndex];
  if (!selected || !selected.shelter) {
    statusBar.setStatus(t('share_no_shelter'));
    return;
  }

  const shelter = selected.shelter;
  const lat = shelter.latitude.toFixed(6);
  const lon = shelter.longitude.toFixed(6);
  // Path component is romnr (stable DSB business key), not lokalId. The
  // upstream Geonorge GeoJSON re-rolls lokalId on every export, so a
  // lokalId-keyed link breaks the moment either party refreshes their
  // dataset. Romnr is unique (verified 556/556) and stable across exports.
  const deepLink = `https://${DEEP_LINK_DOMAIN}/shelter/${shelter.romnr}`;

  const subject = t('share_subject');
  const body = [
    `${subject}: ${shelter.adresse}`,
    t('shelter_capacity', shelter.plasser),
    `${lat}, ${lon}`,
    `geo:${lat},${lon}`,
    deepLink,
  ].join('\n');

  if (navigator.share) {
    try {
      await navigator.share({ title: subject, text: body, url: deepLink });
      return;
    } catch (err) {
      // AbortError means the user cancelled — silent. Anything else falls
      // through to the clipboard fallback below.
      if ((err as DOMException)?.name === 'AbortError') return;
    }
  }

  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(body);
      statusBar.setStatus(t('share_copied'));
    } catch {
      statusBar.setStatus(t('share_no_shelter'));
    }
  }
}
