package no.nav.syfo.service

import no.nav.syfo.consumer.dokarkiv.DokarkivConsumer
import no.nav.syfo.consumer.dokarkiv.domain.AvsenderMottaker
import no.nav.syfo.consumer.dokarkiv.domain.DokarkivRequest
import no.nav.syfo.consumer.dokarkiv.domain.Dokument
import no.nav.syfo.consumer.dokarkiv.domain.Dokumentvariant

class DokarkivService(val dokarkivConsumer: DokarkivConsumer) {
    suspend fun getJournalpostId(fnr: String, uuid: String, pdf: ByteArray): String {
        val dokarkivRequest = createDokarkivRequest(fnr, pdf, uuid)
        return dokarkivConsumer.postDocumentToDokarkiv(dokarkivRequest)?.journalpostId.toString()
    }

    private fun createDokarkivRequest(fnr: String, pdf: ByteArray, uuid: String): DokarkivRequest {
        return DokarkivRequest.create(
            AvsenderMottaker.create(fnr),
            createDokumenterList(pdf, uuid),
            uuid,
        )
    }

    private fun createDokumenterList(pdf: ByteArray, uuid: String): List<Dokument> {
        return listOf(Dokument.create(listOf(Dokumentvariant.create(pdf, uuid))))
    }
}
