package no.nav.syfo.consumer.distribuerjournalpost

enum class Kanal {
    PRINT,
    TRYGDERETTEN,
}

data class JournalpostdistribusjonRequest(
    val journalpostId: String,
    val bestillendeFagsystem: String = "UKJENT",
    val dokumentProdApp: String = "esyfovarsel",
    val distribusjonstype: String,
    val distribusjonstidspunkt: String = "UMIDDELBART",
    val tvingKanal: Kanal? = null,
)
