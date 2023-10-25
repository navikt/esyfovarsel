package no.nav.syfo.service.mikrofrontend

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PMikrofrontendSynlighet
import no.nav.syfo.db.fetchFnrsWithExpiredMicrofrontendEntries
import no.nav.syfo.db.fetchMikrofrontendSynlighetEntriesByFnr
import no.nav.syfo.db.updateMikrofrontendEntrySynligTomByFnrAndTjeneste
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.getSynligTom
import no.nav.syfo.kafka.producers.minsideMikrofrontend.MinSideRecord
import no.nav.syfo.kafka.producers.minsideMikrofrontend.Tjeneste
import no.nav.syfo.service.mikrofrontend.MikrofrontendService.Companion.actionEnabled
import no.nav.syfo.utils.DuplicateAktivitetskravException
import org.slf4j.LoggerFactory

class MikrofrontendAktivitetskravService(val database: DatabaseInterface) {
    val mikrofrontendId = "syfo-aktivitetskrav"

    fun createOrUpdateAktivitetskravMicrofrontendByHendelse(hendelse: ArbeidstakerHendelse): MinSideRecord? {
        return createOrUpdateMinSideRecord(hendelse)
    }

    private fun createOrUpdateMinSideRecord(hendelse: ArbeidstakerHendelse): MinSideRecord? {
        val isMikrofrontendActiveForUser =
            existingMikrofrontendEntries(hendelse.arbeidstakerFnr, Tjeneste.AKTIVITETSKRAV).isNotEmpty()
        if (hendelse.shouldActivateMikrofrontend()) {
            if (!isMikrofrontendActiveForUser)
                return minSideRecordEnabled(hendelse.arbeidstakerFnr)

        }

        if (!isMikrofrontendActiveForUser && hendelse.shouldActivateMikrofrontend()) {
            return minSideRecordEnabled(hendelse.arbeidstakerFnr)
        } else if (isMikrofrontendActiveForUser){
            setExpiryDateForMikrofrontendUser(hendelse)
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

    fun findExpiredAktivitetskravMikrofrontends(): List<Triple<String, String, Tjeneste>> {
        return database.fetchFnrsWithExpiredMicrofrontendEntries(Tjeneste.AKTIVITETSKRAV)
            .map { Triple(it, mikrofrontendId, Tjeneste.AKTIVITETSKRAV) }
    }

    private fun existingMikrofrontendEntries(fnr: String, tjeneste: Tjeneste): List<PMikrofrontendSynlighet> {
        return database.fetchMikrofrontendSynlighetEntriesByFnr(fnr)
            .filter { it.tjeneste == tjeneste.name }
    }

    private fun ArbeidstakerHendelse.shouldActivateMikrofrontend() =
                   this.type == HendelseType.SM_AKTIVITETSPLIKT_STATUS_FORHANDSVARSEL
                || this.type == HendelseType.SM_AKTIVITETSPLIKT_STATUS_NY
}
