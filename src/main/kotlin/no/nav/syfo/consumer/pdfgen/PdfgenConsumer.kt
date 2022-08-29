package no.nav.syfo.consumer.pdfgen

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.UrlEnv
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter


class PdfgenConsumer(urlEnv: UrlEnv) {
    private val client: HttpClient
    private val syfooppdfgenUrl: String
    private val BREV_DATE_FORMAT_PATTERN = "dd. MMM yy"

    private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.PdfgenConsumer")

    init {
        client = HttpClient(CIO) {
            expectSuccess = false
            install(JsonFeature) {
                serializer = JacksonSerializer {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
        }
        syfooppdfgenUrl = urlEnv.syfooppdfgenUrl
        println("PDF URL $syfooppdfgenUrl")
    }

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
        val sendtDateFormatted = LocalDate.now().format(DateTimeFormatter.ofPattern(BREV_DATE_FORMAT_PATTERN))
        val maxDateFormatted = maxDate?.format(DateTimeFormatter.ofPattern(BREV_DATE_FORMAT_PATTERN))

        return PdfgenRequest(Brevdata(navn = navn, sendtdato = sendtDateFormatted, maxdato = maxDateFormatted))
    }
}
