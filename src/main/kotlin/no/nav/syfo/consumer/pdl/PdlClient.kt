package no.nav.syfo.consumer.pdl

import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.metrics.COUNT_CALL_PDL_FAIL
import no.nav.syfo.metrics.COUNT_CALL_PDL_SUCCESS
import no.nav.syfo.utils.httpClientWithRetry
import org.slf4j.LoggerFactory
import java.io.FileNotFoundException

open class PdlClient(private val urlEnv: UrlEnv, private val azureAdTokenConsumer: AzureAdTokenConsumer) {
    private val httpClient = httpClientWithRetry(expectSuccess = true)
    private val log = LoggerFactory.getLogger(PdlClient::class.qualifiedName)

    suspend fun hentPerson(
        personIdent: String,
    ): HentPersonData? {
        val token = azureAdTokenConsumer.getToken(urlEnv.pdlScope)
        val query = getPdlQuery("/pdl/hentPerson.graphql")
        val request = PdlRequest(query, Variables(personIdent))

        val response: HttpResponse = httpClient.post(urlEnv.pdlUrl) {
            setBody(request)
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.Authorization, "Bearer $token")
            header(PDL_BEHANDLINGSNUMMER_HEADER, BEHANDLINGSNUMMER_VURDERE_RETT_TIL_SYKEPENGER)
        }

        when (response.status) {
            HttpStatusCode.OK -> {
                val pdlPersonReponse = response.body<HentPersonResponse>()
                return if (!pdlPersonReponse.errors.isNullOrEmpty()) {
                    COUNT_CALL_PDL_FAIL.increment()
                    pdlPersonReponse.errors.forEach {
                        log.error("Error while requesting person from PersonDataLosningen: ${it.errorMessage()}")
                    }
                    null
                } else {
                    COUNT_CALL_PDL_SUCCESS.increment()
                    pdlPersonReponse.data
                }
            }

            else -> {
                COUNT_CALL_PDL_FAIL.increment()
                log.error("Request with url: $urlEnv.pdlUrl failed with reponse code ${response.status.value}")
                return null
            }
        }
    }

    suspend fun isPersonAlive(
        personIdent: String,
    ): Boolean {
        val personStatus = hentPerson(personIdent)?.isPersonDod() ?: false
        return !personStatus
    }

    private fun getPdlQuery(graphQueryResourcePath: String): String {
        return this::class.java.getResource(graphQueryResourcePath)?.readText()?.replace("[\n\r]", "")
            ?: throw FileNotFoundException("Could not found resource: $graphQueryResourcePath")
    }
}
