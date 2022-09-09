package no.nav.syfo.auth

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.get
import io.ktor.serialization.jackson.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.AuthEnv
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector

val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
    engine {
        customizeClient {
            setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
        }
    }
}

fun getWellKnown(wellKnownUrl: String) = runBlocking { HttpClient(Apache, proxyConfig).get(wellKnownUrl).body<WellKnown>() }

fun hasLoginserviceIdportenClientIdAudience(credentials: JWTCredential, loginserviceIdportenClientId: List<String>): Boolean {
    val isValid = loginserviceIdportenClientId.any { credentials.payload.audience.contains(it) }
    if (!isValid) {
        log.warn("Could not authorize user call")
    }
    return isValid
}

fun validBasicAuthCredentials(authEnv: AuthEnv, credentials: UserPasswordCredential): Boolean {
    val isValid = credentials.name == authEnv.serviceuserUsername && credentials.password == authEnv.serviceuserPassword
    if (!isValid) {
        log.error("System call attempting to authenticate with invalid credentials: ${credentials.name}/${credentials.password}")
    }
    return isValid
}

fun erNiva4(credentials: JWTCredential): Boolean {
    return "Level4" == credentials.payload.getClaim("acr").asString()
}
