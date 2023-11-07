package no.nav.syfo.service.microfrontend

import no.nav.syfo.db.*
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.kafka.consumers.varselbus.domain.*
import no.nav.syfo.kafka.producers.mineside_microfrontend.*
import no.nav.syfo.service.mikrofrontend.MikrofrontendAktivitetskravService

class MikrofrontendService(
    val minSideMicrofrontendKafkaProducer: MinSideMicrofrontendKafkaProducer,
    val mikrofrontendDialogmoteService: MikrofrontendDialogmoteService,
    val mikrofrontendAktivitetskravService: MikrofrontendAktivitetskravService,
    val database: DatabaseInterface
) {

    companion object {
        val actionEnabled = MinSideEvent.ENABLE.toString().lowercase()
        val actionDisabled = MinSideEvent.DISABLE.toString().lowercase()
    }

    fun updateMikrofrontendForUserByHendelse(hendelse: ArbeidstakerHendelse) {
        if (hendelse.isNotEligibleForMikrofrontendProcessing()) {
            return
        }
        val tjeneste = hendelse.type.toMikrofrontendTjenesteType()

        val recordToSend = when (tjeneste) {
            Tjeneste.DIALOGMOTE
                -> mikrofrontendDialogmoteService.updateDialogmoteFrontendForUserByHendelse(hendelse)
            Tjeneste.AKTIVITETSKRAV
                -> mikrofrontendAktivitetskravService.createOrUpdateAktivitetskravMicrofrontendByHendelse(hendelse)
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
        mikrofrontendsToClose.addAll(mikrofrontendAktivitetskravService.findExpiredAktivitetskravMikrofrontends())
        mikrofrontendsToClose.forEach {
            val (fnr, mikrofrontendId, tjeneste) = it
            disableMikrofrontendForUser(
                fnr,
                MinSideRecord(actionDisabled, fnr, mikrofrontendId),
                tjeneste
            )
        }
    }

    fun closeAllMikrofrontendForUser(fnr: PersonIdent) {
        disableMikrofrontendForUser(
            fnr.value,
            mikrofrontendDialogmoteService.minSideRecordDisabled(fnr.value),
            Tjeneste.DIALOGMOTE
        )
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
            HendelseType.SM_DIALOGMOTE_AVLYST
                -> Tjeneste.DIALOGMOTE
            HendelseType.SM_AKTIVITETSPLIKT_STATUS_FORHANDSVARSEL,
            HendelseType.SM_AKTIVITETSPLIKT_STATUS_NY,
            HendelseType.SM_AKTIVITETSPLIKT_STATUS_UNNTAK,
            HendelseType.SM_AKTIVITETSPLIKT_STATUS_OPPFYLT,
            HendelseType.SM_AKTIVITETSPLIKT_STATUS_AUTOMATISK_OPPFYLT,
            HendelseType.SM_AKTIVITETSPLIKT_STATUS_IKKE_OPPFYLT,
            HendelseType.SM_AKTIVITETSPLIKT_STATUS_IKKE_AKTUELL,
                -> Tjeneste.AKTIVITETSKRAV
            else -> throw IllegalArgumentException("$this is not a valid type for updating MF state")
        }
}
