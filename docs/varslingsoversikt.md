# Varslingsoversikt

Denne siden viser hvilke kildeapper som kan sende hendelser inn til `esyfovarsel` via `team-esyfo.varselbus`, og hvilke varslingsflater de treffer.

Oversikten bygger på topic-ACL i `nais/topics/varselbus-topic-*.yaml`, verifiserte produsenter og rutingen i `VarselBusService`.

## Kommunikasjonsflater

En app er listet i en rad når den kan skrive til `team-esyfo.varselbus`, og minst én relevant hendelse treffer den kommunikasjonsflaten i `esyfovarsel`.

Navnene i tabellen er først og fremst kildenavn eller appnavn brukt på varselbus og i NAIS, ikke en egen oversikt over GitHub-repoer.

| Kommunikasjonsflate              | Kildeapp                                                                                                                                                                                                                                                               | Integrasjon                                                                                                                                                                                                                                                                                                                                                                                                         |
| -------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Varsler på Min side arbeidsgiver | `isdialogmote`<br>`isoppfolgingsplan`<br>`syfooppfolgingsplanservice`<br>`syfomotebehov`                                                                                                                                                                               | Direkte integrasjon mot `notifikasjon-produsent-api`, inkludert oppretting og oppdatering av sak, kalenderavtale og status.                                                                                                                                                                                                                                                                                         |
| Varsler til Altinn-ressurs       | `isdialogmote`<br>`syfo-dokumentporten`                                                                                                                                                                                                                                | Direkte integrasjon mot `notifikasjon-produsent-api` for mottaker `ALTINN`, inkludert `ressursId`, sakstatus, idempotens og retry.                                                                                                                                                                                                                                                                                  |
| Dine Sykmeldte                   | `isdialogmote`<br>`syfooppfolgingsplanservice`<br>`syfomotebehov`                                                                                                                                                                                                      | Publisering til `team-esyfo.dinesykmeldte-hendelser-v2`, og logikk for å ferdigstille tidligere varsler.                                                                                                                                                                                                                                                                                                            |
| Varsler på Min side for sykmeldt | `aktivitetskrav-backend`<br>`isarbeidsuforhet`<br>`isdialogmote`<br>`isfrisktilarbeid`<br>`ismeroppfolging`<br>`ismanglendemedvirkning`<br>`isyfomock`<br>`meroppfolging-backend`<br>`syfo-oppfolgingsplan-backend`<br>`syfooppfolgingsplanservice`<br>`syfomotebehov` | Publisering til `min-side.aapen-brukervarsel-v1`, ekstern varsling ved behov og inaktivering av varsler når de er lest eller utløpt.                                                                                                                                                                                                                                                                                |
| Ditt sykefravær                  | `isdialogmote`<br>`meroppfolging-backend`<br>`syfomotebehov`                                                                                                                                                                                                           | Publisering til `flex.ditt-sykefravaer-melding` og logikk for å lukke eller erstatte tidligere meldinger.                                                                                                                                                                                                                                                                                                           |
| Mikrofronter på Min side         | `aktivitetskrav-backend`<br>`isdialogmote`<br>`ismeroppfolging`<br>`meroppfolging-backend`<br>`syfomotebehov`                                                                                                                                                          | Selve `enable` og `disable` publiseres av `esyfovarsel` til `min-side.aapen-microfrontend-v1`. `isdialogmote` og `syfomotebehov` sender hendelser som kan både åpne og lukke dialogmøte-mikrofrontend. `aktivitetskrav-backend`, `meroppfolging-backend` og `ismeroppfolging` sender hendelser som åpner eller forlenger synlighet, mens lukking i dag i hovedsak håndteres av `esyfovarsel` når synlighet utløper. |
| Fysiske brev                     | `aktivitetskrav-backend`<br>`isarbeidsuforhet`<br>`isdialogmote`<br>`isfrisktilarbeid`<br>`ismanglendemedvirkning`<br>`meroppfolging-backend`                                                                                                                          | Distribusjon via journalpost, samt fallback når brukeren ikke kan varsles digitalt.                                                                                                                                                                                                                                                                                                                                 |

### Hendelsestyper og kildeapper

Tabellen under viser verifisert mapping mellom `HendelseType` i `esyfovarsel` og appene som sender hendelsen inn på varselbus.

| HendelseType                                   | Kildeapp                                |
| ---------------------------------------------- | --------------------------------------- |
| `AG_VARSEL_ALTINN_RESSURS`                     | `isdialogmote`<br>`syfo-dokumentporten` |
| `NL_DIALOGMOTE_AVLYST`                         | `isdialogmote`                          |
| `NL_DIALOGMOTE_INNKALT`                        | `isdialogmote`                          |
| `NL_DIALOGMOTE_MOTEBEHOV_TILBAKEMELDING`       | `syfomotebehov`                         |
| `NL_DIALOGMOTE_NYTT_TID_STED`                  | `isdialogmote`                          |
| `NL_DIALOGMOTE_REFERAT`                        | `isdialogmote`                          |
| `NL_DIALOGMOTE_SVAR`                           | `isdialogmote`                          |
| `NL_DIALOGMOTE_SVAR_MOTEBEHOV`                 | `syfomotebehov`                         |
| `NL_OPPFOLGINGSPLAN_FORESPORSEL`               | `isoppfolgingsplan`                     |
| `NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING`     | `syfooppfolgingsplanservice`            |
| `SM_AKTIVITETSPLIKT`                           | `aktivitetskrav-backend`                |
| `SM_ARBEIDSUFORHET_FORHANDSVARSEL`             | `isarbeidsuforhet`                      |
| `SM_DIALOGMOTE_AVLYST`                         | `isdialogmote`                          |
| `SM_DIALOGMOTE_INNKALT`                        | `isdialogmote`                          |
| `SM_DIALOGMOTE_LEST`                           | `isdialogmote`                          |
| `SM_DIALOGMOTE_MOTEBEHOV_TILBAKEMELDING`       | `syfomotebehov`                         |
| `SM_DIALOGMOTE_NYTT_TID_STED`                  | `isdialogmote`                          |
| `SM_DIALOGMOTE_REFERAT`                        | `isdialogmote`                          |
| `SM_DIALOGMOTE_SVAR_MOTEBEHOV`                 | `syfomotebehov`                         |
| `SM_FORHANDSVARSEL_MANGLENDE_MEDVIRKNING`      | `ismanglendemedvirkning`                |
| `SM_KARTLEGGINGSSPORSMAL`                      | `ismeroppfolging`                       |
| `SM_MER_VEILEDNING`                            | `meroppfolging-backend`                 |
| `SM_OPPFOLGINGSPLAN_OPPRETTET`                 | `syfo-oppfolgingsplan-backend`          |
| `SM_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING`     | `syfooppfolgingsplanservice`            |
| `SM_VEDTAK_FRISKMELDING_TIL_ARBEIDSFORMIDLING` | `isfrisktilarbeid`                      |

## Kort fortalt

`esyfovarsel` gjør i dag tre ting på vegne av domeneappene:

1. Leser hendelser fra `team-esyfo.varselbus`.
2. Mapper hver hendelse til riktig kommunikasjonsflate.
3. Håndterer tilstand rundt utsending, ferdigstilling, retry og noen steder fallback til fysisk brev.
