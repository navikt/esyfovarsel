package no.nav.syfo.consumer.pdfgen

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.UrlEnv
import no.nav.syfo.utils.formatDateForLetter
import no.nav.syfo.utils.httpClient
import org.slf4j.LoggerFactory
import java.time.LocalDate

class PdfgenConsumer(urlEnv: UrlEnv) {
    private val client = httpClient()
    private val syfooppdfgenUrl = urlEnv.syfooppdfgenUrl

    private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.PdfgenConsumer")

    fun getMerVeiledningPDF(navn: String?, maxDate: LocalDate?): ByteArray? {
        val merVeiledningPdfUrl = syfooppdfgenUrl + "/api/v1/genpdf/oppfolging/mer_veiledning"
        val request = getPdfgenRequest(navn, maxDate)
        return runBlocking {
            try {
                val response = client.post<HttpResponse>(merVeiledningPdfUrl) {
                    headers {
                        append(HttpHeaders.Accept, ContentType.Application.Json)
                        append(HttpHeaders.ContentType, ContentType.Application.Json)
                    }
                    body = request
                }

                when (response.status) {
                    HttpStatusCode.OK -> {
                        response.receive<ByteArray>()
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
        val sentDateFormatted = formatDateForLetter(LocalDate.now())
        val maxDateFormatted = maxDate?.let { formatDateForLetter(it) }

        return PdfgenRequest(Brevdata(navn = navn, sendtdato = sentDateFormatted, maxdato = maxDateFormatted))
    }
}
