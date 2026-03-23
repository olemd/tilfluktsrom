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

  // Sivilforsvar
  action_civil_defense_info: 'Sivilforsvarsinformasjon',
  civil_defense_title: 'Hva du skal gjøre hvis alarmen går',
  civil_defense_step1_title: '1. Viktig melding-signalet',
  civil_defense_step1_body: 'Tre serier med korte støt med ett minutts stillhet mellom hver serie. Dette betyr: søk informasjon umiddelbart. Slå på DAB-radio, TV, eller sjekk offisielle kilder på nett.',
  civil_defense_step2_title: '2. Flyalarm',
  civil_defense_step2_body: 'Korte støt som varer omtrent ett minutt. Dette betyr umiddelbar fare for angrep — søk dekning nå. Gå til nærmeste tilfluktsrom, kjeller eller innerrom umiddelbart.',
  civil_defense_step3_title: '3. Gå innendørs og finn dekning',
  civil_defense_step3_body: 'Kom deg innendørs. Lukk alle vinduer, dører og ventilasjonsåpninger. Bruk denne appen for å finne nærmeste offentlige tilfluktsrom. Kompasset og kartet fungerer uten internett. Hvis det ikke er noe tilfluktsrom i nærheten, gå til en kjeller eller et innerrom bort fra vinduer.',
  civil_defense_step4_title: '4. Lytt til NRK på DAB-radio',
  civil_defense_step4_body: 'Lytt til NRK P1 på DAB-radio for offisielle oppdateringer og instruksjoner fra myndighetene. DAB-radio fungerer selv når mobilnettet og internett er nede.',
  civil_defense_step5_title: '5. Faren over',
  civil_defense_step5_body: 'Én sammenhengende tone på omtrent 30 sekunder. Faren eller angrepet er over. Fortsett å følge instruksjoner fra myndighetene.',
  civil_defense_source: 'Kilde: DSB (Direktoratet for samfunnssikkerhet og beredskap)',

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
