# Tilgjengelighetserklæring for Tilfluktsrom

> **English preamble** (not part of the statement):
> This is the accessibility statement for the Tilfluktsrom app (Android + PWA).
> The authoritative version is in Norwegian Bokmål — that is what Uutilsynet and
> Norwegian public-sector buyers will review. If you need an English translation
> for procurement or contributor review, contact the maintainer (section 5).
> A maintainer checklist in English follows at the end of this file and must be
> stripped before publication.

---

## 1. Samsvarsstatus

Tilfluktsrom er **delvis i samsvar** med kravene i EN 301 549 (basert på WCAG 2.2 nivå AA). De kjente avvikene er listet opp i avsnitt 3.

## 2. Referansestandard

Erklæringen bygger på EU-direktiv 2016/2102 (Web Accessibility Directive, WAD) slik det er innført i norsk rett, og bruker EN 301 549 / WCAG 2.2 AA som teknisk referanse. For publikumsveiledning henviser vi til Digitaliseringsdirektoratet (Digdir) / Uutilsynet på https://www.uutilsynet.no/.

## 3. Ikke-tilgjengelig innhold

### 3.1 Manglende samsvar med regelverket

Følgende avvik er identifisert ved egenkontroll. Utbedringer er lagt inn i kildekoden per 17. april 2026 og inngår i første utgave som bygges og publiseres etter denne datoen. Når utgaven er publisert, flyttes hvert punkt til et «Utbedret»-avsnitt med konkret versjonsnummer, og avsnitt 1 *Samsvarsstatus* oppdateres tilsvarende.

- **WCAG 2.4.7 Synlig fokus.** Android-appen hadde ikke tydelig synlig fokusmarkering på alle egendefinerte visninger (bl.a. FAB-knapper og den egenutviklede retningspilen `DirectionArrowView`). Skjermtastatur- og bryterbrukere kunne derfor miste oversikten over hvor fokus var plassert. *Status per 17. april 2026: utbedret i kildekode. Systemets standard fokusmarkering er slått på for temaet `Theme.Tilfluktsrom` via `android:defaultFocusHighlightEnabled`, og bundne ripple-bakgrunner (`?attr/selectableItemBackground`) er tatt i bruk på ikonknappene slik at fokusringen blir synlig ved tastatur- og bryternavigasjon. Venter på utgivelse.*
- **WCAG 4.1.2 Navn, rolle, verdi.** Retningspilen i kompassvisningen er tegnet på et `Canvas` og eksponerte ikke retnings- og avstandsinformasjon til TalkBack på en måte som ble oppdatert dynamisk. Skjermleserbrukere fikk derfor ikke samme navigasjonsstøtte som seende brukere. *Status per 17. april 2026: utbedret i kildekode. `DirectionArrowView` rapporterer seg som `ImageView` og setter innholdsbeskrivelse på formen «Retning til tilfluktsrom: <retning>, <avstand> unna»; endringer annonseres til TalkBack hver gang retningen krysser en 45°-sektor, med 750 ms-struping mot spam. Retningsbegrepene er oversatt til både bokmål og nynorsk. Venter på utgivelse.*
- **WCAG 1.4.3 Minimumskontrast.** Advarselsbanneret for manglende offline-kart brukte bakgrunnsfarge `#E65100` med hvit tekst. Kontrastforholdet var 3,75 : 1, under AA-kravet på 4,5 : 1. *Status per 17. april 2026: utbedret i kildekode. Bakgrunnsfargen er endret til `#BF360C`, som gir kontrast på omkring 5,5 : 1 mot hvit tekst. Venter på utgivelse.*
- **WCAG 2.3.3 Bevegelse fra interaksjon.** Retningspilen roterte kontinuerlig med kompasset mens brukeren vendte på enheten. Appen tok ikke hensyn til Android-innstillingen «Fjern animasjoner» / redusert bevegelse, og brukere med vestibulære plager hadde ikke mulighet til å slå av den kontinuerlige rotasjonen. *Status per 17. april 2026: utbedret i kildekode. Når `Settings.Global.ANIMATOR_DURATION_SCALE` er 0 (brukeren har slått av animasjoner), snapper retningspilen til nærmeste av åtte 45°-sektorer i stedet for å rotere kontinuerlig. Venter på utgivelse.*

