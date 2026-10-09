package no.nav.syfo.consumer.narmesteLeder

import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.append
import kotlinx.coroutines.CancellationException
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.ITokenConsumer
import no.nav.syfo.utils.httpClient
import org.slf4j.LoggerFactory

class NarmesteLederConsumer(
    urlEnv: UrlEnv,
    private val azureAdTokenConsumer: ITokenConsumer,
) : INarmesteLederConsumer {
    private val client = httpClient()
    private val basepath = urlEnv.narmestelederUrl
    private val log = LoggerFactory.getLogger(NarmesteLederConsumer::class.qualifiedName)
    private val scope = urlEnv.narmestelederScope

    override suspend fun getNarmesteLeder(
        ansattFnr: String,
        orgnummer: String,
    ): NarmesteLederRelasjon? {
        val requestURL = "$basepath/internal/api/v1/lookup"
        try {
            val token = azureAdTokenConsumer.getToken(scope)
            val response =
                client.post(requestURL) {
                    headers {
                        append(HttpHeaders.Accept, ContentType.Application.Json)
                        append(HttpHeaders.ContentType, ContentType.Application.Json)
                        append(HttpHeaders.Authorization, "Bearer $token")
                    }
                    setBody(LineManagerLookupRequest(ansattFnr, orgnummer))
                }

            return when (response.status) {
                HttpStatusCode.OK -> {
                    response.body<LineManagerLookupResponse>().lineManager?.let { leader ->
                        NarmesteLederRelasjon(
                            narmesteLederId = leader.id,
                            narmesteLederFnr = leader.nationalIdentificationNumber,
                            // An empty list means no valid address; null makes callers stop the varsel.
                            narmesteLederEpost = leader.emailAddresses.takeIf { it.isNotEmpty() }?.joinToString(";"),
                        )
                    }
                }

                else -> {
                    log.atError().addKeyValue("status", response.status.value).log("Could not get nærmeste leder")
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.atError().addKeyValue("exception", e.javaClass.name).log("Could not look up nærmeste leder")
            return null
        } catch (e: Error) {
            log.atError().addKeyValue("exception", e.javaClass.name).log("Error during lookup of nærmeste leder")
            throw e
        }
    }
}

internal data class LineManagerLookupRequest(
    val employeeNationalIdentificationNumber: String,
    val organizationNumber: String,
)

internal data class LineManagerLookupResponse(
    val lineManager: LineManager?,
)

internal data class LineManager(
    val id: String,
    val nationalIdentificationNumber: String,
    val emailAddresses: List<String>,
)
