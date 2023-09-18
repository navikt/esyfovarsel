package no.nav.syfo.service

import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_FORHANDSVARSEL_STANS
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselDataJournalpost
import no.nav.syfo.kafka.consumers.varselbus.domain.toVarselDataJournalpost
import org.apache.commons.cli.MissingArgumentException
import org.slf4j.LoggerFactory
import java.io.IOException

class AktivitetskravVarselService(
    val senderFacade: SenderFacade,
    val accessControlService: AccessControlService,
) {
    private val log = LoggerFactory.getLogger(AktivitetskravVarselService::class.qualifiedName)

    fun sendVarselTilArbeidstaker(varselHendelse: ArbeidstakerHendelse) {
        if (varselHendelse.type == SM_FORHANDSVARSEL_STANS) {
            val journalpostData = varselHendelse.data as VarselDataJournalpost
            journalpostData.id?.let {
                sendFysiskBrevTilArbeidstaker(journalpostData.uuid, varselHendelse, journalpostData.id)
            } ?: log.error("Forhåndsvarsel: JournalpostId is null")
        }
    }

    private fun sendFysiskBrevTilArbeidstaker(
        uuid: String,
        arbeidstakerHendelse: ArbeidstakerHendelse,
        journalpostId: String,
    ) {
        try {
            senderFacade.sendBrevTilFysiskPrint(uuid, arbeidstakerHendelse, journalpostId)
        } catch (e: RuntimeException) {
            log.info("Feil i sending av fysisk brev om dialogmote: ${e.message} for hendelsetype: ${arbeidstakerHendelse.type.name}")
        }
    }

    private fun dataToVarselDataJournalpost(data: Any?): VarselDataJournalpost {
        return data?.let {
            try {
                return data.toVarselDataJournalpost()
            } catch (e: IOException) {
                throw IOException("ArbeidstakerHendelse har feil format")
            }
        } ?: throw MissingArgumentException("EsyfovarselHendelse mangler 'data'-felt")
    }

}
