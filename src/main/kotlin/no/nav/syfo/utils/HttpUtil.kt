package no.nav.syfo.utils

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.logging.*

fun httpClient(): HttpClient {
    return HttpClient(CIO) {
        expectSuccess = false
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.HEADERS
            filter { request ->
                request.url.host.contains("dkif.dev.intern.nav.no")
            }
        }
    }
}
