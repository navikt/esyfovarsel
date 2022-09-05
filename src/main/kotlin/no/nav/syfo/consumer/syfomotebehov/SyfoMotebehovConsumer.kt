package no.nav.syfo.consumer.syfomotebehov

import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.TokenConsumer
import no.nav.syfo.utils.post
import java.io.Serializable

open class SyfoMotebehovConsumer(urlEnv: UrlEnv, private val tokenConsumer: TokenConsumer) {
    private val basepath = urlEnv.syfomotebehovUrl
    private val varselPath = "/syfomotebehov/api/varsel"
    fun sendVarselTilNaermesteLeder(aktorId: String, orgnummer: String, narmesteLederFnr: String, arbeidstakerFnr: String) {
        runBlocking {
            val requestURL = "$basepath$varselPath/naermesteleder/esyfovarsel"
            val stsAccessToken = tokenConsumer.getToken(null)
            val requestBody = MotebehovsvarVarselInfo(aktorId, orgnummer, narmesteLederFnr, arbeidstakerFnr)

            val response = post(
                requestURL, requestBody, stsAccessToken,
                hashMapOf(
                    HttpHeaders.Accept to ContentType.Application.Json.toString(),
                    HttpHeaders.ContentType to ContentType.Application.Json.toString(),
                )
            )

            if (response.status != HttpStatusCode.OK) {
                throw RuntimeException("Klarte ikke å opprette varsel til naermeste leder om møtebehov - ${response.status} $requestURL")
            }
        }
    }

    fun sendVarselTilArbeidstaker(aktorId: String, arbeidstakerFnr: String) {
        runBlocking {
            val requestURL = "$basepath$varselPath/arbeidstaker/esyfovarsel"
            val stsAccessToken = tokenConsumer.getToken(null)
            val requestBody = MotebehovsvarSykmeldtVarselInfo(aktorId, arbeidstakerFnr)

            val response = post(
                requestURL, requestBody, stsAccessToken,
                hashMapOf(
                    HttpHeaders.Accept to ContentType.Application.Json.toString(),
                    HttpHeaders.ContentType to ContentType.Application.Json.toString(),
                )
            )

            if (response.status != HttpStatusCode.OK) {
                throw RuntimeException("Klarte ikke å opprette varsel til sykmeldt om møtebehov - ${response.status} $requestURL")
            }
        }
    }
}

data class MotebehovsvarVarselInfo(
    val sykmeldtAktorId: String,
    val orgnummer: String,
    val naermesteLederFnr: String,
    val arbeidstakerFnr: String,
) : Serializable

data class MotebehovsvarSykmeldtVarselInfo(
    val sykmeldtAktorId: String,
    val arbeidstakerFnr: String,
) : Serializable
