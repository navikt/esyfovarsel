package no.nav.syfo.consumer.pdfgen

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.UrlEnv
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.fetchMaksDatoByFnr
import no.nav.syfo.utils.formatDateForLetter
import no.nav.syfo.utils.httpClientWithRetry
import org.slf4j.LoggerFactory
import java.time.LocalDate

class PdfgenConsumer(urlEnv: UrlEnv, val databaseInterface: DatabaseInterface) {
    private val client = httpClientWithRetry()
    private val syfooppdfgenUrl = urlEnv.syfooppdfgenUrl
    private val urlForReservedUsers = "$syfooppdfgenUrl/api/v1/genpdf/oppfolging/mer_veiledning_for_reserverte"
    private val urlForDigitalUsers = "$syfooppdfgenUrl/api/v1/genpdf/oppfolging/mer_veiledning_for_digitale"

    private val log = LoggerFactory.getLogger(PdfgenConsumer::class.qualifiedName)

    suspend fun getMerVeiledningPdfForReserverte(fnr: String): ByteArray? {
        return getPdf(fnr, urlForReservedUsers)
    }

    suspend fun getMerVeiledningPdfForDigitale(fnr: String): ByteArray? {
        return getPdf(fnr, urlForDigitalUsers)
    }

    private suspend fun getPdf(fnr: String, merVeiledningPdfUrl: String): ByteArray? {
        val sykepengerMaxDate = databaseInterface.fetchMaksDatoByFnr(fnr)
        val request = getPdfgenRequest(
            sykepengerMaxDate?.utbetalt_tom,
            sykepengerMaxDate?.forelopig_beregnet_slutt,
        )

        return try {
            val response = client.post(merVeiledningPdfUrl) {
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json)
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                }
                setBody(request)
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    response.body<ByteArray>()
                }

                else -> {
                    log.error("Could not get PDF byte array from syfooppdfgen: $response")
                    null
                }
            }
        } catch (e: Exception) {
            log.error("Exception while calling syfooppdfgen: ${e.message}", e)
            null
        }
    }

    private fun getPdfgenRequest(utbetaltTom: LocalDate?, maxDate: LocalDate?): PdfgenRequest {
        val sentDateFormatted = formatDateForLetter(LocalDate.now())
        val utbetaltTomFormatted = utbetaltTom?.let { formatDateForLetter(it) }
        val maxDateFormatted = maxDate?.let { formatDateForLetter(it) }

        return PdfgenRequest(
            Brevdata(
                sendtdato = sentDateFormatted,
                utbetaltTom = utbetaltTomFormatted,
                maxdato = maxDateFormatted,
            ),
        )
    }
}
