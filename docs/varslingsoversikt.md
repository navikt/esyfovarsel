# Varslingsoversikt

Denne siden viser hvilke domeneapper som skriver til `team-esyfo.varselbus`, og hvilke varslingsflater de treffer gjennom `esyfovarsel`.

Oversikten bygger på topic-ACL i `nais/topics/varselbus-topic-*.yaml` og rutingen i `VarselBusService`.

## Kommunikasjonsflater

| Kommunikasjonsflate | Mikrotjenester som i dag går via `esyfovarsel` | Hva de må håndtere selv uten `esyfovarsel` |
| --- | --- | --- |
| Varsler på Min side arbeidsgiver | `isdialogmote`<br>`syfomotebehov`<br>`syfooppfolgingsplanservice`<br>`syfo-oppfolgingsplan-backend`<br>`isoppfolgingsplan` | Direkte integrasjon mot `notifikasjon-produsent-api`, inkludert oppretting og oppdatering av sak, kalenderavtale og status. |
| Dine Sykmeldte | `isdialogmote`<br>`syfomotebehov`<br>`syfooppfolgingsplanservice`<br>`syfo-oppfolgingsplan-backend`<br>`isoppfolgingsplan` | Publisering til `team-esyfo.dinesykmeldte-hendelser-v2`, og logikk for å ferdigstille tidligere varsler. |
| Varsler på Min side for sykmeldt | `isdialogmote`<br>`syfomotebehov`<br>`syfooppfolgingsplanservice`<br>`syfo-oppfolgingsplan-backend`<br>`isoppfolgingsplan`<br>`aktivitetskrav-backend`<br>`meroppfolging-backend`<br>`ismeroppfolging` | Publisering til `min-side.aapen-brukervarsel-v1`, ekstern varsling ved behov og inaktivering av varsler når de er lest eller utløpt. |
| Ditt sykefravær | `isdialogmote`<br>`syfomotebehov`<br>`meroppfolging-backend`<br>`ismeroppfolging` | Publisering til `flex.ditt-sykefravaer-melding` og logikk for å lukke eller erstatte tidligere meldinger. |
| Mikrofronter på Min side | `isdialogmote`<br>`syfomotebehov`<br>`aktivitetskrav-backend`<br>`meroppfolging-backend`<br>`ismeroppfolging` | Publisering av `enable` og `disable` til `min-side.aapen-microfrontend-v1`, og egen logikk for synlighet, utløp og mapping til `syfo-dialog`, `syfo-aktivitetskrav` og `syfo-meroppfolging`. |
| Fysiske brev | `isdialogmote`<br>`aktivitetskrav-backend`<br>`meroppfolging-backend`<br>`isarbeidsuforhet`<br>`isfrisktilarbeid`<br>`ismanglendemedvirkning` | Distribusjon via journalpost, samt fallback når brukeren ikke kan varsles digitalt. |

## Kort fortalt

`esyfovarsel` gjør i dag tre ting på vegne av domeneappene:

1. Leser hendelser fra `team-esyfo.varselbus`.
2. Mapper hver hendelse til riktig kommunikasjonsflate.
3. Håndterer tilstand rundt utsending, ferdigstilling, retry og noen steder fallback til fysisk brev.

## Svar på de vanligste spørsmålene

### Hvilken backend bruker varsling på Min side arbeidsgiver?

Det er `esyfovarsel` som sender arbeidsgivernotifikasjoner videre til `notifikasjon-produsent-api`. I denne repoen brukes det blant annet for dialogmøte, møtebehov og oppfølgingsplan.

### Hvilken app skrur av og på mikrofronter?

Det er `esyfovarsel` som gjør dette. Appen leser arbeidstakerhendelser fra varselbussen og publiserer så `enable` eller `disable` til `min-side.aapen-microfrontend-v1`.

Disse mikrofrontene styres her:

- `syfo-dialog`
- `syfo-aktivitetskrav`
- `syfo-meroppfolging`

## Avgrensning

Andre apper har også tilgang til topicet, blant annet `syfo-dokumentporten` og `isyfomock`. De er ikke tatt med her fordi koblingen til en egen varslingsflate ikke er like tydelig i rutingen i dette repoet.
