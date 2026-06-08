# Varslingsoversikt

Denne siden viser hvilke domeneapper som skriver til `team-esyfo.varselbus`, og hvilke varslingsflater de treffer gjennom `esyfovarsel`.

Oversikten bygger på topic-ACL i `nais/topics/varselbus-topic-*.yaml` og rutingen i `VarselBusService`.

## Kommunikasjonsflater

En tjeneste er listet i en rad når minst én av varseltypene den publiserer til `team-esyfo.varselbus`, treffer den kommunikasjonsflaten i `esyfovarsel`.

| Kommunikasjonsflate              | Brukt av mikrotjeneste                                                                                                                                                          | Integrasjon                                                                                                                                                                                                                                                                                                                                                                                                         |
| -------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Varsler på Min side arbeidsgiver | `isdialogmote`<br>`isoppfolgingsplan`<br>`syfooppfolgingsplanservice`<br>`syfomotebehov`                                                                                        | Direkte integrasjon mot `notifikasjon-produsent-api`, inkludert oppretting og oppdatering av sak, kalenderavtale og status.                                                                                                                                                                                                                                                                                         |
| Varsler til Altinn-ressurs       | `isdialogmote`<br>`syfo-dokumentporten`                                                                                                                                         | Direkte integrasjon mot `notifikasjon-produsent-api` for mottaker `ALTINN`, inkludert `ressursId`, sakstatus, idempotens og retry.                                                                                                                                                                                                                                                                                  |
| Dine Sykmeldte                   | `isdialogmote`<br>`syfooppfolgingsplanservice`<br>`syfomotebehov`                                                                                                               | Publisering til `team-esyfo.dinesykmeldte-hendelser-v2`, og logikk for å ferdigstille tidligere varsler.                                                                                                                                                                                                                                                                                                            |
| Varsler på Min side for sykmeldt | `aktivitetskrav-backend`<br>`isdialogmote`<br>`ismeroppfolging`<br>`meroppfolging-backend`<br>`syfo-oppfolgingsplan-backend`<br>`syfooppfolgingsplanservice`<br>`syfomotebehov` | Publisering til `min-side.aapen-brukervarsel-v1`, ekstern varsling ved behov og inaktivering av varsler når de er lest eller utløpt.                                                                                                                                                                                                                                                                                |
| Ditt sykefravær                  | `isdialogmote`<br>`meroppfolging-backend`<br>`syfomotebehov`                                                                                                                    | Publisering til `flex.ditt-sykefravaer-melding` og logikk for å lukke eller erstatte tidligere meldinger.                                                                                                                                                                                                                                                                                                           |
| Mikrofronter på Min side         | `aktivitetskrav-backend`<br>`isdialogmote`<br>`ismeroppfolging`<br>`meroppfolging-backend`<br>`syfomotebehov`                                                                   | Selve `enable` og `disable` publiseres av `esyfovarsel` til `min-side.aapen-microfrontend-v1`. `isdialogmote` og `syfomotebehov` sender hendelser som kan både åpne og lukke dialogmøte-mikrofrontend. `aktivitetskrav-backend`, `meroppfolging-backend` og `ismeroppfolging` sender hendelser som åpner eller forlenger synlighet, mens lukking i dag i hovedsak håndteres av `esyfovarsel` når synlighet utløper. |
| Fysiske brev                     | `aktivitetskrav-backend`<br>`isarbeidsuforhet`<br>`isdialogmote`<br>`isfrisktilarbeid`<br>`ismanglendemedvirkning`<br>`meroppfolging-backend`                                   | Distribusjon via journalpost, samt fallback når brukeren ikke kan varsles digitalt.                                                                                                                                                                                                                                                                                                                                 |

## Kort fortalt

`esyfovarsel` gjør i dag tre ting på vegne av domeneappene:

1. Leser hendelser fra `team-esyfo.varselbus`.
2. Mapper hver hendelse til riktig kommunikasjonsflate.
3. Håndterer tilstand rundt utsending, ferdigstilling, retry og noen steder fallback til fysisk brev.

## Svar på de vanligste spørsmålene

### Hvilken backend bruker varsling på Min side arbeidsgiver?

Det er `esyfovarsel` som sender arbeidsgivernotifikasjoner videre til `notifikasjon-produsent-api`. I denne repoen brukes det blant annet for dialogmøte, møtebehov og oppfølgingsplan.

### Hva med Altinn-delen?

`esyfovarsel` håndterer også arbeidsgivervarsler av typen `AG_VARSEL_ALTINN_RESSURS`. De går via `notifikasjon-produsent-api`, men med mottaker `ALTINN` og en `ressursId`, for eksempel `nav_syfo_dialogmote`.

Både `isdialogmote` og `syfo-dokumentporten` bruker denne flyten. I begge repoene brukes `AG_VARSEL_ALTINN_RESSURS` for varsler til Altinn-ressurs.

### Hvilken app skrur av og på mikrofronter?

Det er `esyfovarsel` som gjør dette. Appen leser arbeidstakerhendelser fra varselbussen og publiserer så `enable` eller `disable` til `min-side.aapen-microfrontend-v1`.

Disse mikrofrontene styres her:

- `syfo-dialog`
- `syfo-aktivitetskrav`
- `syfo-meroppfolging`

## Avgrensning

Andre apper har også tilgang til topicet, blant annet `isyfomock`. De er ikke tatt med her fordi koblingen til en egen varslingsflate ikke er like tydelig i rutingen i dette repoet.
