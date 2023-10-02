package no.nav.syfo.service.microfrontend

import no.nav.syfo.db.*
import no.nav.syfo.db.domain.toMikrofrontendSynlighet
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.getSynligTom
import no.nav.syfo.kafka.producers.mineside_microfrontend.MinSideRecord
import no.nav.syfo.kafka.producers.mineside_microfrontend.Tjeneste
import no.nav.syfo.service.microfrontend.MikrofrontendService.Companion.actionDisabled
import no.nav.syfo.service.microfrontend.MikrofrontendService.Companion.actionEnabled
import no.nav.syfo.utils.DuplicateAktivitetskravException
import org.slf4j.LoggerFactory

class MikrofrontendAktivitetskravService(
    val database: DatabaseInterface
) {
    private val log = LoggerFactory.getLogger(MikrofrontendAktivitetskravService::class.java)

    companion object {
        const val mikrofrontendId = "syfo-aktivitetskrav"
    }

    fun updateAktivitetskravMikrofrontendForUserByHendelse(hendelse: ArbeidstakerHendelse): MinSideRecord? {
        return when (hendelse.type) {
            HendelseType.SM_FORHANDSVARSEL_STANS -> setMikrofrontendSynlighet(hendelse)
            HendelseType.SM_AKTIVITETSKRAV -> setMikrofrontendSynlighet(hendelse)
            else -> null
        }
    }

    fun findExpiredAktivitetskravMikrofrontends(): List<Triple<String, String, Tjeneste>> {
        return database.fetchFnrsWithExpiredMicrofrontendEntries(Tjeneste.AKTIVITETSKRAV)
            .map { Triple(it, mikrofrontendId, Tjeneste.AKTIVITETSKRAV) }
    }

    private fun setNewDateForMikrofrontendUser(hendelse: ArbeidstakerHendelse): MinSideRecord? {
        return database.fetchMikrofrontendSynlighetEntriesByFnr(hendelse.arbeidstakerFnr)
            .lastOrNull { entry -> entry.tjeneste == Tjeneste.AKTIVITETSKRAV.name }
            ?.let {
                database.updateMikrofrontendEntrySynligTomByExistingEntry(
                    it.toMikrofrontendSynlighet(),
                    hendelse.getSynligTom()!!.toLocalDate()
                )
                return null
            }
            ?: run {
                log.warn(
                    "[MIKROFRONTEND_SERVICE]: Received ${hendelse.type} from VarselBus without corresponding entry " +
                            "in MIKROFRONTEND_SYNLIGHET DB-table. Creating new entry ..."
                )
                minSideRecordEnabled(hendelse.arbeidstakerFnr)
            }
    }

    private fun setMikrofrontendSynlighet(hendelse: ArbeidstakerHendelse): MinSideRecord? {
        val userHasExistingEntries =
            existingMikrofrontendEntries(hendelse.arbeidstakerFnr, Tjeneste.AKTIVITETSKRAV).isNotEmpty()

        throwExceptionOnIllegalUserState(
            userHasExistingEntries,
            hendelse.ferdigstill ?: false,
            hendelse.type
        )

        if (hendelse.type == HendelseType.SM_FORHANDSVARSEL_STANS) {
            if (userHasExistingEntries) {
                setExpiryDateForMikrofrontendUser(hendelse)
            } else {
                return minSideRecordEnabled(hendelse.arbeidstakerFnr)
            }
        }

        return null
    }

    private fun setExpiryDateForMikrofrontendUser(hendelse: ArbeidstakerHendelse) {
        database.updateMikrofrontendEntrySynligTomByFnrAndTjeneste(
            hendelse.arbeidstakerFnr,
            Tjeneste.AKTIVITETSKRAV,
            hendelse.getSynligTom()!!.toLocalDate()
        )
    }

    private fun throwExceptionOnIllegalUserState(
        existingEntry: Boolean,
        ferdigstill: Boolean,
        hendelseType: HendelseType
    ) {
        if (existingEntry &&
            hendelseType == HendelseType.SM_FORHANDSVARSEL_STANS &&
            !ferdigstill
        ) {
            throw DuplicateAktivitetskravException()
        }
    }

    private fun existingMikrofrontendEntries(fnr: String, tjeneste: Tjeneste) =
        database.fetchMikrofrontendSynlighetEntriesByFnr(fnr)
            .filter { it.tjeneste == tjeneste.name }

    private fun minSideRecordEnabled(fnr: String) =
        MinSideRecord(
            eventType = actionEnabled,
            fnr = fnr,
            microfrontendId = mikrofrontendId
        )

    fun minSideRecordDisabled(fnr: String) =
        MinSideRecord(
            eventType = actionDisabled,
            fnr = fnr,
            microfrontendId = mikrofrontendId
        )
}
