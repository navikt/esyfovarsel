# esyfovarsel
Varsler for eSYFO

## Technologies used
* Kotlin
* Ktor
* Gradle
* Spek
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
