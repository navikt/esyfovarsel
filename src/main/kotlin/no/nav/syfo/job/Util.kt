package no.nav.syfo.job

import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking
import no.nav.syfo.JobEnv
import no.nav.syfo.utils.httpClient
import org.slf4j.LoggerFactory
import org.slf4j.helpers.Util
import java.util.Base64

fun closeExpiredMicrofrontendsJob(env: JobEnv) {
    val logg = LoggerFactory.getLogger(Util::class.java)
    runBlocking {
        logg.info("Starter closeExpiredMicrofrontendsJob")
        val credentials = "${env.serviceuserUsername}:${env.serviceuserPassword}"
        val encodededCredentials = Base64.getEncoder().encodeToString(credentials.toByteArray())
        val httpClient = httpClient()
        val response: HttpResponse =
            httpClient.post(env.jobTriggerUrl) {
                headers {
                    append("Authorization", "Basic $encodededCredentials")
                }
            }
        if (response.status.isSuccess()) {
            logg.info("jobb startet")
        } else {
            logg.error("Feil: fikk status ${response.status}")
        }
        httpClient.close()
    }
}

fun sendSentralPrintAktivitetspliktLetterJob(env: JobEnv) {
    val logg = LoggerFactory.getLogger(Util::class.java)
    if (env.revarsleUnreadAktivitetskrav) {
        runBlocking {
            logg.info("Starter sendSentralPrintAktivitetspliktLetterJob")
            val credentials = "${env.serviceuserUsername}:${env.serviceuserPassword}"
            val encodededCredentials = Base64.getEncoder().encodeToString(credentials.toByteArray())
            val httpClient = httpClient()
            val response: HttpResponse =
                httpClient.post(env.jobTriggerUrl) {
                    headers {
                        append("Authorization", "Basic $encodededCredentials")
                    }
                }
            if (response.status.isSuccess()) {
                logg.info("Triggered sendSentralPrintAktivitetspliktLetterJob")
            } else {
                logg.error("Error in sendSentralPrintAktivitetspliktLetterJob: got status: ${response.status}")
            }
            httpClient.close()
        }
    } else {
        logg.info("sendSentralPrintAktivitetspliktLetterJob toggle is false")
    }
}
