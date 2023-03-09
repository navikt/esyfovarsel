package no.nav.syfo.auth


import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.ApplicationCall
import io.ktor.auth.Principal
import io.ktor.auth.UserPasswordCredential
import io.ktor.auth.jwt.JWTCredential
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.request.header
import java.net.ProxySelector
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.AuthEnv
import org.apache.http.impl.conn.SystemDefaultRoutePlanner

fun validBasicAuthCredentials(authEnv: AuthEnv, credentials: UserPasswordCredential): Boolean {
    val isValid = credentials.name == authEnv.serviceuserUsername && credentials.password == authEnv.serviceuserPassword
    if (!isValid) {
        log.error("System call attempting to authenticate with invalid credentials: ${credentials.name}/${credentials.password}")
    }
    return isValid
}

val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
    install(JsonFeature) {
        serializer = JacksonSerializer {
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

fun getWellKnown(wellKnownUrl: String) = runBlocking { HttpClient(Apache, proxyConfig).get<WellKnown>(wellKnownUrl) }

fun isNiva4(credentials: JWTCredential): Boolean {
    return "Level4" == credentials.payload.getClaim("acr").asString()
}

fun ApplicationCall.getToken(): String? {
    if (request.header("Authorization") != null) {
        return request.header("Authorization")!!.removePrefix("Bearer ")
    }
    return request.cookies.get(name = "selvbetjening-idtoken")
}

fun unauthorized(credentials: JWTCredential): Principal? {
    log.warn(
        "Auth: Unexpected audience for jwt {}, {}",
        StructuredArguments.keyValue("issuer", credentials.payload.issuer),
        StructuredArguments.keyValue("audience", credentials.payload.audience)
    )
    return null
}

fun hasClientIdAudience(credentials: JWTCredential, clientId: String): Boolean {
    return credentials.payload.audience.contains(clientId)
}

fun finnFnrFraToken(principal: JWTPrincipal): String {
    return if (principal.payload.getClaim("pid") != null && !principal.payload.getClaim("pid").asString().isNullOrEmpty()) {
        log.debug("Bruker fnr fra pid-claim")
        principal.payload.getClaim("pid").asString()
    } else {
        log.debug("Bruker fnr fra subject")
        principal.payload.subject
    }
}

data class BrukerPrincipal(
    val fnr: String,
    val principal: JWTPrincipal,
    val token: String,
) : Principal
