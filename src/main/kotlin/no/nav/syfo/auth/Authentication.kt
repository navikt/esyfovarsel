package no.nav.syfo.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.routing.routing
import java.net.URL
import java.util.concurrent.TimeUnit
import no.nav.syfo.AuthEnv
import no.nav.syfo.api.job.registerJobTriggerApi
import no.nav.syfo.service.microfrontend.MikrofrontendService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger(Authentication::class.qualifiedName)

fun Application.setupAuthentication(
    authEnv: AuthEnv,
    jwkProviderTokenX: JwkProvider,
    tokenXIssuer: String,
) {
    val jwtIssuerList = listOf(
        JwtIssuer(
            acceptedAudienceList = listOf(authEnv.clientId),
            jwtIssuerType = JwtIssuerType.INTERNAL_AZUREAD,
            wellKnown = getWellKnown(
                wellKnownUrl = authEnv.aadAppWellKnownUrl,
            ),
        ),
    )

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
            verifier(jwkProviderTokenX, tokenXIssuer)
            validate { credentials ->
                when {
                    hasClientIdAudience(credentials, authEnv.tokenXClientId) && isNiva4(credentials) -> {
                        val principal = JWTPrincipal(credentials.payload)
                        BrukerPrincipal(
                            fnr = finnFnrFraToken(principal),
                            principal = principal,
                        )
                    }

                    else -> unauthorized(credentials)
                }
            }
        }

        jwtIssuerList.forEach {
            configureJwt(
                jwtIssuer = it,
            )
        }
    }
}

private fun AuthenticationConfig.configureJwt(
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
                expectedAudience = jwtIssuer.acceptedAudienceList,
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
    mikrofrontendService: MikrofrontendService,
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
        authenticate("auth-basic") {
            registerJobTriggerApi(mikrofrontendService)
        }
    }
}

fun Application.setupRoutesWithAuthentication(
    mikrofrontendService: MikrofrontendService,
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
            registerJobTriggerApi(mikrofrontendService)
        }
    }
}
