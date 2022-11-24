package no.nav.syfo.consumer.pdl

import io.ktor.client.call.receive
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.append
import java.io.FileNotFoundException
import java.time.LocalDate
import java.time.Period
import kotlinx.coroutines.runBlocking
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.utils.httpClient
import no.nav.syfo.utils.parsePDLDate
import org.slf4j.LoggerFactory

open class PdlConsumer(private val urlEnv: UrlEnv, private val azureAdTokenConsumer: AzureAdTokenConsumer) {
    private val client = httpClient()
    private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.pdl.PdlConsumer")

    fun isBrukerGradertForInformasjon(ident: String): Boolean? {
        val response = callPdl(PERSON_QUERY, ident)

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

    fun isBrukerYngreEnn67(ident: String): Boolean {
        val response = callPdl(PERSON_QUERY, ident)

        return when (response?.status) {
            HttpStatusCode.OK -> {
                val fodselsdato = runBlocking { response.receive<PdlPersonResponse>().data?.getFodselsdato() }
                return isFodselsdatoMindreEnn67Ar(fodselsdato)
            }

            HttpStatusCode.NoContent -> {
                log.error("Could not get adressesperre from PDL: No content found in the response body")
                true
            }

            HttpStatusCode.Unauthorized -> {
                log.error("Could not get adressesperre from PDL: Unable to authorize")
                true
            }

            else -> {
                log.error("Could not get adressesperre from PDL: $response")
                true
            }
        }
    }

    fun isFodselsdatoMindreEnn67Ar(fodselsdato: String?): Boolean {
        val parsedFodselsdato = fodselsdato?.let { parsePDLDate(it) }
        val isFodselsdatoMindreEnn67Ar = parsedFodselsdato == null || (Period.between(parsedFodselsdato, LocalDate.now()).years < 67)
        if (!isFodselsdatoMindreEnn67Ar) log.info("[PdlConsumer] Person is over 67 years")
        return isFodselsdatoMindreEnn67Ar
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

    private fun callPdl(service: String, ident: String): HttpResponse? {
        return runBlocking {
            val token = azureAdTokenConsumer.getToken(urlEnv.pdlScope)
            val bearerTokenString = "Bearer $token"
            val graphQueryResourcePath = "$QUERY_PATH_PREFIX/$service"
            val graphQuery =
                this::class.java.getResource(graphQueryResourcePath)?.readText()?.replace("[\n\r]", "")
                    ?: throw FileNotFoundException("Could not found resource: $graphQueryResourcePath")
            val requestBody = PdlRequest(graphQuery, Variables(ident))
            try {
                client.post<HttpResponse>(urlEnv.pdlUrl) {
                    headers {
                        append(TEMA_HEADER, OPPFOLGING_TEMA_HEADERVERDI)
                        append(HttpHeaders.ContentType, ContentType.Application.Json)
                        append(HttpHeaders.Authorization, bearerTokenString)
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
