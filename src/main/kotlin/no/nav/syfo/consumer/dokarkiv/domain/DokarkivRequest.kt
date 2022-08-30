package no.nav.syfo.consumer.dokarkiv.domain

const val JOURNALFORENDE_ENHET = 9999 // automatisk journalføring uten mennesker involvert

data class DokarkivRequest(
    val avsenderMottaker: AvsenderMottaker,
    val tittel: String,
    val bruker: Bruker? = null,
    val dokumenter: List<Dokument>,
    val journalfoerendeEnhet: Int?,
    val journalpostType: String,
    val tema: String,
    val kanal: String,
    val sak: Sak,
) {
    companion object {
        fun create(
            avsenderMottaker: AvsenderMottaker,
            dokumenter: List<Dokument>,
        ) = DokarkivRequest(
            avsenderMottaker = avsenderMottaker,
            tittel = "Brev om snart slutt på sykepenger",
            bruker = Bruker(id = avsenderMottaker.id, idType = avsenderMottaker.idType),
            dokumenter = dokumenter,
            journalfoerendeEnhet = JOURNALFORENDE_ENHET,
            journalpostType = "UTGAAENDE",
            tema = "OPP", // Oppfolging
            kanal = "S",
            sak = Sak("GENERELL_SAK"),
        )
    }
}

data class AvsenderMottaker private constructor(
    val id: String,
    val idType: String,
) {
    companion object {
        fun create(
            id: String,
        ) = AvsenderMottaker(
            id = id,
            idType = "FNR",
        )
    }
}

data class Bruker(
    val id: String,
    val idType: String,
) {
    companion object {
        fun create(
            id: String,
            idType: String = "FNR",
        ) = Bruker(
            id = id,
            idType = idType,
        )
    }
}

data class Dokument private constructor(
    val brevkode: String,
    val dokumentvarianter: List<Dokumentvariant>,
    val tittel: String,
) {
    companion object {
        fun create(
            dokumentvarianter: List<Dokumentvariant>,
        ) = Dokument(
            tittel = "Brev om snart slutt på sykepenger",
            brevkode = "SNART_SLUTT_PA_SYKEPENGER",
            dokumentvarianter = dokumentvarianter,
        )
    }
}

data class Sak(val sakstype: String = "GENERELL_SAK")
