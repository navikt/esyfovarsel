package no.nav.syfo.service.microfrontend

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.fetchFnrsWithExpiredMicrofrontendEntries
import no.nav.syfo.db.fetchMikrofrontendSynlighetEntriesByFnr
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.producers.mineside_microfrontend.MinSideRecord
import no.nav.syfo.kafka.producers.mineside_microfrontend.Tjeneste
import no.nav.syfo.service.microfrontend.MikrofrontendService.Companion.actionEnabled

class MikrofrontendMerOppfolgingService(val database: DatabaseInterface) {
    private val mikrofrontendId = "syfo-meroppfolging"

    fun createEnableMerOppfolgingRecord(hendelse: ArbeidstakerHendelse): MinSideRecord? {
        val isMikrofrontendActiveForUser = database.fetchMikrofrontendSynlighetEntriesByFnr(hendelse.arbeidstakerFnr)
            .any { it.tjeneste == Tjeneste.MER_OPPFOLGING.name }

        if (!isMikrofrontendActiveForUser) {
            return MinSideRecord(
                eventType = actionEnabled,
                fnr = hendelse.arbeidstakerFnr,
                microfrontendId = mikrofrontendId,
            )
        }

        return null
    }

    fun findExpiredAktivitetskravMikrofrontends(): List<Triple<String, String, Tjeneste>> {
        return database.fetchFnrsWithExpiredMicrofrontendEntries(Tjeneste.MER_OPPFOLGING)
            .map { Triple(it, mikrofrontendId, Tjeneste.MER_OPPFOLGING) }
    }

}
