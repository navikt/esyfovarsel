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

suspend fun post(requestURL: String, requestBody: Any, bearerToken: String?, requestHeaders: HashMap<String, String>): HttpResponse {
    return httpClient().post(requestURL) {
        headers {
            bearerToken ?: append(HttpHeaders.Authorization, "Bearer $bearerToken")
            for (header in requestHeaders) {
                append(header.key, header.value)
            }
        }
        body = requestBody
    }
}

suspend fun postWithParameter(requestURL: String, requestBody: Any?, bearerToken: String?, requestHeaders: HashMap<String, String>, parameter: Pair<String, Any>): HttpResponse {
    return httpClient().post(requestURL) {
        headers {
            bearerToken ?: append(HttpHeaders.Authorization, "Bearer $bearerToken")
            for (header in requestHeaders) {
                append(header.key, header.value)
            }
        }
        parameter(parameter.first, parameter.second)
        body = requestBody!!
    }
}

suspend fun get(requestURL: String, bearerToken: String?, requestHeaders: HashMap<String, String>): HttpResponse {
    return httpClient().get(requestURL) {
        headers {
            bearerToken ?: append(HttpHeaders.Authorization, "Bearer $bearerToken")
            for (requestHeader in requestHeaders) {
                append(requestHeader.key, requestHeader.value)
            }
        }
    }
}



