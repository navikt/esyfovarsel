package no.nav.syfo.service.microfrontend

import no.nav.syfo.db.*
import no.nav.syfo.kafka.consumers.varselbus.domain.*
import no.nav.syfo.kafka.producers.mineside_microfrontend.*
import org.slf4j.LoggerFactory

class MikrofrontendService(
    val minSideMicrofrontendKafkaProducer: MinSideMicrofrontendKafkaProducer,
    val mikrofrontendDialogmoteService: MikrofrontendDialogmoteService,
    val database: DatabaseInterface
) {
    private val log = LoggerFactory.getLogger(MikrofrontendService::class.java)

    companion object {
        val actionEnabled = MinSideEvent.enable.toString()
        val actionDisabled = MinSideEvent.disable.toString()
    }

    fun updateMikrofrontendForUserByHendelse(hendelse: ArbeidstakerHendelse) {
        if (isNotEligableForMFProcessing(hendelse.type)) {
            return
        }
        val tjeneste = hendelse.type.toMikrofrontendTjenesteType()

        val recordToSend = when (tjeneste) {
            Tjeneste.DIALOGMOTE -> mikrofrontendDialogmoteService.updateDialogmoteFrontendForUserByHendelse(
                hendelse
            )
        }

        recordToSend?.let { record ->
            when (record.eventType) {
                actionEnabled -> enableMikrofrontendForUser(hendelse, record, tjeneste)
                actionDisabled -> disableMikrofrontendForUser(hendelse.arbeidstakerFnr, record, tjeneste)
            }
        }
    }

    fun findAndCloseExpiredMikrofrontends() {
        val mikrofrontendsToClose = mutableListOf<Triple<String, String, Tjeneste>>()
        mikrofrontendsToClose.addAll(mikrofrontendDialogmoteService.findExpiredDialogmoteMikrofrontends())
        mikrofrontendsToClose.forEach {
            val (fnr, mikrofrontendId, tjeneste) = it
            disableMikrofrontendForUser(
                fnr,
                MinSideRecord(actionDisabled, fnr, mikrofrontendId),
                tjeneste
            )
        }
    }

    private fun isNotEligableForMFProcessing(type: HendelseType) =
        when (type) {
            HendelseType.SM_DIALOGMOTE_SVAR_MOTEBEHOV,
            HendelseType.SM_DIALOGMOTE_INNKALT,
            HendelseType.SM_DIALOGMOTE_AVLYST,
            HendelseType.SM_DIALOGMOTE_REFERAT,
            HendelseType.SM_DIALOGMOTE_NYTT_TID_STED,
            HendelseType.SM_DIALOGMOTE_LEST -> false
            else -> true
        }

    private fun enableMikrofrontendForUser(
        hendelse: ArbeidstakerHendelse,
        minSideRecord: MinSideRecord,
        tjeneste: Tjeneste
    ) {
        storeMikrofrontendSynlighetEntryInDb(hendelse, tjeneste)
        minSideMicrofrontendKafkaProducer.sendRecordToMinSideTopic(minSideRecord)
    }

    private fun disableMikrofrontendForUser(
        fnr: String,
        minSideRecord: MinSideRecord,
        tjeneste: Tjeneste
    ) {
        minSideMicrofrontendKafkaProducer.sendRecordToMinSideTopic(minSideRecord)
        database.deleteMikrofrontendSynlighetEntryByFnrAndTjeneste(fnr, tjeneste)
    }

    private fun storeMikrofrontendSynlighetEntryInDb(hendelse: ArbeidstakerHendelse, tjeneste: Tjeneste) {
        database.storeMikrofrontendSynlighetEntry(
            MikrofrontendSynlighet(
                synligFor = hendelse.arbeidstakerFnr,
                tjeneste = tjeneste,
                synligTom = hendelse.getSynligTom()?.toLocalDate()
            )
        )
    }

    private fun HendelseType.toMikrofrontendTjenesteType(): Tjeneste =
        when (this) {
            HendelseType.SM_DIALOGMOTE_INNKALT,
            HendelseType.SM_DIALOGMOTE_NYTT_TID_STED,
            HendelseType.SM_DIALOGMOTE_LEST,
            HendelseType.SM_DIALOGMOTE_SVAR_MOTEBEHOV,
            HendelseType.SM_DIALOGMOTE_REFERAT,
            HendelseType.SM_DIALOGMOTE_AVLYST -> Tjeneste.DIALOGMOTE
            else -> throw IllegalArgumentException("$this is not a valid type for updating MF state")
        }
}
