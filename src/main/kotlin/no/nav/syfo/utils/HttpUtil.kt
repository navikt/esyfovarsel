package no.nav.syfo.utils

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson

fun httpClient(): HttpClient {
    return HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }
}

fun httpClientWithRetry(expectSuccess: Boolean = false): HttpClient {
    return HttpClient(CIO) {
        install(HttpRequestRetry) {
            retryOnExceptionIf(2) { _, cause ->
                cause !is ClientRequestException
            }
            constantDelay(500L)
        }
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
        }
        this.expectSuccess = expectSuccess
    }
}
