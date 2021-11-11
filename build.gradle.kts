import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0"

val kluentVersion = "1.39"
val ktorVersion = "1.6.0"
val prometheusVersion = "0.8.1"
val micrometerVersion = "1.7.3"
val spekVersion = "2.0.9"
val mockkVersion = "1.10.2"
val slf4jVersion = "1.7.30"
val logbackVersion = "1.2.3"
val logstashEncoderVersion = "6.3"
val postgresVersion = "42.2.13"
val hikariVersion = "4.0.1"
val flywayVersion = "7.5.2"
val vaultJdbcVersion = "1.3.7"
val jacksonVersion = "2.11.3"
val postgresEmbeddedVersion = "0.13.3"
val kafkaVersion = "2.7.0"
val kafkaEmbeddedVersion = "2.4.0"
val avroVersion = "1.8.2"
val confluentVersion = "5.5.0"
val brukernotifikasjonerSchemaVersion = "1.2021.06.21-08.21-7998a39f216a"

val githubUser: String by project
val githubPassword: String by project

plugins {
    kotlin("jvm") version "1.5.31"
    id("org.jetbrains.kotlin.plugin.allopen") version "1.5.31"
    id("com.diffplug.gradle.spotless") version "3.18.0"
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

allOpen {
    annotation("no.nav.syfo.annotation.Mockable")
}

repositories {
    mavenCentral()
    jcenter()
    maven(url = "https://jitpack.io")
    maven(url = "https://dl.bintray.com/kotlin/ktor")
    maven(url = "https://dl.bintray.com/spekframework/spek-dev")
    maven(url = "https://dl.bintray.com/kotlin/kotlinx/")
    maven(url = "https://packages.confluent.io/maven/")
    maven(url = "https://repo.adeo.no/repository/maven-releases/")
    maven(url = "https://github.com/navikt/vault-jdbc")
}

dependencies {

    // Kotlin / Server
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("io.ktor:ktor-auth:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion")

    // Logging
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    // Metrics and Prometheus
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_pushgateway:$prometheusVersion")

    // Database
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("no.nav:vault-jdbc:$vaultJdbcVersion")

    // JSON parsing
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

    //Kafka
    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")
    implementation("org.apache.kafka:kafka_2.12:$kafkaVersion")
    implementation("io.confluent:kafka-streams-avro-serde:$confluentVersion")
    implementation("io.confluent:kafka-schema-registry:$confluentVersion") {
          exclude(group = "org.slf4j", module = "slf4j-log4j12")
    }
    implementation("org.apache.avro:avro:$avroVersion")
    implementation("com.github.navikt:brukernotifikasjon-schemas:$brukernotifikasjonerSchemaVersion")

    // Test
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "ch.qos.logback", module = "logback-classic")
    }
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("com.opentable.components:otj-pg-embedded:$postgresEmbeddedVersion")
    testImplementation("no.nav:kafka-embedded-env:$kafkaEmbeddedVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runtime-jvm:$spekVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion")
}

configurations.implementation {
    exclude(group = "com.fasterxml.jackson.module", module = "jackson-module-scala_2.12")
}

tasks {
    create("printVersion") {
        println(project.version)
    }

    withType<ShadowJar> {
        manifest.attributes["Main-Class"] = "no.nav.syfo.BootstrapApplicationKt"
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "14"
    }

    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging.showStandardStreams = true
    }

}
