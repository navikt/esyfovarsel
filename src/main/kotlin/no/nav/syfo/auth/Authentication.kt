package no.nav.syfo.auth

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.routing.*
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
import java.net.URL
import java.util.concurrent.TimeUnit

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
                    hasLoginserviceIdportenClientIdAudience(credentials, authEnv.loginserviceAudience) && isNiva4(
                        credentials
                    ) -> JWTPrincipal(credentials.payload)

                    else -> null
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
    authEnv: AuthEnv
) {
    setupAuthentication(authEnv)
    routing {
        authenticate("loginservice") {
            registerSykepengerMaxDateRestApi(sykepengerMaxDateService)
        }
        authenticate("auth-basic") {
            registerAdminApi(replanleggingService)
            registerJobTriggerApi(varselSender)
        }
        authenticate(JwtIssuerType.INTERNAL_AZUREAD.name) {
            registerSykepengerMaxDateAzureApi(sykepengerMaxDateService)
        }
    }
}
