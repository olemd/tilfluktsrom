/** Norwegian Bokmål strings. Ported from res/values-nb/strings.xml. */
export const nb: Record<string, string> = {
  app_name: 'Tilfluktsrom',

  status_ready: 'Klar',
  status_loading: 'Laster tilfluktsromdata\u2026',
  status_updating: 'Oppdaterer\u2026',
  status_offline: 'Frakoblet modus',
  status_shelters_loaded: '%d tilfluktsrom lastet',
  status_no_location: 'Venter på GPS\u2026',
  status_caching_map: 'Lagrer kart for frakoblet bruk\u2026',

  loading_shelters: 'Laster ned tilfluktsromdata\u2026',
  loading_map: 'Lagrer kartfliser\u2026',
  loading_map_explanation:
    'Forbereder frakoblet kart.\nKartet vil rulle kort for å lagre omgivelsene dine.',
  loading_first_time: 'Gjør klar for første gangs bruk\u2026',

  shelter_capacity: '%d plasser',
  shelter_room_nr: 'Rom %d',
  nearest_shelter: 'Nærmeste tilfluktsrom',
  no_shelters: 'Ingen tilfluktsromdata tilgjengelig',

  action_refresh: 'Oppdater data',
  action_toggle_view: 'Bytt mellom kart og kompassvisning',
  action_skip: 'Hopp over',
  action_cache_ok: 'Lagre kart',
  action_cache_now: 'Lagre nå',
  warning_no_map_cache:
    'Ingen frakoblet kart lagret. Kartet krever internett.',

  permission_location_title: 'Posisjonstillatelse kreves',
  permission_location_message:
    'Denne appen trenger din posisjon for å finne nærmeste tilfluktsrom. Vennligst gi tilgang til posisjon.',
  permission_denied:
    'Posisjonstillatelse avslått. Appen kan ikke finne tilfluktsrom i nærheten uten den.',

  error_download_failed:
    'Kunne ikke laste ned tilfluktsromdata. Sjekk internettforbindelsen.',
  error_no_data_offline:
    'Ingen lagrede data tilgjengelig. Koble til internett for å laste ned tilfluktsromdata.',
  update_success: 'Tilfluktsromdata oppdatert',
  update_failed: 'Oppdatering mislyktes — bruker lagrede data',

  // Tilgjengelighet
  direction_arrow_description: 'Retning til tilfluktsrom, %s unna',
  a11y_map: 'Kart',
  a11y_compass: 'Kompass',
  a11y_shelter_info: 'Tilfluktsrominfo',
  a11y_nearest_shelters: 'Nærmeste tilfluktsrom',

  // Om
  about_title: 'Om Tilfluktsrom',
  about_description:
    'Tilfluktsrom hjelper deg med å finne nærmeste offentlige tilfluktsrom i Norge. Appen fungerer uten internett etter første oppsett.',
  about_privacy_title: 'Personvern',
  about_privacy_body:
    'Denne appen samler ikke inn, sender eller deler noen personopplysninger. Det finnes ingen analyse, sporing eller tredjepartstjenester. GPS-posisjonen din brukes bare lokalt på enheten din for å finne tilfluktsrom i nærheten, og sendes aldri til noen server.',
  about_data_title: 'Datakilder',
  about_data_body:
    'Tilfluktsromdata: DSB (Direktoratet for samfunnssikkerhet og beredskap), distribuert via Geonorge. Kartfliser: OpenStreetMap. Begge lagres lokalt for frakoblet bruk.',
  about_stored_title: 'Lagret på enheten din',
  about_stored_body:
    'Tilfluktsromdatabase (offentlige data), kartfliser for frakoblet bruk og kartbuffer-metadata. Ingen data forlater enheten din bortsett fra forespørsler om å laste ned tilfluktsromdata og kartfliser.',
  about_copyright: 'Opphavsrett © Ole-Morten Duesund',
  about_open_source: 'Åpen kildekode — kode.naiv.no/olemd/tilfluktsrom',
  action_about: 'Om',
  action_close: 'Lukk',
  action_clear_cache: 'Slett lagrede data',
  cache_cleared: 'Alle lagrede data slettet',
};
