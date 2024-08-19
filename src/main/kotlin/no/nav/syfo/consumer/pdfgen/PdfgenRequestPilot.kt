package no.nav.syfo.consumer.pdfgen

data class PdfgenRequestPilot(
    val brevdata: BrevdataPilot,
)

class BrevdataPilot(
    val sendtdato: String,
    val daysLeft: String?,
    val maxdato: String?,
)
