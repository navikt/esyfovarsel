# Varslingsoversikt

Denne siden viser hvilke domeneapper som skriver til `team-esyfo.varselbus`, og hvilke varslingsflater de treffer gjennom `esyfovarsel`.

Oversikten bygger på topic-ACL i `nais/topics/varselbus-topic-*.yaml` og rutingen i `VarselBusService`.

## Kort fortalt

| Varslingsdel               | Hvor brukeren ser det            | Teknisk vei                                                          |
| -------------------------- | -------------------------------- | -------------------------------------------------------------------- |
| Arbeidsgivernotifikasjoner | Min side arbeidsgiver            | `esyfovarsel` kaller `notifikasjon-produsent-api`                    |
| Mikrofronter               | Min side                         | `esyfovarsel` publiserer til `min-side.aapen-microfrontend-v1`       |
| Brukernotifikasjoner       | Min side                         | `esyfovarsel` publiserer til `min-side.aapen-brukervarsel-v1`        |
| Dine Sykmeldte             | Arbeidsgiverflaten for sykmeldte | `esyfovarsel` publiserer til `team-esyfo.dinesykmeldte-hendelser-v2` |
| Ditt sykefravær            | Sykmeldtflaten                   | `esyfovarsel` publiserer til `flex.ditt-sykefravaer-melding`         |
| Fysiske brev               | Brevpost                         | `esyfovarsel` sender til distribusjon via journalpost                |

## Hvilken domeneapp bruker hvilken varsling?

| Domeneapp(er) som skriver til varselbussen                                        | Typiske hendelser                            | Varslingsflater som brukes                                                                                                                                                                       |
| --------------------------------------------------------------------------------- | -------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `isdialogmote`, `syfomotebehov`                                                   | Dialogmøte og møtebehov                      | **Sykmeldt:** Brukernotifikasjoner og Ditt sykefravær. **Arbeidsgiver/nærmeste leder:** Dine Sykmeldte og arbeidsgivernotifikasjoner på Min side arbeidsgiver. **Mikrofrontend:** `syfo-dialog`. |
| `syfooppfolgingsplanservice`, `syfo-oppfolgingsplan-backend`, `isoppfolgingsplan` | Oppfølgingsplan                              | **Sykmeldt:** Brukernotifikasjoner. **Arbeidsgiver/nærmeste leder:** Dine Sykmeldte og arbeidsgivernotifikasjoner.                                                                               |
| `aktivitetskrav-backend`                                                          | Aktivitetskrav                               | **Sykmeldt:** Brukernotifikasjoner eller fysisk brev. **Mikrofrontend:** `syfo-aktivitetskrav`.                                                                                                  |
| `meroppfolging-backend`, `ismeroppfolging`                                        | Mer veiledning og kartleggingsspørsmål       | **Sykmeldt:** Brukernotifikasjoner. **Ditt sykefravær:** Mer veiledning. **Mikrofrontend:** `syfo-meroppfolging`.                                                                                |
| `isarbeidsuforhet`                                                                | Arbeidsuførhet forhåndsvarsel                | **Sykmeldt:** fysisk brev.                                                                                                                                                                       |
| `isfrisktilarbeid`                                                                | Vedtak om friskmelding til arbeidsformidling | **Sykmeldt:** fysisk brev.                                                                                                                                                                       |
| `ismanglendemedvirkning`                                                          | Manglende medvirkning                        | **Sykmeldt:** fysisk brev.                                                                                                                                                                       |

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
