package no.nav.syfo.consumer

import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.TokenConsumer
import no.nav.syfo.consumer.pdl.*
import no.nav.syfo.isNotGCP
import no.nav.syfo.utils.post
import org.slf4j.LoggerFactory

open class PdlConsumer(private val urlEnv: UrlEnv, private val tokenConsumer: TokenConsumer) {
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

    fun callPdl(service: String, aktorId: String): HttpResponse? {
        return runBlocking {
            val token = tokenConsumer.getToken(urlEnv.pdlScope)
            val graphQuery = this::class.java.getResource("$QUERY_PATH_PREFIX/$service").readText().replace("[\n\r]", "")
            val requestBody = PdlRequest(graphQuery, Variables(aktorId))
            try {
                post(urlEnv.pdlUrl, requestBody, urlEnv.pdlScope, getRequestHeaders(token))
            } catch (e: Exception) {
                log.error("Error while calling PDL ($service): ${e.message}", e)
                null
            }
        }
    }

    private fun getRequestHeaders(token: String): HashMap<String, String> {
        val requestHeaders = hashMapOf<String, String>()
        requestHeaders[TEMA_HEADER] = OPPFOLGING_TEMA_HEADERVERDI
        requestHeaders[HttpHeaders.ContentType] = ContentType.Application.Json.toString()

        if (isNotGCP()) {
            requestHeaders[NAV_CONSUMER_TOKEN_HEADER] = "Bearer $token"
        }
        return requestHeaders
    }
}

class LocalPdlConsumer(urlEnv: UrlEnv, tokenConsumer: TokenConsumer) : PdlConsumer(urlEnv, tokenConsumer) {
    override fun getFnr(aktorId: String): String {
        return aktorId.substring(0, 11)
    }
}
