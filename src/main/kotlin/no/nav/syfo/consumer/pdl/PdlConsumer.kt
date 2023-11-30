package no.nav.syfo.consumer.pdl

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.utils.httpClient
import no.nav.syfo.utils.isAlderMindreEnnGittAr
import org.slf4j.LoggerFactory
import java.io.FileNotFoundException

open class PdlConsumer(private val urlEnv: UrlEnv, private val azureAdTokenConsumer: AzureAdTokenConsumer) {
    private val client = httpClient()
    private val log = LoggerFactory.getLogger(PdlConsumer::class.qualifiedName)

    open suspend fun getFnr(aktorId: String): String? {
        val response = callPdl(IDENTER_QUERY, aktorId)

        return when (response?.status) {
            HttpStatusCode.OK -> {
                val pdlResponse = response.body<PdlIdentResponse>().data?.hentIdenter?.identer?.first()?.ident
                pdlResponse
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

    suspend fun isBrukerYngreEnnGittMaxAlder(ident: String, maxAlder: Int): Boolean {
        val response = callPdl(PERSON_QUERY, ident)

        return when (response?.status) {
            HttpStatusCode.OK -> {
                val fodselsdato = response.body<PdlPersonResponse>().data?.getFodselsdato()
                if (fodselsdato == null) {
                    log.warn("Returnert fødselsdato for en person fra PDL er null. Fortsetter som om bruker er yngre enn $maxAlder år da fødselsdato er ukjent.")
                    return true
                } else {
                    return isAlderMindreEnnGittAr(fodselsdato, maxAlder)
                }
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

    open suspend fun hentPerson(fnr: String): PdlHentPerson? {
        val response = callPdl(PERSON_QUERY, fnr)

        return when (response?.status) {
            HttpStatusCode.OK -> {
                val pdlResponse = response.body<PdlPersonResponse>().data
                pdlResponse
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

    private suspend fun callPdl(service: String, ident: String): HttpResponse? {
        val token = azureAdTokenConsumer.getToken(urlEnv.pdlScope)
        val bearerTokenString = "Bearer $token"
        val graphQueryResourcePath = "$QUERY_PATH_PREFIX/$service"
        val graphQuery =
            this::class.java.getResource(graphQueryResourcePath)?.readText()?.replace("[\n\r]", "")
                ?: throw FileNotFoundException("Could not found resource: $graphQueryResourcePath")
        val requestBody = PdlRequest(graphQuery, Variables(ident))
        return try {
            client.post(urlEnv.pdlUrl) {
                headers {
                    append(PDL_BEHANDLINGSNUMMER_HEADER, BEHANDLINGSNUMMER_VURDERE_RETT_TIL_SYKEPENGER)
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                    append(HttpHeaders.Authorization, bearerTokenString)
                }
                setBody(requestBody)
            }
        } catch (e: Exception) {
            log.error("Error while calling PDL ($service): ${e.message}", e)
            null
        }
    }

    class LocalPdlConsumer(urlEnv: UrlEnv, azureAdTokenConsumer: AzureAdTokenConsumer) :
        PdlConsumer(urlEnv, azureAdTokenConsumer) {
        override suspend fun getFnr(aktorId: String): String {
            return aktorId.substring(0, 11)
        }
    }
}
