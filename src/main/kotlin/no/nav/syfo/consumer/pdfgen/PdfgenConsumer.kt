package no.nav.syfo.consumer.pdfgen

import io.ktor.client.call.receive
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.append
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import no.nav.syfo.UrlEnv
import no.nav.syfo.consumer.pdl.PdlConsumer
import no.nav.syfo.consumer.pdl.getFullNameAsString
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.fetchForelopigBeregnetSluttPaSykepengerByFnr
import no.nav.syfo.utils.formatDateForLetter
import no.nav.syfo.utils.httpClient
import org.slf4j.LoggerFactory

class PdfgenConsumer(urlEnv: UrlEnv, val pdlConsumer: PdlConsumer, val databaseInterface: DatabaseInterface) {
    private val client = httpClient()
    private val syfooppdfgenUrl = urlEnv.syfooppdfgenUrl

    private val log = LoggerFactory.getLogger(PdfgenConsumer::class.qualifiedName)

    fun getMerVeiledningPDF(fnr: String): ByteArray? {
        val mottakerNavn = pdlConsumer.hentPerson(fnr)?.getFullNameAsString()
        val sykepengerMaxDate = databaseInterface.fetchForelopigBeregnetSluttPaSykepengerByFnr(fnr)
        val merVeiledningPdfUrl = syfooppdfgenUrl + "/api/v1/genpdf/oppfolging/mer_veiledning"
        val request = getPdfgenRequest(mottakerNavn, sykepengerMaxDate)

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
