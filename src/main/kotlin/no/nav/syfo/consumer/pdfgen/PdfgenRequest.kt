package no.nav.syfo.consumer.pdfgen

data class PdfgenRequest(
    val brevdata: Brevdata,
)

class Brevdata(
    val sendtdato: String,
    val utbetaltTom: String?,
    val maxdato: String?,
)
