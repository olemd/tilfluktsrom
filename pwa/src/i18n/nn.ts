/** Norwegian Nynorsk strings. Ported from res/values-nn/strings.xml. */
export const nn: Record<string, string> = {
  app_name: 'Tilfluktsrom',

  status_ready: 'Klar',
  status_loading: 'Lastar tilfluktsromdata\u2026',
  status_updating: 'Oppdaterer\u2026',
  status_offline: 'Fr\u00e5kopla modus',
  status_shelters_loaded: '%d tilfluktsrom lasta',
  status_no_location: 'Ventar p\u00e5 GPS\u2026',
  status_caching_map: 'Lagrar kart for fr\u00e5kopla bruk\u2026',

  loading_shelters: 'Lastar ned tilfluktsromdata\u2026',
  loading_map: 'Lagrar kartfliser\u2026',
  loading_map_explanation:
    'F\u00f8rebur fr\u00e5kopla kart.\nKartet vil rulle kort for \u00e5 lagre omgjevnadene dine.',
  loading_first_time: 'Gjer klar for fyrste gongs bruk\u2026',

  shelter_capacity: '%d plassar',
  shelter_room_nr: 'Rom %d',
  nearest_shelter: 'N\u00e6raste tilfluktsrom',
  no_shelters: 'Ingen tilfluktsromdata tilgjengeleg',

  action_refresh: 'Oppdater data',
  action_toggle_view: 'Byt mellom kart og kompassvising',
  action_skip: 'Hopp over',
  action_cache_ok: 'Lagre kart',
  action_cache_now: 'Lagre no',
  warning_no_map_cache:
    'Ingen fr\u00e5kopla kart lagra. Kartet krev internett.',

  permission_location_title: 'Posisjonsløyve krevst',
  permission_location_message:
    'Denne appen treng posisjonen din for \u00e5 finne n\u00e6raste tilfluktsrom. Ver venleg og gje tilgang til posisjon.',
  permission_denied:
    'Posisjonsløyve avsl\u00e5tt. Appen kan ikkje finne tilfluktsrom i n\u00e6rleiken utan det.',

  error_download_failed:
    'Kunne ikkje laste ned tilfluktsromdata. Sjekk internettilkoplinga.',
  error_no_data_offline:
    'Ingen lagra data tilgjengeleg. Kopla til internett for \u00e5 laste ned tilfluktsromdata.',
  update_success: 'Tilfluktsromdata oppdatert',
  update_failed: 'Oppdatering mislukkast \u2014 brukar lagra data',

  // Tilgjenge
  direction_arrow_description: 'Retning til tilfluktsrom, %s unna',
  a11y_map: 'Kart',
  a11y_compass: 'Kompass',
  a11y_shelter_info: 'Tilfluktsrominfo',
  a11y_nearest_shelters: 'Nærmaste tilfluktsrom',
};
