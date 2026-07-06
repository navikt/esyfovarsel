# Lokal utvikling

## Krav

Du trenger Java 25, Docker og [mise](https://mise.jdx.dev/).

## Start lokale avhengigheter

```bash
mise run docker-up
```

Dette starter Postgres på `localhost:5432`, Kafka på `localhost:9092` og Kafka UI på `http://localhost:9080`.

Schema Registry finnes i `docker-compose.kafka.yml`, men `mise run docker-up` starter den ikke fordi esyfovarsel ikke bruker den lokalt.

## Start appen

```bash
mise run start
```

Appen bruker `KTOR_ENV=local` som standard. Den kobler seg mot databasen `esyfovarsel_dev`, kjører Flyway ved oppstart og lytter på Kafka uten TLS.

Sjekk helse-endepunktene:

- `http://localhost:8080/isAlive`
- `http://localhost:8080/isReady`

## Test lokal Kafka- og Arbeidsgivernotifikasjon-flyt

Opprett topicet hvis det ikke allerede finnes (Det opprettes dersom man starter app):

```bash
docker exec broker kafka-topics --bootstrap-server broker:29092 --create --if-not-exists --topic team-esyfo.varselbus
```

Publiser en testmelding til lokal Kafka:

```bash
bash scripts/publish-varselbus-message.sh scripts/messages/narmeste-leder-dialogmote-motebehov-tilbakemelding.json
```

Scriptet bruker disse standardverdiene lokalt:

- broker-container: `broker`
- bootstrap-server: `broker:29092`
- topic: `team-esyfo.varselbus`
- key: `local-key`
- key separator: `@@@`

Du kan overstyre med miljøvariabler eller argument:

```bash
KAFKA_MESSAGE_KEY=test-key bash scripts/publish-varselbus-message.sh \
  scripts/messages/narmeste-leder-dialogmote-motebehov-tilbakemelding.json
```

```bash
bash scripts/publish-varselbus-message.sh \
  scripts/messages/narmeste-leder-dialogmote-motebehov-tilbakemelding.json \
  annen-key \
  annet-topic
```

Fødselsnumrene i eksempelfilen er syntetiske testnumre. Ikke bruk reelle personidenter i lokal testdata eller meldinger.

Den lokale fake-produsenten logger bare tekniske og ikke-sensitive felt. Lokal fake-støtte er tilrettelagt for Arbeidsgivernotifikasjon-flyten. Andre utgående klienter er ikke fullfaket lokalt og peker fortsatt mot lokale eller placeholder-URL-er, så enkelte Kafka-hendelser kan feile hvis de treffer andre kodestier.

Du kan også sende meldinger via Kafka UI på `http://localhost:9080`.

## Lokal jobbkjøring

Kjør jobben én gang lokalt:

```bash
mise run start-job
```

Dette starter appen med `JOB=true` og leser `src/main/resources/localEnvJob.json`.

`mise run start-job` kaller `http://localhost:8080/job/trigger`, så appen må allerede kjøre lokalt med `mise run start`.
