/** Norwegian Bokm\u00e5l strings. Ported from res/values-nb/strings.xml. */
export const nb: Record<string, string> = {
  app_name: 'Tilfluktsrom',

  status_ready: 'Klar',
  status_loading: 'Laster tilfluktsromdata\u2026',
  status_updating: 'Oppdaterer\u2026',
  status_offline: 'Frakoblet modus',
  status_shelters_loaded: '%d tilfluktsrom lastet',
  status_no_location: 'Venter p\u00e5 GPS\u2026',
  status_caching_map: 'Lagrer kart for frakoblet bruk\u2026',

  loading_shelters: 'Laster ned tilfluktsromdata\u2026',
  loading_map: 'Lagrer kartfliser\u2026',
  loading_map_explanation:
    'Forbereder frakoblet kart.\nKartet vil rulle kort for \u00e5 lagre omgivelsene dine.',
  loading_first_time: 'Gj\u00f8r klar for f\u00f8rste gangs bruk\u2026',

  shelter_capacity: '%d plasser',
  shelter_room_nr: 'Rom %d',
  nearest_shelter: 'N\u00e6rmeste tilfluktsrom',
  no_shelters: 'Ingen tilfluktsromdata tilgjengelig',

  action_refresh: 'Oppdater data',
  action_toggle_view: 'Bytt mellom kart og kompassvisning',
  action_skip: 'Hopp over',
  action_cache_ok: 'Lagre kart',
  action_cache_now: 'Lagre n\u00e5',
  warning_no_map_cache:
    'Ingen frakoblet kart lagret. Kartet krever internett.',

  permission_location_title: 'Posisjonstillatelse kreves',
  permission_location_message:
    'Denne appen trenger din posisjon for \u00e5 finne n\u00e6rmeste tilfluktsrom. Vennligst gi tilgang til posisjon.',
  permission_denied:
    'Posisjonstillatelse avsl\u00e5tt. Appen kan ikke finne tilfluktsrom i n\u00e6rheten uten den.',

  error_download_failed:
    'Kunne ikke laste ned tilfluktsromdata. Sjekk internettforbindelsen.',
  error_no_data_offline:
    'Ingen lagrede data tilgjengelig. Koble til internett for \u00e5 laste ned tilfluktsromdata.',
  update_success: 'Tilfluktsromdata oppdatert',
  update_failed: 'Oppdatering mislyktes \u2014 bruker lagrede data',
};
