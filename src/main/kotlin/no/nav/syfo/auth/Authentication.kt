package no.nav.syfo.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.UserIdPrincipal
import io.ktor.auth.authenticate
import io.ktor.auth.basic
import io.ktor.auth.jwt.*
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.routing.routing
import java.net.URL
import java.util.concurrent.TimeUnit
import no.nav.syfo.AuthEnv
import no.nav.syfo.api.admin.registerAdminApi
import no.nav.syfo.api.job.registerJobTriggerApi
import no.nav.syfo.api.maxdate.registerSykepengerMaxDateAzureApi
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

        val jwtIssuerList = listOf(
            JwtIssuer(
                acceptedAudienceList = listOf(authEnv.clientId),
                jwtIssuerType = JwtIssuerType.INTERNAL_AZUREAD,
                wellKnown = getWellKnown(
                    wellKnownUrl = authEnv.aadAppWellKnownUrl,
                ),
            ),
        )

        jwtIssuerList.forEach {
            configureJwt(
                jwtIssuer = it,
            )
        }
    }

}

private fun Authentication.Configuration.configureJwt(
    jwtIssuer: JwtIssuer,
) {
    val jwkProvider = JwkProviderBuilder(URL(jwtIssuer.wellKnown.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()
    jwt(name = jwtIssuer.jwtIssuerType.name) {
        verifier(
            jwkProvider = jwkProvider,
            issuer = jwtIssuer.wellKnown.issuer,
        )
        validate { credential ->
            val credentialsHasExpectedAudience = credential.inExpectedAudience(
                expectedAudience = jwtIssuer.acceptedAudienceList
            )
            if (credentialsHasExpectedAudience) {
                JWTPrincipal(credential.payload)
            } else {
                log.warn("Auth: Unexpected audience for jwt ${credential.payload.issuer}, ${credential.payload.audience}")
                null
            }
        }
    }
}

private fun JWTCredential.inExpectedAudience(expectedAudience: List<String>) = expectedAudience.any {
    this.payload.audience.contains(it)
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
        authenticate(JwtIssuerType.INTERNAL_AZUREAD.name) {
            registerSykepengerMaxDateAzureApi(sykepengerMaxDateService)
        }
    }
}

fun Application.setupRoutesWithAuthentication(
    varselSender: VarselSender,
    replanleggingService: ReplanleggingService,
    sykepengerMaxDateService: SykepengerMaxDateService,
    authEnv: AuthEnv,
) {
    val wellKnownTokenX = getWellKnown(authEnv.tokenXWellKnownUrl)
    val jwkProviderTokenX = JwkProviderBuilder(URL(wellKnownTokenX.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    setupAuthentication(authEnv, jwkProviderTokenX, wellKnownTokenX.issuer)

    routing {
        authenticate("auth-basic") {
            registerAdminApi(replanleggingService)
            registerJobTriggerApi(varselSender)
        }
        authenticate(JwtIssuerType.INTERNAL_AZUREAD.name) {
            registerSykepengerMaxDateAzureApi(sykepengerMaxDateService)
        }
        authenticate("tokenx") {
            registerSykepengerMaxDateRestApi(sykepengerMaxDateService)
        }
    }
}
