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

  // About
  about_title: 'About Tilfluktsrom',
  about_description:
    'Tilfluktsrom helps you find the nearest public shelter in Norway. The app works offline after initial setup.',
  about_privacy_title: 'Privacy',
  about_privacy_body:
    'This app does not collect, transmit, or share any personal data. There are no analytics, tracking, or third-party services. Your GPS location is used only on your device to find nearby shelters and is never sent to any server.',
  about_data_title: 'Data sources',
  about_data_body:
    'Shelter data: Geonorge (Norwegian Mapping Authority). Map tiles: OpenStreetMap. Both are cached locally for offline use.',
  about_stored_title: 'Stored on your device',
  about_stored_body:
    'Shelter database (public data), map tiles for offline use, and map cache metadata. No data leaves your device except requests to download shelter data and map tiles.',
  about_copyright: 'Copyright © Ole-Morten Duesund',
  about_open_source: 'Open source — kode.naiv.no/olemd/tilfluktsrom',
  action_about: 'About',
  action_close: 'Close',
};
