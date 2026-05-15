package no.nav.syfo.producer.arbeidsgivernotifikasjon

import com.apollo.graphql.NySakMutation
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.ArbeidsgiverDeleteNotifikasjon
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.ArbeidsgiverNotifikasjon
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NyKalenderInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NyStatusSakInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.OppdaterKalenderInput

interface IArbeidsgiverNotifikasjonProdusent {
    suspend fun createNewOppgaveForArbeidsgiver(arbeidsgiverNotifikasjon: ArbeidsgiverNotifikasjon): String?

    suspend fun createNewBeskjedForArbeidsgiver(arbeidsgiverNotifikasjonInput: ArbeidsgiverNotifikasjon): String?

    suspend fun createNewSak(mutation: NySakMutation): String?

    suspend fun nyStatusSak(nyStatusSakInput: NyStatusSakInput): String?

    suspend fun createNewKalenderavtale(nyKalenderInput: NyKalenderInput): String?

    suspend fun updateKalenderavtale(oppdaterKalenderInput: OppdaterKalenderInput): String?

    suspend fun deleteNotifikasjonForArbeidsgiver(arbeidsgiverDeleteNotifikasjon: ArbeidsgiverDeleteNotifikasjon)
}
