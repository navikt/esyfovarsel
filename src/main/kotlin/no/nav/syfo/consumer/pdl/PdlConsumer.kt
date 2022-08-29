package no.nav.syfo.consumer

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.TokenConsumer
import no.nav.syfo.consumer.pdl.*
import no.nav.syfo.isNotGCP
import no.nav.syfo.utils.httpClient
import org.slf4j.LoggerFactory

open class PdlConsumer(private val urlEnv: UrlEnv, private val tokenConsumer: TokenConsumer) {
    private val client = httpClient()
    private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.PdlConsumer")

    open fun getFnr(aktorId: String): String? {
        val response = callPdl(IDENTER_QUERY, aktorId)

        return when (response?.status) {
            HttpStatusCode.OK -> {
                runBlocking {
                    val pdlResponse = response.receive<PdlIdentResponse>().data?.hentIdenter?.identer?.first()?.ident
                    pdlResponse
                }
            }
            HttpStatusCode.NoContent -> {
                log.error("Could not get fnr from PDL: No content found in the response body")
                null
            }
            HttpStatusCode.Unauthorized -> {
                log.error("Could not get fnr from PDL: Unable to authorize")
                null
            }
            else -> {
                log.error("Could not get fnr from PDL: $response")
                null
            }
        }
    }

    fun isBrukerGradertForInformasjon(aktorId: String): Boolean? {
        val response = callPdl(PERSON_QUERY, aktorId)

        return when (response?.status) {
            HttpStatusCode.OK -> {
                runBlocking { response.receive<PdlPersonResponse>().data?.isKode6Eller7() }
            }
            HttpStatusCode.NoContent -> {
                log.error("Could not get adressesperre from PDL: No content found in the response body")
                null
            }
            HttpStatusCode.Unauthorized -> {
                log.error("Could not get adressesperre from PDL: Unable to authorize")
                null
            }
            else -> {
                log.error("Could not get adressesperre from PDL: $response")
                null
            }
        }
    }

    open fun hentPerson(fnr: String): PdlHentPerson? {
        val response = callPdl(PERSON_QUERY, fnr)

        return when (response?.status) {
            HttpStatusCode.OK -> {
                runBlocking {
                    val pdlResponse = response.receive<PdlPersonResponse>().data
                    pdlResponse
                }
            }
            HttpStatusCode.NoContent -> {
                log.error("Could not get navn from PDL: No content found in the response body")
                null
            }
            HttpStatusCode.Unauthorized -> {
                log.error("Could not get navn from PDL: Unable to authorize")
                null
            }
            else -> {
                log.error("Could not get fnr from PDL: $response")
                null
            }
        }
    }


    fun callPdl(service: String, aktorId: String): HttpResponse? {
        return runBlocking {
            val token = tokenConsumer.getToken(urlEnv.pdlScope)
            val bearerTokenString = "Bearer $token"
            val graphQuery = this::class.java.getResource("$QUERY_PATH_PREFIX/$service").readText().replace("[\n\r]", "")
            val requestBody = PdlRequest(graphQuery, Variables(aktorId))
            try {
                client.post<HttpResponse>(urlEnv.pdlUrl) {
                    headers {
                        append(TEMA_HEADER, OPPFOLGING_TEMA_HEADERVERDI)
                        append(HttpHeaders.ContentType, ContentType.Application.Json)
                        append(HttpHeaders.Authorization, bearerTokenString)
                    }
                    if (isNotGCP()) {
                        headers {
                            append(NAV_CONSUMER_TOKEN_HEADER, bearerTokenString)
                        }
                    }
                    body = requestBody
                }
            } catch (e: Exception) {
                log.error("Error while calling PDL ($service): ${e.message}", e)
                null
            }
        }
    }
}

class LocalPdlConsumer(urlEnv: UrlEnv, tokenConsumer: TokenConsumer) : PdlConsumer(urlEnv, tokenConsumer) {
    override fun getFnr(aktorId: String): String {
        return aktorId.substring(0, 11)
    }
}
