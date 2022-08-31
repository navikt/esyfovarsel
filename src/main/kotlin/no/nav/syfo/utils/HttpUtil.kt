package no.nav.syfo.utils

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

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
    }
}

suspend fun post(requestURL: String, requestBody: Any, token: String?, requestHeaders: HashMap<String, String>): HttpResponse {
    return httpClient().post(requestURL) {
        headers {
            token ?: append(HttpHeaders.Authorization, "Bearer $token")
            for (header in requestHeaders) {
                append(header.key, header.value)
            }
        }
        body = requestBody
    }
}

suspend fun get(requestURL: String, token: String?, requestHeaders: HashMap<String, String>): HttpResponse {
    return httpClient().get(requestURL) {
        headers {
            token ?: append(HttpHeaders.Authorization, "Bearer $token")
            for (requestHeader in requestHeaders) {
                append(requestHeader.key, requestHeader.value)
            }
        }
    }
}


