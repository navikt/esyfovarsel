# esyfovarsel

Varsler for eSYFO. Appen lytter på Kafka-topicet `team-esyfo.varselbus` og videresender varsler til relevante kanaler som Dine Sykmeldte, Brukernotifikasjoner og Arbeidsgivernotifikasjoner.

## Lokal kjøring

Du trenger Java 21, Docker og [mise](https://mise.jdx.dev/).

1. Start lokale avhengigheter:
   ```bash
   mise run docker-up
   ```
   Dette starter Postgres på `localhost:5432`, Kafka på `localhost:9092` og Kafka UI på `http://localhost:9080`.
2. Start appen:
   ```bash
   mise run start
   ```
   Appen bruker `KTOR_ENV=local` som standard. Den kobler seg mot databasen `esyfovarsel_dev`, kjører Flyway ved oppstart og lytter på Kafka uten TLS.
3. Sjekk helse-endepunktene:
   - `http://localhost:8080/isAlive`
   - `http://localhost:8080/isReady`

Schema Registry finnes i `docker-compose.kafka.yml`, men `mise run docker-up` starter den ikke fordi esyfovarsel ikke bruker den lokalt.

## Test av lokal Kafka- og Arbeidsgivernotifikasjon-flyt

Opprett topicet hvis det ikke allerede finnes:

```bash
docker exec broker kafka-topics --bootstrap-server broker:29092 --create --if-not-exists --topic team-esyfo.varselbus
```

Send en testmelding som går til den lokale fake-produsenten for Arbeidsgivernotifikasjon:

```bash
printf 'local-key@@@{"@type":"NarmesteLederHendelse","type":"NL_DIALOGMOTE_MOTEBEHOV_TILBAKEMELDING","ferdigstill":false,"data":{"tilbakemelding":"Lokal test"},"narmesteLederFnr":"00000000000","arbeidstakerFnr":"12345678910","orgnummer":"999999999"}\n' | \
docker exec -i broker kafka-console-producer --bootstrap-server broker:29092 --topic team-esyfo.varselbus --property parse.key=true --property key.separator='@@@'
```

Fødselsnumrene i eksempelet over er syntetiske testnumre. Ikke bruk reelle personidenter i lokal testdata eller meldinger.

Den lokale fake-produsenten logger bare tekniske og ikke-sensitive felt. Lokal fake-støtte er tilrettelagt for Arbeidsgivernotifikasjon-flyten. Andre utgående klienter er ikke fullfaket lokalt og peker fortsatt mot lokale eller placeholder-URL-er, så enkelte Kafka-hendelser kan feile hvis de treffer andre kodestier. Du kan også sende meldinger via Kafka UI på `http://localhost:9080`.

## Lokal jobbkjøring

Kjør jobben én gang lokalt:

```bash
mise run start-job
```

Dette starter appen med `JOB=true` og leser `src/main/resources/localEnvJob.json`.

`mise run start-job` kaller `http://localhost:8080/job/trigger`, så appen må allerede kjøre lokalt med `mise run start`.

## Nyttige kommandoer

- Bygg og test:
  ```bash
  ./gradlew build
  ```
- Flyway-info mot lokal database:
  ```bash
  mise run flyway-info
  ```
- Stopp lokale avhengigheter:
  ```bash
  mise run docker-down
  ```

## For Nav-ansatte

- [#esyfo på Slack](https://nav-it.slack.com/archives/C012X796B4L)
