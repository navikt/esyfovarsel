package no.nav.syfo.consumer.distribuerjournalpost

data class JournalpostdistribusjonRequest(
    val journalpostId: String,
    val bestillendeFagsystem: String = "UKJENT",
    val dokumentProdApp: String = "esyfovarsel",
    val distribusjonstype: String = DistibusjonsType.ANNET.name,
    val distribusjonstidspunkt: String = "UMIDDELBART"
)
