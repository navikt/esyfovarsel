package no.nav.syfo.auth

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.routing.*
import no.nav.syfo.AppEnvironment
import no.nav.syfo.api.admin.registerAdminApi
import no.nav.syfo.api.bruker.registerBrukerApi
import no.nav.syfo.service.ReplanleggingService
import no.nav.syfo.service.VarselSendtService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.TimeUnit


val log: Logger = LoggerFactory.getLogger("no.nav.syfo.varsel.JwtValidation")

fun Application.setupAuthentication(
    env: AppEnvironment
) {
    val wellKnown = getWellKnown(env.loginserviceDiscoveryUrl)
    val jwkProvider = JwkProviderBuilder(URL(wellKnown.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()


    install(Authentication) {
        jwt(name = "loginservice") {
            verifier(jwkProvider, wellKnown.issuer)
            validate { credentials ->
                when {
                    hasLoginserviceIdportenClientIdAudience(credentials, env.loginserviceAudience) && erNiva4(credentials) -> JWTPrincipal(credentials.payload)
                    else -> null
                }
            }
        }
        basic("auth-basic") {
            realm = "Access to the '/admin/' path"
            validate { credentials ->
                if (credentials.name == env.commonEnv.serviceuserUsername && credentials.password == env.commonEnv.serviceuserPassword) {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
    }
}

fun Application.setupRoutesWithAuthentication(
    varselSendtService: VarselSendtService,
    replanleggingService: ReplanleggingService,
    appEnv: AppEnvironment
) {
    setupAuthentication(appEnv)
    routing {
        authenticate("loginservice") {
            registerBrukerApi(varselSendtService)
        }
        authenticate("auth-basic") {
            registerAdminApi(replanleggingService)
        }
    }
}
