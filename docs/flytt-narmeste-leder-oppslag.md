# Flytt oppslag av nærmeste leder til esyfo-narmesteleder

Sak: [navikt/esyfovarsel#1117](https://github.com/navikt/esyfovarsel/issues/1117)

## Kort fortalt

esyfovarsel skal hente nærmeste leder fra team eSyfos eget API, `esyfo-narmesteleder`, i stedet for fra team sykmelding. Lederne får de samme varslene som før, og fødselsnummeret sendes ikke lenger i en header. Da kan team sykmelding avvikle det gamle endepunktet.

## Akseptansekriterier

- [ ] esyfovarsel henter aktiv nærmeste leder med `POST /internal/api/v1/lookup` i `esyfo-narmesteleder`, i dev og prod.
- [ ] Den sykmeldtes fødselsnummer sendes bare i request body. Det står ikke i URL, query-parametre eller headere, og det logges ikke.
- [ ] Varsler til nærmeste leder får samme leder-id, fødselsnummer og e-postadresser som med dagens oppslag. Det er tre bevisste unntak:
  - Har den sykmeldte flere aktive ledere i virksomheten, velges lederen med nyeste `aktivFom`.
  - Kommaseparerte adresser splittes.
  - Ugyldige adresser utelates.
- [ ] Flere adresser i `emailAddresses` gir én e-post per adresse.
- [ ] Er `emailAddresses` tom, blir utfallet det samme som når e-posten mangler i dag: Varselet sendes ikke, og det logges uten personopplysninger.
- [ ] Mangler det en aktiv leder, eller feiler oppslaget, blir utfallet det samme som i dag.
- [ ] Tester dekker requesten, mappingen av svaret (også `lineManager: null`, flere e-postadresser og tom `emailAddresses`) og feilsvar.
- [ ] esyfovarsel har ikke lenger konfigurasjon eller `accessPolicy.outbound` mot `narmesteleder` i `teamsykmelding`.

## Låste beslutninger

- Svaret mappes til dagens felter. `emailAddresses` slås sammen med `;`. Koden som bygger arbeidsgivernotifikasjoner, inkludert splittingen fra [#1055](https://github.com/navikt/esyfovarsel/pull/1055), endres ikke.

  | `NarmesteLederRelasjon` | Svar fra `/lookup` |
  |---|---|
  | `narmesteLederId` | `lineManager.id` |
  | `narmesteLederFnr` | `lineManager.nationalIdentificationNumber` |
  | `narmesteLederEpost` | `lineManager.emailAddresses`, slått sammen med `;` |
  | `narmesteLederEpost = null` | `lineManager.emailAddresses` er tom |
  | `null` (ingen leder) | `lineManager: null` |

- En tom `emailAddresses` mappes til `narmesteLederEpost = null`. `NarmesteLederService.hasNarmesteLederInfo`, `DialogmoteInnkallingNarmesteLederVarselService` og `OppfolgingsplanVarselService` stopper da varselet og logger uten personopplysninger. Utfallet blir i praksis som i dag, siden en ugyldig adresse i dag får kallet til arbeidsgivernotifikasjoner (fager) til å feile.

- Svarer endepunktet noe annet enn `200` med gyldig svar, eller feiler kallet, gir oppslaget `null` («ingen leder»), som i dag. Klienten skal ikke kaste unntak slik [referanseklienten](https://github.com/navikt/syfo-oppfolgingsplan-backend/blob/main/src/main/kotlin/no/nav/syfo/narmesteleder/client/NarmestelederClient.kt) i `syfo-oppfolgingsplan-backend` gjør.
- Forutsetning for prod: [navikt/esyfo-narmesteleder#664](https://github.com/navikt/esyfo-narmesteleder/issues/664) må være i prod før merge til `main` (som deployer til prod). Saken gjør at `/lookup` forkaster ugyldige e-postadresser med `parseSeparatedList` i stedet for å svare `500`. Uten denne rettingen ville en leder med én ugyldig adresse ikke bli funnet, og varselet om dialogmøteinnkalling til lederen ville falle bort uten nytt forsøk. `esyfo-narmesteleder` teller forkastede adresser i en metrikk, og de ugyldige adressene i databasen rettes etter en egen plan. esyfovarsel teller ikke selv.
- Behandlingskatalogen trenger ikke oppdateres.
- Fødselsnummer, e-post og annet varselinnhold holdes ute av vanlige logger. Svaret inneholder lederens fødselsnummer og e-post, og feilmeldinger fra deserialisering kan gjengi verdier fra svaret. Derfor logges verken responsbody eller unntaksmeldinger.

## Implementasjonskontekst

- I dag kaller `NarmesteLederConsumer` `GET /sykmeldt/narmesteleder?orgnummer=` med fødselsnummeret i headeren `Sykmeldt-Fnr`. Klienten gir `null` ved alt annet enn `200` og ved unntak.
- Tre tjenester bruker oppslaget gjennom `NarmesteLederService`, og de leser bare id, fødselsnummer og e-post. Slik håndterer de en manglende leder:
  - `DialogmoteInnkallingNarmesteLederVarselService` logger og sender ikke varselet.
  - `OppfolgingsplanVarselService` sender ikke forespørselsvarselet. Mangler lederen når en oppfølgingsplansak skal lages, kaster tjenesten `OppfolgingsplanVarselRetryableException`.
  - `ArbeidsgiverNotifikasjonService` svarer `false`. `SenderFacade` lagrer da varselet for nytt forsøk, men bare i `sendTilArbeidsgiverNotifikasjonMedRetrylagring`.
- `splitEpostadresser` deler e-post på `;` før det lages eksterne varsler for beskjed, oppgave og for ny og oppdatert kalenderavtale. esyfovarsel lagrer ikke e-posten.
- Om `/lookup` i `esyfo-narmesteleder` (lest på commit [`f9d8e39`](https://github.com/navikt/esyfo-narmesteleder/tree/f9d8e3905a941821698e5cba7fa35d6abd497c51); se også [OpenAPI-spesifikasjonen](https://github.com/navikt/esyfo-narmesteleder/blob/main/src/main/resources/openapi/internal-documentation.yaml)):
  - Endepunktet returnerer relasjonen med `aktiv_tom IS NULL`, uten krav om aktiv sykmelding. Har den sykmeldte flere aktive ledere, velges den med nyeste `aktivFom`. I dag er valget tilfeldig.
  - `id` og e-postfeltet kommer fra leesah-topicet (`team-esyfo.syfo-narmesteleder-leesah`), altså samme kilde som i dag. At id-en blir den samme, kontrolleres i dev.
  - `emailAddresses` lages fra det samme tekstfeltet, splittet på `,` og `;` og trimmet. Kommaseparerte adresser blir dermed splittet riktig. I dag sendes de som én adresse.
  - Er én av adressene i feltet ugyldig, svarer hele oppslaget `500` fram til [navikt/esyfo-narmesteleder#664](https://github.com/navikt/esyfo-narmesteleder/issues/664) er rettet. Etter rettingen utelates ugyldige adresser. Er ingen gyldige, er listen tom.
  - Endepunktet svarer `400` ved ugyldig fødselsnummer eller orgnummer, `401` ved ugyldig token og `403` når appen ikke er forhåndsgodkjent.
- `esyfovarsel` står verken i `accessPolicy.inbound` eller i `AZURE_APP_PRE_AUTHORIZED_APPS` hos `esyfo-narmesteleder`. `AZURE_APP_PRE_AUTHORIZED_APPS` er satt eksplisitt i manifestet der, så begge må endres.
- Env-navnene `NARMESTELEDER_URL` og `NARMESTELEDER_SCOPE` beholdes, med verdiene fra saken. Den utgående regelen mot `syfosmregister` i `teamsykmelding` skal stå. Token hentes med den eksisterende `AzureAdTokenConsumer`.
- En PR som ikke er draft, deployes til dev. Merge til `main` deployes til prod. `esyfovarsel-job` kaller bare `/job/trigger`, så det er bare `esyfovarsel` som trenger tilgang.

## Bevis

- Ny `NarmesteLederConsumerSpek` som kjører mot en innebygd mock-server på `narmestelederUrl` (port 9097) og mot AAD-mocken, etter mønsteret i `DkifConsumerSpek` og `MockServers`. Testene dekker:
  - Request: `POST` til riktig sti, med JSON-body med fødselsnummer og orgnummer og med `Authorization: Bearer`. Fødselsnummeret finnes verken i URI-en eller i noen header.
  - Mapping: én adresse, flere adresser (`a;b`), tom liste (`narmesteLederEpost = null`) og `lineManager: null` (`null`).
  - Feil: `400`, `401`, `403`, `500`, ugyldig JSON og nedetid gir `null`.
  - Logger: ingen logghendelse inneholder den sykmeldtes fødselsnummer, lederens fødselsnummer eller e-post. Dette sjekkes med logbacks `ListAppender`.
- `ArbeidsgiverNotifikasjonSpek` viser allerede at `;`-separerte adresser gir én ekstern e-post per adresse for oppgave og for oppdatert kalenderavtale. Beskjed bruker samme kode som oppgave, og ny kalenderavtale bruker den samme `splitEpostadresser`.
- Eksisterende tjenestetester skal passere uten endret oppførsel.
- Kjør `./gradlew test --tests 'no.nav.syfo.consumer.narmesteLeder.*'` og deretter `./gradlew build`. Bygget krever Docker.
- Kontroll i dev med en syntetisk bruker: oppslaget svarer `200`, gir samme `narmesteLederId` som en eksisterende sak og gir de forventede mottakerne.
- Kontroll i prod etter merge: oppslaget gir ingen `401`, `403` eller `5xx`, og loggmeldingen «narmesteLederRelasjon er null» øker ikke.

## Ikke-mål

- Ingen endring i koden som bygger arbeidsgivernotifikasjoner, eller i hvordan tjenestene håndterer manglende leder.
- Ingen ny retry, metrikk eller alarm (jf. [#1094](https://github.com/navikt/esyfovarsel/issues/1094)).
- Ingen endring av e-postvalideringen i `/lookup` fra esyfovarsel. Den rettes i [navikt/esyfo-narmesteleder#664](https://github.com/navikt/esyfo-narmesteleder/issues/664).
- Ingen ny tokenmekanisme, for eksempel Texas.
- Ingen flytting av varsler til `syfo-budstikka`.

## Avhengigheter

- `esyfo-narmesteleder` legger `esyfovarsel` inn i `accessPolicy.inbound` og `AZURE_APP_PRE_AUTHORIZED_APPS`, med esyfovarsels egen clientId i hvert miljø. Dev må være på plass før PR-en markeres klar, og prod før merge.
- [navikt/esyfo-narmesteleder#664](https://github.com/navikt/esyfo-narmesteleder/issues/664) er i prod før merge til `main`.
- [navikt/esyfo-narmesteleder#432](https://github.com/navikt/esyfo-narmesteleder/issues/432) oppdateres når prod er verifisert. Da kan team sykmelding fjerne tilgangen for esyfovarsel.
- [#1094](https://github.com/navikt/esyfovarsel/issues/1094): Flyttes varslene til `syfo-budstikka` før det gamle endepunktet avvikles, trengs ikke denne endringen.

## Forslag til gjennomføring (ikke besluttet)

- Trim `NarmesteLederRelasjon` til de tre feltene som brukes, og slett `Tilgang` og `NarmestelederResponse`. Tjenestene endres ikke.
- Behold `id` som tekst, uten UUID-parsing, slik at sak-nøkler og lenker matcher eksakt.
- Logg statuskode og unntaksklasse med SLF4J `addKeyValue`, uten orgnummer.
- Kast `CancellationException` videre. Et avbrutt kall er ikke et feilet oppslag.
- Samle kode og Nais-konfig i én PR. Rollback betyr å deploye forrige image så lenge det gamle endepunktet finnes.
