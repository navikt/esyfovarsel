package no.nav.syfo.service

import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_FORHANDSVARSEL_STANS
import no.nav.syfo.metrics.tellAktivitetskravFraIsAktivitetskrav
import no.nav.syfo.utils.dataToVarselData
import org.slf4j.LoggerFactory

class AktivitetskravVarselService(
    val senderFacade: SenderFacade,
    val accessControlService: AccessControlService,
    val isNewSendingEnabled: Boolean
) {
    private val log = LoggerFactory.getLogger(AktivitetskravVarselService::class.qualifiedName)

    fun sendVarselTilArbeidstaker(varselHendelse: ArbeidstakerHendelse) {
        if (!isNewSendingEnabled) {
            tellAktivitetskravFraIsAktivitetskrav()
            if (varselHendelse.type == SM_FORHANDSVARSEL_STANS) {
                val varselData = dataToVarselData(varselHendelse.data)

                requireNotNull(varselData.journalpost?.id)

                sendFysiskBrevTilArbeidstaker(
                    varselData.journalpost!!.uuid,
                    varselHendelse,
                    varselData.journalpost.id!!
                )
            }
        }
    }

    private fun sendFysiskBrevTilArbeidstaker(
        uuid: String,
        arbeidstakerHendelse: ArbeidstakerHendelse,
        journalpostId: String,
    ) {
        try {
            senderFacade.sendBrevTilFysiskPrint(uuid, arbeidstakerHendelse, journalpostId, DistibusjonsType.VIKTIG)
        } catch (e: RuntimeException) {
            log.info("Feil i sending av fysisk brev om dialogmote: ${e.message} for hendelsetype: ${arbeidstakerHendelse.type.name}")
        }
    }
}
