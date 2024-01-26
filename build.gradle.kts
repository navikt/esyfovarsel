import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

group = "no.nav.syfo"
version = "1.0"

val kluentVersion = "1.73"
val ktorVersion = "2.3.1"
val prometheusVersion = "0.15.0"
val micrometerVersion = "1.8.4"
val kotestVersion = "5.6.2"
val kotestExtensionsVersion = "2.0.0"
val mockkVersion = "1.13.5"
val slf4jVersion = "1.7.36"
val logbackVersion = "1.2.13"
val javaxVersion = "2.1.1"
val logstashEncoderVersion = "7.0.1"
val postgresVersion = "42.6.0"
val hikariVersion = "5.0.1"
val flywayVersion = "7.5.2"
val vaultJdbcVersion = "1.3.9"
val jacksonVersion = "2.15.2"
val postgresEmbeddedVersion = "1.0.0"
val kafkaVersion = "3.6.0"
val avroVersion = "1.11.3"
val confluentVersion = "7.4.0"
val brukernotifikasjonerSchemaVersion = "2.5.1"
val kotlinVersion = "1.8.22"

val githubUser: String by project
val githubPassword: String by project

plugins {
    kotlin("jvm") version "1.9.22"
    id("java")
    id("org.jetbrains.kotlin.plugin.allopen") version "1.5.31"
    id("com.diffplug.spotless") version "6.22.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(19))
    }
}

allOpen {
    annotation("no.nav.syfo.annotation.Mockable")
}

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
    maven(url = "https://packages.confluent.io/maven/")
    maven(url = "https://repo.adeo.no/repository/maven-releases/")
    maven(url = "https://github.com/navikt/vault-jdbc")
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.scala-lang" && requested.name == "scala-library" && (requested.version == "2.13.3")) {
            useVersion("2.13.9")
            because("fixes critical bug CVE-2022-36944 in 2.13.6")
        }
        if (requested.group == "io.netty" && requested.name == "netty-handler" && requested.version == "4.1.92.Final") {
            useVersion("4.1.94.Final")
            because("fixes bug CVE-2023-34462")
        }
        if (requested.group == "com.google.guava" && requested.name == "guava" && requested.version == "30.1.1-jre") {
            useVersion("32.0.0-jre")
            because("fixes bug CVE-2023-2976")
        }
    }
}

dependencies {

    // Ktor server
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-apache-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // API
    implementation("javax.ws.rs:javax.ws.rs-api:$javaxVersion")

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

    // JSON parsing
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    // Kafka
    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")
    implementation("org.apache.kafka:kafka_2.13:$kafkaVersion") {
        exclude(group = "log4j")
    }
    implementation("io.confluent:kafka-avro-serializer:$confluentVersion")
    implementation("org.apache.avro:avro:$avroVersion")
    implementation("com.github.navikt:brukernotifikasjon-schemas:$brukernotifikasjonerSchemaVersion")

    // Test
    testImplementation(kotlin("test"))
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("com.opentable.components:otj-pg-embedded:$postgresEmbeddedVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("io.kotest:kotest-runner-junit5-jvm:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")
    testImplementation("io.kotest.extensions:kotest-assertions-ktor:$kotestExtensionsVersion")

    constraints {
        implementation("org.apache.zookeeper:zookeeper") {
            because("CVE-2023-44981")
            version {
                require("3.8.3")
            }
        }
    }
}

configurations.implementation {
    exclude(group = "com.fasterxml.jackson.module", module = "jackson-module-scala_2.13")
}

tasks {
    create("printVersion") {
        println(project.version)
    }

    withType<ShadowJar> {
        setProperty("zip64", true)
        manifest.attributes["Main-Class"] = "no.nav.syfo.BootstrapApplicationKt"
    }

    withType<Test> {
        useJUnitPlatform()
    }
}
