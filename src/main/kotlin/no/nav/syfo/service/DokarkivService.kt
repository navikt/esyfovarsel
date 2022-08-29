package no.nav.syfo.service

import no.nav.syfo.consumer.PdlConsumer
import no.nav.syfo.consumer.dokarkiv.DokarkivConsumer
import no.nav.syfo.consumer.dokarkiv.domain.AvsenderMottaker
import no.nav.syfo.consumer.dokarkiv.domain.DokarkivRequest
import no.nav.syfo.consumer.dokarkiv.domain.Dokument
import no.nav.syfo.consumer.dokarkiv.domain.Dokumentvariant
import no.nav.syfo.consumer.pdfgen.PdfgenConsumer
import no.nav.syfo.consumer.pdl.getFullNameAsString

class DokarkivService(val dokarkivConsumer: DokarkivConsumer, val pdfgenConsumer: PdfgenConsumer, val pdlConsumer: PdlConsumer) {
    suspend fun getJournalpostId(fnr: String, uuid: String): String? {
        val mottakerNavn = pdlConsumer.hentPerson(fnr)?.getFullNameAsString()
        val pdf = pdfgenConsumer.getMerVeiledningPDF(mottakerNavn, null)

        val dokarkivRequest = pdf?.let { createDokarkivRequest(fnr, pdf, uuid) }

        return dokarkivRequest?.let { dokarkivConsumer.postDocumentToDokarkiv(dokarkivRequest)?.journalpostId.toString() }
    }

    private fun createDokarkivRequest(fnr: String, pdf: ByteArray, uuid: String): DokarkivRequest {
        return DokarkivRequest.create(
            AvsenderMottaker.create(fnr),
            createDokumenterList(pdf, uuid),
        )
    }

    private fun createDokumenterList(pdf: ByteArray, uuid: String): List<Dokument> {
        return listOf(Dokument.create(listOf(Dokumentvariant.create(pdf, uuid))))
    }
}
