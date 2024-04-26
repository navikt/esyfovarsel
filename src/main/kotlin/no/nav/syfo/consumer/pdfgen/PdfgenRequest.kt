package no.nav.syfo.consumer.pdfgen

data class PdfgenRequest(
    val brevdata: Brevdata,
)

class Brevdata(
    val navn: String?,
    val sendtdato: String,
    val utbetaltTom: String?,
    val maxdato: String?,
)
