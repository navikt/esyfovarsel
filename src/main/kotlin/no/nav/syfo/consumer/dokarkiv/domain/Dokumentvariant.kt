package no.nav.syfo.consumer.dokarkiv.domain

data class Dokumentvariant private constructor(
    val filtype: String,
    val variantformat: String,
    val fysiskDokument: ByteArray,
    val filnavn: String,
) {
    companion object {
        fun create(
            fysiskDokument: ByteArray,
            uuid: String,
        ): Dokumentvariant {
            return Dokumentvariant(
                filtype = "PDFA",
                variantformat = "ARKIV",
                fysiskDokument = fysiskDokument,
                filnavn = "Brev om snart slutt på sykepenger-$uuid",
            )
        }
    }
}
