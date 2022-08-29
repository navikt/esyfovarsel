package no.nav.syfo.consumer.dokarkiv.domain

data class DokarkivResponse(
    val dokumenter: List<DokumentInfo>? = null,
    val journalpostId: Int,
    val journalpostferdigstilt: Boolean? = null,
    val journalstatus: String,
    val melding: String? = null,
)
