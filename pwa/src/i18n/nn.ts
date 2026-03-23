/** Norwegian Nynorsk strings. Ported from res/values-nn/strings.xml. */
export const nn: Record<string, string> = {
  app_name: 'Tilfluktsrom',

  status_ready: 'Klar',
  status_loading: 'Lastar tilfluktsromdata\u2026',
  status_updating: 'Oppdaterer\u2026',
  status_offline: 'Fråkopla modus',
  status_shelters_loaded: '%d tilfluktsrom lasta',
  status_no_location: 'Ventar på GPS\u2026',
  status_caching_map: 'Lagrar kart for fråkopla bruk\u2026',

  loading_shelters: 'Lastar ned tilfluktsromdata\u2026',
  loading_map: 'Lagrar kartfliser\u2026',
  loading_map_explanation:
    'Førebur fråkopla kart.\nKartet vil rulle kort for å lagre omgjevnadene dine.',
  loading_first_time: 'Gjer klar for fyrste gongs bruk\u2026',

  shelter_capacity: '%d plassar',
  shelter_room_nr: 'Rom %d',
  nearest_shelter: 'Næraste tilfluktsrom',
  no_shelters: 'Ingen tilfluktsromdata tilgjengeleg',

  action_refresh: 'Oppdater data',
  action_toggle_view: 'Byt mellom kart og kompassvising',
  action_skip: 'Hopp over',
  action_cache_ok: 'Lagre kart',
  action_cache_now: 'Lagre no',
  warning_no_map_cache:
    'Ingen fråkopla kart lagra. Kartet krev internett.',

  permission_location_title: 'Posisjonsløyve krevst',
  permission_location_message:
    'Denne appen treng posisjonen din for å finne næraste tilfluktsrom. Ver venleg og gje tilgang til posisjon.',
  permission_denied:
    'Posisjonsløyve avslått. Appen kan ikkje finne tilfluktsrom i nærleiken utan det.',

  error_download_failed:
    'Kunne ikkje laste ned tilfluktsromdata. Sjekk internettilkoplinga.',
  error_no_data_offline:
    'Ingen lagra data tilgjengeleg. Kopla til internett for å laste ned tilfluktsromdata.',
  update_success: 'Tilfluktsromdata oppdatert',
  update_failed: 'Oppdatering mislukkast — brukar lagra data',

  // Tilgjenge
  direction_arrow_description: 'Retning til tilfluktsrom, %s unna',
  a11y_map: 'Kart',
  a11y_compass: 'Kompass',
  a11y_shelter_info: 'Tilfluktsrominfo',
  a11y_nearest_shelters: 'Nærmaste tilfluktsrom',

  // Om
  about_title: 'Om Tilfluktsrom',
  about_description:
    'Tilfluktsrom hjelper deg med å finne næraste offentlege tilfluktsrom i Noreg. Appen fungerer utan internett etter fyrste oppsett.',
  about_privacy_title: 'Personvern',
  about_privacy_body:
    'Denne appen samlar ikkje inn, sender eller deler nokon personopplysingar. Det finst ingen analyse, sporing eller tredjepartstenester. GPS-posisjonen din vert berre brukt lokalt på eininga di for å finne tilfluktsrom i nærleiken, og vert aldri sendt til nokon tenar.',
  about_data_title: 'Datakjelder',
  about_data_body:
    'Tilfluktsromdata: Geonorge (Kartverket). Kartfliser: OpenStreetMap. Begge vert lagra lokalt for fråkopla bruk.',
  about_stored_title: 'Lagra på eininga di',
  about_stored_body:
    'Tilfluktsromdatabase (offentlege data), kartfliser for fråkopla bruk og kartbuffer-metadata. Ingen data forlèt eininga di bortsett frå førespurnader om å laste ned tilfluktsromdata og kartfliser.',
  about_copyright: 'Opphavsrett © Ole-Morten Duesund',
  about_open_source: 'Open kjeldekode — kode.naiv.no/olemd/tilfluktsrom',
  action_about: 'Om',
  action_close: 'Lukk',
};
