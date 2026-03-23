/** English strings — default locale. Ported from res/values/strings.xml. */
export const en: Record<string, string> = {
  app_name: 'Tilfluktsrom',

  // Status
  status_ready: 'Ready',
  status_loading: 'Loading shelter data\u2026',
  status_updating: 'Updating\u2026',
  status_offline: 'Offline mode',
  status_shelters_loaded: '%d shelters loaded',
  status_no_location: 'Waiting for GPS\u2026',
  status_caching_map: 'Caching map for offline use\u2026',

  // Loading
  loading_shelters: 'Downloading shelter data\u2026',
  loading_map: 'Caching map tiles\u2026',
  loading_map_explanation:
    'Preparing offline map.\nThe map will scroll briefly to cache your surroundings.',
  loading_first_time: 'Setting up for first use\u2026',

  // Shelter info
  shelter_capacity: '%d places',
  shelter_room_nr: 'Room %d',
  nearest_shelter: 'Nearest shelter',
  no_shelters: 'No shelter data available',

  // Actions
  action_refresh: 'Refresh data',
  action_toggle_view: 'Toggle map/compass view',
  action_skip: 'Skip',
  action_cache_ok: 'Cache map',
  action_cache_now: 'Cache now',
  warning_no_map_cache: 'No offline map cached. Map requires internet.',

  // Permissions
  permission_location_title: 'Location permission required',
  permission_location_message:
    'This app needs your location to find the nearest shelter. Please grant location access.',
  permission_denied:
    'Location permission denied. The app cannot find nearby shelters without it.',

  // Errors
  error_download_failed:
    'Could not download shelter data. Check your internet connection.',
  error_no_data_offline:
    'No cached data available. Connect to the internet to download shelter data.',
  update_success: 'Shelter data updated',
  update_failed: 'Update failed \u2014 using cached data',

  // Accessibility
  direction_arrow_description: 'Direction to shelter, %s away',
  a11y_map: 'Map',
  a11y_compass: 'Compass',
  a11y_shelter_info: 'Shelter info',
  a11y_nearest_shelters: 'Nearest shelters',
};