### 3.2 Uforholdsmessig byrde

- **Full tastaturnavigasjon i kartet (PWA).** Leaflet-kartet støtter zoom og grunnleggende pan med tastatur, men ikke effektiv tastaturnavigasjon til vilkårlige geografiske punkter. Å bygge et fullt tilgjengelig alternativt kart vil være uforholdsmessig for en privatfinansiert kodebase. Avviket er delvis avbøtet ved at appen alltid viser en tekstbasert liste over nærmeste tilfluktsrom som fungerer fullt ut uten mus eller kart.

Vurderingen skal dokumenteres nærmere før erklæringen brukes i en offentlig anskaffelsessammenheng; se vedlikeholdsnotatet nederst.

### 3.3 Innhold utenfor virkeområdet

Følgende innhold er ikke omfattet av regelverket og vurderes heller ikke som en del av erklæringen:

- Kartfliser fra OpenStreetMap er tredjepartsinnhold som ikke er under vår kontroll.
- Offentlige tilfluktsromdata fra DSB/Geonorge vises uendret. Eventuelle feil i kildedata (adresse, kapasitet, plassering) kan ikke rettes i appen og må meldes til dataeier.
- Operativsystemkomponenter (bl.a. Android systemdialoger, nettleserens UI for PWA-installasjon) er ikke vårt ansvar.

## 4. Utarbeiding av erklæringen

- **Vurderingsmetode:** Egenkontroll. Ingen uavhengig tredjepartsrevisjon er gjennomført.
- **Dato erklæringen ble utarbeidet:** 17. april 2026.
- **Dato for siste gjennomgang:** 17. april 2026.

## 5. Tilbakemelding og kontaktinformasjon

Har du oppdaget en tilgjengelighetsfeil som ikke er nevnt her, eller trenger du innholdet i et annet format?

- **E-post:** olemd@odinprosjekt.no
- **Forventet svartid:** 14 dager.

## 6. Klageordning

Er du ikke fornøyd med svaret du får, kan saken meldes til tilsynsorganet:

- **Tilsynsmyndighet:** Digitaliseringsdirektoratet (Digdir) / Uutilsynet.
- **Nettsted:** https://www.uutilsynet.no/
- **Klagerett:** Du har rett til å melde manglende universell utforming av digitale tjenester til tilsynet.

---

## Notes for the maintainer (English — strip before publication)

This statement is honest today but must be kept up to date. Before using it as a deliverable alongside a public-sector licensing agreement or a live public-sector deployment:

- [ ] Replace `olemd@odinprosjekt.no` in §5 with a dedicated, monitored, public-facing address that the licensee (or you, if self-hosting) commits to reading within the stated response time.
- [ ] After each release that ships an accessibility fix, move the corresponding item from §3.1 to an "Utbedret i versjon X.Y.Z" (resolved) list and update §1 *Samsvarsstatus* accordingly. When §3.1 is empty, §1 becomes "i samsvar".
- [ ] Replace each *Venter på utgivelse* status with a concrete version number as soon as that version ships, so buyers can verify against the changelog.
- [ ] Update §4 *Dato for siste gjennomgang* at every release where user-facing code or content changes, even if nothing in §3 changes.
- [ ] Commission an independent WCAG 2.2 AA audit before the first public-sector deployment. §4 must then be amended from "Egenkontroll" to cite the auditor, their contact, and the audit date.
- [ ] Confirm §6 *Tilsynsmyndighet* matches the actual supervisory body for the buyer's sector. For most Norwegian public-sector bodies it is Digdir/Uutilsynet, but sector-specific services (e.g. under DSB or Sivilforsvaret) may have alternative complaint paths.
- [ ] Re-verify the §3.2 disproportionate-burden claim before publication. WAD requires a documented cost-vs-benefit assessment, not a bare claim. Keep a short internal memo on file.
- [ ] If an English translation is provided for international reviewers, mark it clearly as a courtesy translation and keep the Bokmål version authoritative.
- [ ] Re-verify: is the deployed service actually covered by WAD? Privately-owned apps are exempt; coverage begins once the app is procured by a public-sector body or otherwise delivers a public-sector service.
