package no.nav.syfo.service.mikrofrontend

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PMikrofrontendSynlighet
import no.nav.syfo.db.fetchMikrofrontendSynlighetEntriesByFnr
import no.nav.syfo.db.updateMikrofrontendEntrySynligTomByFnrAndTjeneste
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.getSynligTom
import no.nav.syfo.kafka.producers.minsideMikrofrontend.MinSideRecord
import no.nav.syfo.kafka.producers.minsideMikrofrontend.Tjeneste
import no.nav.syfo.service.mikrofrontend.MikrofrontendService.Companion.actionEnabled
import no.nav.syfo.utils.DuplicateAktivitetskravException

class MikrofrontendAktivitetskravService(val database: DatabaseInterface) {
    val mikrofrontendId = "syfo-aktivitetskrav"

    fun createOrUpdateAktivitetskravMicrofrontendByHendelse(hendelse: ArbeidstakerHendelse): MinSideRecord? {
        return createOrUpdateMinSideRecord(hendelse)
    }

    private fun createOrUpdateMinSideRecord(hendelse: ArbeidstakerHendelse): MinSideRecord? {
        val userHasExistingEntries =
            existingMikrofrontendEntries(hendelse.arbeidstakerFnr, Tjeneste.AKTIVITETSKRAV).isNotEmpty()
        val ferdigstill = hendelse.ferdigstill ?: false

        throwExceptionOnIllegalUserState(
            userHasExistingEntries,
            ferdigstill,
            hendelse.type,
        )

        if (hendelse.type == HendelseType.SM_AKTIVITETSPLIKT_STATUS_FORHANDSVARSEL) {
            if (userHasExistingEntries) {
                setExpiryDateForMikrofrontendUser(hendelse)
            } else {
                return minSideRecordEnabled(hendelse.arbeidstakerFnr)
            }
        } else {
            if (!ferdigstill) {
                return minSideRecordEnabled(hendelse.arbeidstakerFnr)
            } else if (userHasExistingEntries) {
                return minSideRecordDisabled(hendelse.arbeidstakerFnr)
            }
        }

        return null
    }

    private fun setExpiryDateForMikrofrontendUser(hendelse: ArbeidstakerHendelse) {
        database.updateMikrofrontendEntrySynligTomByFnrAndTjeneste(
            hendelse.arbeidstakerFnr,
            Tjeneste.AKTIVITETSKRAV,
            hendelse.getSynligTom()!!.toLocalDate(),
        )
    }

    private fun minSideRecordEnabled(fnr: String) =
        MinSideRecord(
            eventType = actionEnabled,
            fnr = fnr,
            microfrontendId = mikrofrontendId,
        )

    fun minSideRecordDisabled(fnr: String) =
        MinSideRecord(
            eventType = MikrofrontendService.actionDisabled,
            fnr = fnr,
            microfrontendId = MikrofrontendDialogmoteService.dialogmoteMikrofrontendId,
        )

    private fun existingMikrofrontendEntries(fnr: String, tjeneste: Tjeneste): List<PMikrofrontendSynlighet> {
        return database.fetchMikrofrontendSynlighetEntriesByFnr(fnr)
            .filter { it.tjeneste == tjeneste.name }
    }

    private fun throwExceptionOnIllegalUserState(
        existingEntry: Boolean,
        ferdigstill: Boolean,
        hendelseType: HendelseType,
    ) {
        if (isExistingEntryIkkeFerdigstilt(existingEntry, ferdigstill)) {
            when (hendelseType) {
                HendelseType.SM_AKTIVITETSPLIKT_STATUS_FORHANDSVARSEL,
                HendelseType.SM_AKTIVITETSPLIKT_STATUS_UNNTAK,
                HendelseType.SM_AKTIVITETSPLIKT_STATUS_OPPFYLT,
                HendelseType.SM_AKTIVITETSPLIKT_STATUS_IKKE_AKTUELL,
                -> throw DuplicateAktivitetskravException(hendelseType)

                else -> {}
            }
        }
    }

    private fun isExistingEntryIkkeFerdigstilt(
        existingEntry: Boolean,
        ferdigstill: Boolean,
    ): Boolean {
        return existingEntry && !ferdigstill
    }
}
