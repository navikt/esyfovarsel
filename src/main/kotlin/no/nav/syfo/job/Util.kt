package no.nav.syfo.job

import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import java.util.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.JobEnv
import no.nav.syfo.utils.httpClient
import org.slf4j.LoggerFactory
import org.slf4j.helpers.Util

fun closeExpiredMicrofrontendsJob(env: JobEnv) {
    val logg = LoggerFactory.getLogger(Util::class.java)
    runBlocking {
        logg.info("Starter closeExpiredMicrofrontendsJob")
        val credentials = "${env.serviceuserUsername}:${env.serviceuserPassword}"
        val encodededCredentials = Base64.getEncoder().encodeToString(credentials.toByteArray())
        val httpClient = httpClient()
        val response: HttpResponse = httpClient.post(env.jobTriggerUrl) {
            headers {
                append("Authorization", "Basic $encodededCredentials")
            }
        }
        val status = response.status
        if (status == HttpStatusCode.OK) {
            logg.info("Jobb closeExpiredMicrofrontendsJob startet")
        } else {
            logg.error("Feil i closeExpiredMicrofrontendsJob: Klarte ikke kalle trigger-API i esyfovarsel. Fikk svar med status: $status")
        }
        httpClient.close()
    }
}

fun sendForcedPhysicalAktivitetspliktLetterJob(env: JobEnv) {
    val logg = LoggerFactory.getLogger(Util::class.java)
        if (env.revarsleUnreadAktivitetskrav) {
            runBlocking {
                logg.info("Starter sendForcedPhysicalAktivitetspliktLetterJob")
                val credentials = "${env.serviceuserUsername}:${env.serviceuserPassword}"
                val encodededCredentials = Base64.getEncoder().encodeToString(credentials.toByteArray())
                val httpClient = httpClient()
                val response: HttpResponse = httpClient.post(env.jobTriggerUrl) {
                    headers {
                        append("Authorization", "Basic $encodededCredentials")
                    }
                }
                val status = response.status
                if (status == HttpStatusCode.OK) {
                    logg.info("Triggered sendForcedPhysicalAktivitetspliktLetterJob")
                } else {
                    logg.error("Error in sendForcedPhysicalAktivitetspliktLetterJob: got status: $status")
                }
                httpClient.close()
            }
        } else {
            logg.info("sendForcedPhysicalAktivitetspliktLetterJob toggle is false")
        }
}


