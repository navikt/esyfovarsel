package no.nav.syfo.auth

import com.auth0.jwk.JwkProvider
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.UserIdPrincipal
import io.ktor.auth.authenticate
import io.ktor.auth.basic
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.routing.routing
import no.nav.syfo.AuthEnv
import no.nav.syfo.api.admin.registerAdminApi
import no.nav.syfo.api.job.registerJobTriggerApi
import no.nav.syfo.api.maxdate.registerSykepengerMaxDateRestApi
import no.nav.syfo.job.VarselSender
import no.nav.syfo.service.ReplanleggingService
import no.nav.syfo.service.SykepengerMaxDateService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.varsel.JwtValidation")

fun Application.setupAuthentication(
    authEnv: AuthEnv,
    jwkProviderTokenX: JwkProvider,
    tokenXIssuer: String,
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
        jwt(name = "tokenx") {
            authHeader {
                if (it.getToken() == null) {
                    return@authHeader null
                }
                return@authHeader HttpAuthHeader.Single("Bearer", it.getToken()!!)
            }
            verifier(jwkProviderTokenX, tokenXIssuer)
            validate { credentials ->
                when {
                    hasClientIdAudience(credentials, authEnv.tokenXClientId) && isNiva4(credentials) -> {
                        val principal = JWTPrincipal(credentials.payload)
                        BrukerPrincipal(
                            fnr = finnFnrFraToken(principal),
                            principal = principal,
                            token = this.getToken()!!
                        )
                    }
                    else -> unauthorized(credentials)
                }
            }
        }
    }

}

fun Application.setupLocalRoutesWithAuthentication(
    varselSender: VarselSender,
    replanleggingService: ReplanleggingService,
    sykepengerMaxDateService: SykepengerMaxDateService,
    authEnv: AuthEnv,

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
        registerSykepengerMaxDateRestApi(sykepengerMaxDateService)
        registerAdminApi(replanleggingService)

        authenticate("auth-basic") {
            registerJobTriggerApi(varselSender)
        }
    }
}

fun Application.setupRoutesWithAuthentication(
    varselSender: VarselSender,
    replanleggingService: ReplanleggingService,
    sykepengerMaxDateService: SykepengerMaxDateService,
    authEnv: AuthEnv,
    jwkProviderTokenX: JwkProvider,
    tokenXIssuer: String,
) {
    setupAuthentication(authEnv, jwkProviderTokenX, tokenXIssuer)
    routing {
        authenticate("auth-basic") {
            registerAdminApi(replanleggingService)
            registerJobTriggerApi(varselSender)
        }
        authenticate("tokenx") {
            registerSykepengerMaxDateRestApi(sykepengerMaxDateService)
        }
    }
}
