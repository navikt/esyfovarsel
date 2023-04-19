package no.nav.syfo.job

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.JobEnv
import no.nav.syfo.utils.httpClient
import org.slf4j.LoggerFactory
import java.util.*

fun sendNotificationsJob(env: JobEnv) {
    val logg = LoggerFactory.getLogger("no.nav.syfo.job.Util")
    if (env.sendVarsler) {
        runBlocking {
            logg.info("Starter jobb")
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
                logg.info("Har trigget varselutsending")
            } else {
                logg.error("Feil i esyfovarsel-job: Klarte ikke kalle trigger-API i esyfovarsel. Fikk svar med status: $status")
            }
            httpClient.close()
        }
    } else {
        logg.info("Jobb togglet av")
    }
}
