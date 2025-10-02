package no.nav.syfo.arbeidstakervarsel.service

import no.nav.syfo.arbeidstakervarsel.domain.Microfrontend
import no.nav.syfo.arbeidstakervarsel.domain.MicrofrontendType
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.deleteMikrofrontendSynlighetEntryByFnrAndTjeneste
import no.nav.syfo.db.storeMikrofrontendSynlighetEntry
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.getSynligTom
import no.nav.syfo.kafka.producers.mineside_microfrontend.MikrofrontendSynlighet
import no.nav.syfo.kafka.producers.mineside_microfrontend.MinSideEvent
import no.nav.syfo.kafka.producers.mineside_microfrontend.MinSideMicrofrontendKafkaProducer
import no.nav.syfo.kafka.producers.mineside_microfrontend.MinSideRecord
import no.nav.syfo.kafka.producers.mineside_microfrontend.Tjeneste
import no.nav.syfo.service.microfrontend.MikrofrontendAktivitetskravService
import no.nav.syfo.service.microfrontend.MikrofrontendDialogmoteService
import no.nav.syfo.service.microfrontend.MikrofrontendMerOppfolgingService

class MikrofrontendService(
    val minSideMicrofrontendKafkaProducer: MinSideMicrofrontendKafkaProducer,
    val mikrofrontendDialogmoteService: MikrofrontendDialogmoteService,
    val mikrofrontendAktivitetskravService: MikrofrontendAktivitetskravService,
    val mikrofrontendMerOppfolgingService: MikrofrontendMerOppfolgingService,
    val database: DatabaseInterface
) {

    companion object {
        val actionEnabled = MinSideEvent.ENABLE.toString().lowercase()
        val actionDisabled = MinSideEvent.DISABLE.toString().lowercase()
    }

    fun updateMikrofrontendForUserByHendelse(mottakerFnr: String, hendelse: Microfrontend) {
        val recordToSend = when (hendelse.microfrontendType) {
            MicrofrontendType.DIALOGMOTE
                -> mikrofrontendDialogmoteService.updateDialogmoteFrontendForUserByHendelse(hendelse)

            MicrofrontendType.AKTIVITETSKRAV
                -> mikrofrontendAktivitetskravService.createOrUpdateAktivitetskravMicrofrontendByHendelse(hendelse)

            MicrofrontendType.MER_OPPFOLGING -> mikrofrontendMerOppfolgingService.createEnableMerOppfolgingRecord(
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
        mikrofrontendsToClose.addAll(mikrofrontendAktivitetskravService.findExpiredAktivitetskravMikrofrontends())
        mikrofrontendsToClose.addAll(mikrofrontendMerOppfolgingService.findExpiredAktivitetskravMikrofrontends())
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

            HendelseType.SM_AKTIVITETSPLIKT
                -> Tjeneste.AKTIVITETSKRAV

            HendelseType.SM_MER_VEILEDNING
                -> Tjeneste.MER_OPPFOLGING

            else -> throw IllegalArgumentException("$this is not a valid type for updating MF state")
        }
}
