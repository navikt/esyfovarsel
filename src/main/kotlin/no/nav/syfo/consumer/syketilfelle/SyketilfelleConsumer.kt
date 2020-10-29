package no.nav.syfo.consumer.syketilfelle

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.readText
import io.ktor.http.HttpStatusCode
import java.time.LocalDate
import no.nav.syfo.consumer.token.TokenConsumer
import no.nav.syfo.logger
import no.nav.syfo.objectMapper
import kotlin.math.log

class SyketilfelleConsumer(
        val tokenConsumer: TokenConsumer,
        val httpClient: HttpClient = HttpClient()
) {
    val LOGGER = logger()

    suspend fun beregnOppfolgingstilfelle(aktoerId: String): Oppfolgingstilfelle {
        val token = tokenConsumer.token()

        val response = httpClient.get<HttpStatement> {
            url {
                host = "https://syfosyketilfelle.nais.preprod.local"
                path("oppfolgingstilfelle/beregn/syfomoteadmin/$aktoerId")
            }
            header("Authorization", "Bearer $token")
        }.execute()

        if (HttpStatusCode.OK == response.status) {
            LOGGER.info("Beregning av oppfolgingstilfelle for $aktoerId, status: ${response.status}")
            return objectMapper.readValue(response.readText())
        } else {
            LOGGER.error("Feil ved beregning av oppfolgingstilfelle for $aktoerId, status: ${response.status}")
            throw RuntimeException("Feil ved beregning av oppfolgingstilfelle: ${response.status}")
        }
    }
}

data class Oppfolgingstilfelle(
        val antallBrukteDager: Int,
        val oppbruktArbeidsgvierperiode: Boolean,
        val arbeidsgiverperiode: Periode?
)

data class Periode(val fom: LocalDate, val tom: LocalDate)

