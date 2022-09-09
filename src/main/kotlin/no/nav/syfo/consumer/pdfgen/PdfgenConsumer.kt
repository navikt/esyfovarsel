package no.nav.syfo.consumer.pdfgen

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.UrlEnv
import no.nav.syfo.utils.httpClient
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PdfgenConsumer(urlEnv: UrlEnv) {
    private val client = httpClient()
    private val syfooppdfgenUrl = urlEnv.syfooppdfgenUrl
    private val BREV_DATE_FORMAT_PATTERN = "dd. MMM yy"

    private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.PdfgenConsumer")


    fun getMerVeiledningPDF(navn: String?, maxDate: LocalDate?): ByteArray? {
        val merVeiledningPdfUrl = syfooppdfgenUrl + "/api/v1/genpdf/oppfolging/mer_veiledning"
        val request = getPdfgenRequest(navn, maxDate)

        return runBlocking {
            try {
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
    }

    private fun getPdfgenRequest(navn: String?, maxDate: LocalDate?): PdfgenRequest {
        val sentDateFormatted = LocalDate.now().format(DateTimeFormatter.ofPattern(BREV_DATE_FORMAT_PATTERN))
        val maxDateFormatted = maxDate?.format(DateTimeFormatter.ofPattern(BREV_DATE_FORMAT_PATTERN))

        return PdfgenRequest(Brevdata(navn = navn, sendtdato = sentDateFormatted, maxdato = maxDateFormatted))
    }
}
