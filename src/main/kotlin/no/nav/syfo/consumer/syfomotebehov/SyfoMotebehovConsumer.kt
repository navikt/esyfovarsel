package no.nav.syfo.consumer.syfomotebehov

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.TokenConsumer
import no.nav.syfo.utils.httpClient

open class SyfoMotebehovConsumer(urlEnv: UrlEnv, private val tokenConsumer: TokenConsumer) {
    private val client = httpClient()
    private val basepath = urlEnv.syfomotebehovUrl

    fun sendVarselTilNaermesteLeder(aktorId: String, orgnummer: String, narmesteLederFnr: String, arbeidstakerFnr: String) {
        runBlocking {
            val requestURL = "$basepath/syfomotebehov/api/varsel/naermesteleder/esyfovarsel"
            val stsAccessToken = tokenConsumer.getToken(null)
            val bearerTokenString = "Bearer $stsAccessToken"
            val requestBody = MotebehovsvarVarselInfo(aktorId, orgnummer, narmesteLederFnr, arbeidstakerFnr)

            val response = client.post<HttpResponse>(requestURL) {
                headers {
                    append(HttpHeaders.Authorization, bearerTokenString)
                    append(HttpHeaders.Accept, ContentType.Application.Json)
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                }
                body = requestBody
            }
            if (response.status != HttpStatusCode.OK) {
                throw RuntimeException("Klarte ikke å opprette varsel til naermeste leder om møtebehov - ${response.status} $requestURL")
            }
        }
    }
}

data class MotebehovsvarVarselInfo(
    val sykmeldtAktorId: String,
    val orgnummer: String,
    val naermesteLederFnr: String,
    val arbeidstakerFnr: String,
)