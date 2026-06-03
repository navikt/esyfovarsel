# Referansegrunnlag for ADR-001

Dette dokumentet samler bakgrunn og detaljer til diskusjonen om fremtidig varselarkitektur. Det er ikke en beslutning. Det bevarer kontekst fra tidligere issue-plan og gjør det enklere å ta diskusjonen på nytt uten å miste historikken.

Se hoveddokumentet: [ADR-001: Fremtidig varselarkitektur i esyfovarsel](../ADR-001-fremtidig-varselarkitektur.md).

Den tidligere oppgaveplanen pauses og erstattes som styrende grunnlag av ADR-001 inntil teamet har valgt retning.

## Tidligere issue-plan

Tidligere plan for nytt arbeidsgivervarsel-spor i `esyfovarsel` besto av disse issueene:

- [**#996** Etabler schema- og kontraktstrategi for arbeidsgivervarsel](https://github.com/navikt/esyfovarsel/issues/996)
- [**#997** Etabler arbeidsgivervarsel-topic, ACL og consumer](https://github.com/navikt/esyfovarsel/issues/997)
- [**#998** Etabler idempotens og status per output for arbeidsgivervarsel](https://github.com/navikt/esyfovarsel/issues/998)
- [**#999** Støtt `NarmesteLederVarsel` med arbeidsgivernotifikasjon og DineSykmeldte-signal](https://github.com/navikt/esyfovarsel/issues/999)
- [**#1000** Støtt `VirksomhetVarsel` for arbeidsgivervarsel](https://github.com/navikt/esyfovarsel/issues/1000)
- [**#1001** Harmoniser arbeidstakervarsel med ny kontraktsmodell](https://github.com/navikt/esyfovarsel/issues/1001)
- [**#1002** Epic: Nytt arbeidsgivervarsel-spor i `esyfovarsel`](https://github.com/navikt/esyfovarsel/issues/1002)

Retningen i den planen var i hovedsak:

- eget arbeidsgivervarsel-topic
- Schema Registry
- JSON Schema som foretrukket spor
- Avro vurdert eksplisitt
- varianttyper i kontrakten
- idempotens per output
- DLQ og retention som del av designet

Denne retningen skal nå forstås som **tidligere plan og referanse**, ikke som en låst beslutning.

Hvorfor planen pauses:

- teamet ønsker å diskutere arkitektur før oppgaver settes i gang
- det er et tydelig ønske om å ikke haste-løse noe i `esyfovarsel`
- oppfølgingsplan er første behov, men ikke eneste fremtidige produsent
- `sak` eller `case` er fortsatt for uklart til at det er klokt å låse kontrakt og topic nå

## Detaljert alternativmatrise

| Alternativ | Passer dagens stack | Hovedgevinst | Hovedkostnad | Typiske Nav-spørsmål |
|---|---|---|---|---|
| HTTP API inn til `esyfovarsel` + outbox | Ja | Tydelig inngangskontrakt og lav terskel for teamet | Krever inbound-policy, auth og god idempotens | Hvem får kalle API-et, og hvordan valideres det? |
| Kafka/topic inn til eksisterende `esyfovarsel` | Ja | Passer asynkron modell og flere produsenter | Mer krevende kontrakt, ACL, DLQ og operativ feilsøking | Skal vi bruke nytt topic, og hvor streng skal retention være? |
| Ny app eller tjeneste | Ja, men dyrere | Klarere ny grense hvis ansvaret faktisk er nytt | Høy etablerings- og migrasjonskostnad | Egen drift, egne manifester, eget ansvar |
| Domeneappene sender selv | Delvis | Maksimalt domeneeierskap | Stor fare for duplisering og sprik | Hvem eier kanalregler, idempotens og observability? |
| Gammel måte / gjøre ingenting nå | Ja | Lav risiko på kort sikt | Utsetter avklaringer og kan øke gjeld | Hvor lenge er "midlertidig" akseptabelt? |

### Alternativ 1: HTTP API inn til `esyfovarsel` + outbox

Mer detalj:

- Produsent sender en bestilling til `esyfovarsel`
- `esyfovarsel` validerer kontrakt, lagrer bestilling og kjører utsending asynkront
- Kan kombineres med statusoppslag eller bare asynkron kvittering

Dette kan være et godt valg hvis:

- teamet ønsker tydelig kontrakt og enkel lokal utvikling
- første produsenter er få og kjente
- vi vil skille tydelig mellom "bestilling mottatt" og "utsending fullført"

Dette er risikabelt hvis:

- API-et blir for kanalspesifikt
- vi åpner inbound for mange kallere uten god styring
- vi ikke avklarer hvordan `sak` skal representeres ved bestilling

### Alternativ 2: Kafka/topic inn til eksisterende `esyfovarsel`

Mer detalj:

- Produsent publiserer hendelse til nytt topic eller nytt kontraktspor
- `esyfovarsel` konsumerer, validerer og sender videre til relevante kanaler

Dette kan være et godt valg hvis:

- flere produsenter skal på over tid
- vi ønsker løs kobling og tydelig asynkron modell
- replay og senere utvidelser er viktigere enn synkron kvittering

Dette er risikabelt hvis:

- vi låser oss til komplisert kontrakt før `sak` er avklart
- vi undervurderer operativ kostnad ved DLQ, retention og replay
- vi gjør Kafka til standardvalg uten å ha vurdert HTTP seriøst

### Alternativ 3: Ny app eller tjeneste

Mer detalj:

- ny tjeneste kan eie inngangskontrakt og eventuelt `sak`
- `esyfovarsel` kan fortsette som ren utsendingstjeneste, eller fases videre

Dette kan være et godt valg hvis:

- vi mener ansvaret faktisk er nytt
- vi vil rydde opp i eksisterende grenser

Dette er risikabelt hvis:

- vi egentlig bare lager et ekstra lag
- vi ikke har kapasitet til ny driftsflate og migrasjon nå

### Alternativ 4: Domeneappene sender selv uten `esyfovarsel`

Mer detalj:

- hver produsent integrerer direkte med kanalene den trenger
- `esyfovarsel` får mindre eller ingen rolle

Dette kan være et godt valg hvis:

- kanalbehovene er få og tydelig domeneavgrenset
- teamene faktisk vil eie hele flyten selv

Dette er risikabelt hvis:

- vi mister felles mønstre for idempotens, logging og sikkerhet
- mange apper må lære arbeidsgivernotifikasjon, brev og andre kanaler parallelt

### Alternativ 5: Midlertidig gammel måte / gjøre ingenting nå

Mer detalj:

- oppfølgingsplan løses med minst mulig endring
- arkitekturvalg utsettes til vi har bedre bilde av produsenter og `sak`

Dette kan være et godt valg hvis:

- tidspresset er høyt
- usikkerheten om målbildet er større enn gevinsten ved å starte nå

Dette er risikabelt hvis:

- "midlertidig" blir langvarig
- teamet ikke setter en tydelig ny beslutningsfrist

## Sak/case-konseptet

Dette er trolig det viktigste åpne spørsmålet.

Dagens løsning har allerede et `sak`-begrep i arbeidsgivernotifikasjon. I eksisterende kode og database finnes blant annet:

- tabell for arbeidsgivernotifikasjonssak
- felt for `type`, `eksternSakId`, `ressursId` og `mottaker_type`
- logikk for å opprette eller gjenbruke sak per type og mottaker

Det viser at `sak` allerede betyr noe i dagens løsning. Samtidig oppleves dagens konsept som suboptimalt for videre utvidelse.

Det som bør avklares:

1. **Hva er en sak?**
   Er det en teknisk gruppering for kanalene, eller en faktisk domenesak som produsenten eier?

2. **Hvem eier identiteten?**
   Bør domeneappen sende inn egen `sakId` eller grupperingsnøkkel, eller skal `esyfovarsel` opprette en ny identitet?

3. **Hva er stabil nøkkel?**
   Hvis flere varsler hører til samme sak over tid, må vi vite hva som er den stabile nøkkelen. Dette påvirker både idempotens, brukeropplevelse og gjenbruk av sak i arbeidsgivernotifikasjon.

4. **Hvor mye kanalmodell skal lekke inn i domenekontrakten?**
   Hvis `sak` blir definert av arbeidsgivernotifikasjon først, kan vi låse oss til én kanalmodell for tidlig.

5. **Skal sak være obligatorisk for alle varsler?**
   Kanskje noen varsler alltid må høre til en sak, mens andre ikke trenger det.

Mulig arbeidsregel til diskusjon:

- domeneapp eier forretningsmessig `sak`
- kontrakten inn til `esyfovarsel` inneholder nok informasjon til å gruppere riktig
- `esyfovarsel` oversetter dette til kanalspesifikke `sak`-modeller ved behov

Denne arbeidsregelen må vurderes mot dagens kode og behov hos minst oppfølgingsplan og én annen produsent.

## Teknisk faktagrunnlag fra dagens løsning

### Repo og dokumentasjon

- Repoet har ikke hatt `docs/adr/` fra før
- Dokumentasjon under `docs/` har i praksis vært begrenset til `docs/local-development.md`

### Stack

- Kotlin/Ktor
- plain Apache Kafka-klient
- PostgreSQL
- NAIS på GCP
- OpenTelemetry auto-instrumentation

### Dagens Kafka-spor

Produksjonstopicen `varselbus` har:

- `metadata.name: varselbus`
- namespace `team-esyfo`
- pool `nav-prod`
- `partitions: 20`
- `replication: 3`
- `retentionHours: -1`
- `retentionBytes: -1`
- `esyfovarsel` med read
- mange produsenter med write
- `dvh-sykefravar-airflow-kafka` med read

Dette sier noe viktig om dagens arkitektur:

- `esyfovarsel` er allerede et konsumknutepunkt
- topicet er langlivet og deles av mange
- endringer her påvirker flere produsenter og minst én annen konsument
- uendelig retention i dagens topic er et faktum, ikke en anbefaling for nye topics eller DLQ-er

### Dagens prod-manifest

`nais/nais-prod.yaml` viser blant annet:

- app: `esyfovarsel`
- namespace: `team-esyfo`
- inbound `accessPolicy` bare for `esyfovarsel-job`
- outbound-regler mot blant annet `digdir-krr-proxy`, `notifikasjon-produsent-api`, `syfosmregister`, `narmesteleder` og `istilgangskontroll`
- Kafka pool `nav-prod`
- Cloud SQL Postgres 17
- Prometheus path `/prometheus`
- liveness `/isAlive`
- readiness `/isReady`

Konsekvens:

- HTTP-alternativet krever manifestendring og auth-avklaring
- Kafka-alternativet passer bedre med dagens nettverksmodell, men krever topic- og kontraktvalg

### Eksisterende `sak`-spor i kode

Kode og migreringer viser at arbeidsgivernotifikasjon allerede modellerer `sak` mer detaljert enn bare en tilfeldig gruppering:

- `V41__Alter_arbeidsgivernotifikasjoner_sak_add_type_and_nullable.sql`
- `V44__alter_arbeidsgivernotifikasjoner_sak_lenke_nullable_add_mottaker_type.sql`
- domeneobjekter som `NyStatusSakInput`
- tjenester som oppretter eller oppdaterer sak ved gjenbruk

Dette styrker behovet for å diskutere `sak` før ny generell arkitektur velges.

## Hva bør diskuteres med teamet

Foreslåtte spørsmål til neste diskusjon:

1. Hva er vi egentlig redde for å låse oss til hvis vi velger Kafka nå?
2. Hva mister vi hvis vi velger HTTP som inngang, men fortsatt beholder asynkron intern utsending?
3. Er `esyfovarsel` riktig sted for ny inngang, eller bare riktig sted for utsending?
4. Hvilke produsenter ser vi for oss de neste 12 til 24 månedene?
5. Hvilken minste løsning er forsvarlig for oppfølgingsplan hvis vi utsetter målarkitektur?
6. Hva må være avklart om `sak` før vi kan lage kontrakt?
7. Trenger vi én generell kontrakt, eller ett lite kontraktssett med tydelige varianter?
8. Hva blir enklest å drifte, feilsøke og rulle tilbake i praksis?
