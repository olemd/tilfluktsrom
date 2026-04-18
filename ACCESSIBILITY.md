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

Tilfluktsrom er **delvis i samsvar** med kravene i EN 301 549 (basert på WCAG 2.2 nivå AA). Statusen er «delvis» fordi ett punkt er dokumentert som uforholdsmessig byrde (se avsnitt 3.2); egenkontroll har ikke identifisert andre gjenværende avvik på dette nivået per versjon 1.9.0.

## 2. Referansestandard

Erklæringen bygger på EU-direktiv 2016/2102 (Web Accessibility Directive, WAD) slik det er innført i norsk rett, og bruker EN 301 549 / WCAG 2.2 AA som teknisk referanse. For publikumsveiledning henviser vi til Digitaliseringsdirektoratet (Digdir) / Uutilsynet på https://www.uutilsynet.no/.

## 3. Ikke-tilgjengelig innhold

### 3.1 Manglende samsvar med regelverket

Per versjon 1.9.0 har egenkontroll ikke identifisert gjenværende avvik på dette nivået i Android-appen. Tidligere identifiserte avvik som er utbedret, er dokumentert i avsnitt 7 (Historikk).

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
- **Dato for siste gjennomgang:** 18. april 2026.

## 5. Tilbakemelding og kontaktinformasjon

Har du oppdaget en tilgjengelighetsfeil som ikke er nevnt her, eller trenger du innholdet i et annet format?

- **E-post:** olemd@odinprosjekt.no
- **Forventet svartid:** 14 dager.

## 6. Klageordning

Er du ikke fornøyd med svaret du får, kan saken meldes til tilsynsorganet:

- **Tilsynsmyndighet:** Digitaliseringsdirektoratet (Digdir) / Uutilsynet.
- **Nettsted:** https://www.uutilsynet.no/
- **Klagerett:** Du har rett til å melde manglende universell utforming av digitale tjenester til tilsynet.

## 7. Historikk

### Versjon 1.9.0 — 17. april 2026

Følgende avvik ble identifisert ved egenkontroll og utbedret i denne versjonen:

- **WCAG 2.4.7 Synlig fokus.** Systemets standard fokusmarkering er slått på for temaet `Theme.Tilfluktsrom` via `android:defaultFocusHighlightEnabled`, og bundne ripple-bakgrunner (`?attr/selectableItemBackground`) er tatt i bruk på ikonknappene slik at fokusringen er synlig ved tastatur- og bryternavigasjon.
- **WCAG 4.1.2 Navn, rolle, verdi.** `DirectionArrowView` rapporterer seg nå som `ImageView` og setter innholdsbeskrivelse på formen «Retning til tilfluktsrom: <retning>, <avstand> unna»; endringer annonseres til TalkBack hver gang retningen krysser en 45°-sektor, med 750 ms-struping mot spam. Retningsbegrepene er oversatt til både bokmål og nynorsk.
- **WCAG 1.4.3 Minimumskontrast.** Advarselsbanneret for manglende offline-kart hadde tidligere bakgrunnsfarge `#E65100` med hvit tekst (kontrastforhold 3,75 : 1, under AA-kravet på 4,5 : 1). Bakgrunnsfargen er endret til `#BF360C`, som gir kontrast på omkring 5,5 : 1 mot hvit tekst.
- **WCAG 2.3.3 Bevegelse fra interaksjon.** Retningspilen rotert tidligere kontinuerlig med kompasset, uten hensyn til Android-innstillingen «Fjern animasjoner». Når `Settings.Global.ANIMATOR_DURATION_SCALE` er 0, snapper retningspilen nå til nærmeste av åtte 45°-sektorer i stedet for å rotere kontinuerlig.

---

## Notes for the maintainer (English — strip before publication)

This statement is honest today but must be kept up to date. Before using it as a deliverable alongside a public-sector licensing agreement or a live public-sector deployment:

- [ ] Replace `olemd@odinprosjekt.no` in §5 with a dedicated, monitored, public-facing address that the licensee (or you, if self-hosting) commits to reading within the stated response time.
- [ ] After each release that ships an accessibility fix, move the corresponding item from §3.1 to §7 (Historikk) under a new `Versjon X.Y.Z` subsection, and update §1 *Samsvarsstatus* accordingly. When §3.1 and §3.2 are both empty, §1 becomes "i samsvar".
- [ ] Update §4 *Dato for siste gjennomgang* at every release where user-facing code or content changes, even if nothing in §3 changes.
- [ ] Commission an independent WCAG 2.2 AA audit before the first public-sector deployment. §4 must then be amended from "Egenkontroll" to cite the auditor, their contact, and the audit date.
- [x] Confirm §6 *Tilsynsmyndighet*. Digdir / Uutilsynet is the correct supervisory body for this app (confirmed 2026-04-17); no buyer-specific override expected.
- [ ] Re-verify the §3.2 disproportionate-burden claim before publication. WAD requires a documented cost-vs-benefit assessment, not a bare claim. Keep a short internal memo on file.
- [ ] If an English translation is provided for international reviewers, mark it clearly as a courtesy translation and keep the Bokmål version authoritative.
- [ ] Re-verify: is the deployed service actually covered by WAD? Privately-owned apps are exempt; coverage begins once the app is procured by a public-sector body or otherwise delivers a public-sector service.
