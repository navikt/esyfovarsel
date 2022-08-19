package no.nav.syfo.consumer.syfosyketilfelle

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.TokenConsumer
import no.nav.syfo.kafka.consumers.oppfolgingstilfelle.domain.Oppfolgingstilfelle39Uker
import no.nav.syfo.kafka.consumers.oppfolgingstilfelle.domain.OppfolgingstilfellePerson
import no.nav.syfo.service.SyketilfelleInterface
import no.nav.syfo.utils.httpClient
import org.slf4j.LoggerFactory
import java.time.LocalDate

open class SyfosyketilfelleConsumer(urlEnv: UrlEnv, private val tokenConsumer: TokenConsumer) : SyketilfelleInterface {
    private val client = httpClient()
    private val basepath = urlEnv.syfosyketilfelleUrl
    private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.SyfosyketilfelleConsumer")

    suspend fun getOppfolgingstilfelle(aktorId: String): OppfolgingstilfellePerson? {
        val requestURL = "$basepath/kafka/oppfolgingstilfelle/beregn/$aktorId"
        val stsAccessToken = tokenConsumer.getToken(null)
        val bearerTokenString = "Bearer $stsAccessToken"

        val response = client.get<HttpResponse>(requestURL) {
            headers {
                append(HttpHeaders.Authorization, bearerTokenString)
                append(HttpHeaders.Accept, ContentType.Application.Json)
            }
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                response.receive<OppfolgingstilfellePerson>()
            }
            HttpStatusCode.NoContent -> {
                log.info("Could not get oppfolgingstilfelle: No content found in the response body")
                null
            }
            HttpStatusCode.Unauthorized -> {
                log.error("Could not get oppfolgingstilfelle: Unable to authorize")
                throw RuntimeException("Could not get oppfolgingstilfelle: Unable to authorize")
            }
            else -> {
                log.error("Could not get oppfolgingstilfelle: $response")
                throw RuntimeException("Could not get oppfolgingstilfelle: $response")
            }
        }
    }

    open suspend fun getOppfolgingstilfelle39Uker(aktorId: String): Oppfolgingstilfelle39Uker? {
        val requestURL = "$basepath/kafka/oppfolgingstilfelle/beregn/$aktorId/39ukersvarsel"
        val stsAccessToken = tokenConsumer.getToken(null)
        val bearerTokenString = "Bearer $stsAccessToken"

        val response = client.get<HttpResponse>(requestURL) {
            headers {
                append(HttpHeaders.Authorization, bearerTokenString)
                append(HttpHeaders.Accept, ContentType.Application.Json)
            }
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                response.receive<Oppfolgingstilfelle39Uker>()
            }
            HttpStatusCode.NoContent -> {
                log.info("Could not get Oppfolgingstilfelle (39 uker): No content found in the response body")
                null
            }
            HttpStatusCode.Unauthorized -> {
                log.error("Could not get oppfolgingstilfelle (39 uker): Unable to authorize")
                throw RuntimeException("Could not get oppfolgingstilfelle (39 uker): Unable to authorize")
            }
            else -> {
                log.error("Could not get oppfolgingstilfelle (39 uker): $response")
                throw RuntimeException("Could not get oppfolgingstilfelle (39 uker): $response")
            }
        }
    }

    override suspend fun getOppfolgingstilfelle39UkerCommon(fnr: String, aktorId: String): Oppfolgingstilfelle39Uker? {
        return getOppfolgingstilfelle39Uker(aktorId)
    }
}

class LocalSyfosyketilfelleConsumer(urlEnv: UrlEnv, tokenConsumer: TokenConsumer) : SyfosyketilfelleConsumer(urlEnv, tokenConsumer) {
    override suspend fun getOppfolgingstilfelle39Uker(aktorId: String): Oppfolgingstilfelle39Uker {
        return Oppfolgingstilfelle39Uker(
            aktorId,
            16,
            287,
            LocalDate.now().minusWeeks(40),
            LocalDate.now().plusWeeks(1)
        )
    }
}
