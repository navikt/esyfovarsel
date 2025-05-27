# esyfovarsel
Varsler for eSYFO. Lytter på topic'en `team-esyfo.varselbus` og videresender varsler til
relevante kanaler (DineSykmeldte, Brukernotifikasjoner og Arbeidsgivernotifikasjoner).
Bruker også til å planlegge 39-ukers varsel for sykmeldt.

## Technologies used
* Kotlin
* Ktor
* Gradle
* Kotest
* Postgres
* Kafka

### Building the application
Run `./gradlew build`    

### Running app locally

- Run `docker-compose up` in a terminal at the project root
- Start BootstrapApplication

### Running job locally

- Run `docker-compose up` in a terminal at the project root
- Start one instance of BootstrapApplication
- Start another instance of BootStrapApplication with environment variable `JOB=true`
- This will run the job once
