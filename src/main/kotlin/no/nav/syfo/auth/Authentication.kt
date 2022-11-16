package no.nav.syfo.auth

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.UserIdPrincipal
import io.ktor.auth.authenticate
import io.ktor.auth.basic
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.routing.routing
import java.net.URL
import java.util.concurrent.TimeUnit
import no.nav.syfo.AuthEnv
import no.nav.syfo.api.admin.registerAdminApi
import no.nav.syfo.api.job.registerJobTriggerApi
import no.nav.syfo.api.maxdate.registerForelopigBeregnetSluttPaSykepengerRestApi
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.job.VarselSender
import no.nav.syfo.service.ReplanleggingService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.varsel.JwtValidation")

fun Application.setupAuthentication(
    authEnv: AuthEnv
) {
    val wellKnown = getWellKnown(authEnv.loginserviceDiscoveryUrl)
    val jwkProvider = JwkProviderBuilder(URL(wellKnown.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    install(Authentication) {
        basic("auth-basic") {
            realm = "Access to the '/admin/' path"
            validate { credentials ->
                when {
                    validBasicAuthCredentials(authEnv, credentials) -> UserIdPrincipal(credentials.name)
                    else -> null
                }
            }
        }
        jwt(name = "loginservice") {
            verifier(jwkProvider, wellKnown.issuer)
            validate { credentials ->
                when {
                    hasLoginserviceIdportenClientIdAudience(credentials, authEnv.loginserviceAudience) && isNiva4(credentials) -> JWTPrincipal(credentials.payload)
                    else -> null
                }
            }
        }
    }
}

fun Application.setupLocalRoutesWithAuthentication(
    varselSender: VarselSender,
    replanleggingService: ReplanleggingService,
    database: DatabaseInterface,
    authEnv: AuthEnv
) {
    install(Authentication) {
        basic("auth-basic") {
            realm = "Access to the '/admin/' path"
            validate { credentials ->
                when {
                    validBasicAuthCredentials(authEnv, credentials) -> UserIdPrincipal(credentials.name)
                    else -> null
                }
            }
        }
    }

    routing {
        registerForelopigBeregnetSluttPaSykepengerRestApi(database)
        registerAdminApi(replanleggingService)

        authenticate("auth-basic") {
            registerJobTriggerApi(varselSender)
        }
    }
}

fun Application.setupRoutesWithAuthentication(
    varselSender: VarselSender,
    replanleggingService: ReplanleggingService,
    database: DatabaseInterface,
    authEnv: AuthEnv
) {
    setupAuthentication(authEnv)
    routing {
        authenticate("loginservice") {
            registerForelopigBeregnetSluttPaSykepengerRestApi(database)
        }
        authenticate("auth-basic") {
            registerAdminApi(replanleggingService)
            registerJobTriggerApi(varselSender)
        }
    }
}
