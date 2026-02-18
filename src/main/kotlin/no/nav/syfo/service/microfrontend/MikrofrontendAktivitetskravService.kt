package no.nav.syfo.service.microfrontend

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PMikrofrontendSynlighet
import no.nav.syfo.db.fetchFnrsWithExpiredMicrofrontendEntries
import no.nav.syfo.db.fetchMikrofrontendSynlighetEntriesByFnr
import no.nav.syfo.db.updateMikrofrontendEntrySynligTomByFnrAndTjeneste
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.getSynligTom
import no.nav.syfo.kafka.producers.mineside_microfrontend.MinSideRecord
import no.nav.syfo.kafka.producers.mineside_microfrontend.Tjeneste
import no.nav.syfo.service.microfrontend.MikrofrontendService.Companion.actionEnabled
import no.nav.syfo.utils.dataToVarselData

class MikrofrontendAktivitetskravService(
    val database: DatabaseInterface,
) {
    private val mikrofrontendId = "syfo-aktivitetskrav"

    fun createOrUpdateAktivitetskravMicrofrontendByHendelse(hendelse: ArbeidstakerHendelse): MinSideRecord? =
        createOrUpdateMinSideRecord(hendelse)

    private fun createOrUpdateMinSideRecord(hendelse: ArbeidstakerHendelse): MinSideRecord? {
        val isMikrofrontendActiveForUser =
            existingMikrofrontendEntries(hendelse.arbeidstakerFnr).isNotEmpty()

        val actions = dataToVarselData(hendelse.data).aktivitetskrav
        requireNotNull(actions)

        if (!isMikrofrontendActiveForUser && actions.enableMicrofrontend) {
            return minSideRecordEnabled(hendelse.arbeidstakerFnr)
        } else if (isMikrofrontendActiveForUser && actions.extendMicrofrontendDuration) {
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

    fun findExpiredAktivitetskravMikrofrontends(): List<Triple<String, String, Tjeneste>> =
        database
            .fetchFnrsWithExpiredMicrofrontendEntries(Tjeneste.AKTIVITETSKRAV)
            .map { Triple(it, mikrofrontendId, Tjeneste.AKTIVITETSKRAV) }

    private fun existingMikrofrontendEntries(fnr: String): List<PMikrofrontendSynlighet> =
        database
            .fetchMikrofrontendSynlighetEntriesByFnr(fnr)
            .filter { it.tjeneste == Tjeneste.AKTIVITETSKRAV.name }
}
