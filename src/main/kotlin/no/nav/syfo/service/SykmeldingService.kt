package no.nav.syfo.service

import io.ktor.util.*
import no.nav.syfo.consumer.SykmeldingerConsumer
import no.nav.syfo.consumer.syfosmregister.SyfosmregisterResponse
import no.nav.syfo.consumer.syfosmregister.SykmeldingPeriode
import java.time.LocalDate

class SykmeldingService @KtorExperimentalAPI constructor(val sykmeldingerConsumer: SykmeldingerConsumer) {

    @KtorExperimentalAPI
    suspend fun isNot100SykmeldtPaVarlingsdato(varselDato: LocalDate, fnr: String): Boolean {
        val sykmeldinger: List<SyfosmregisterResponse>? = sykmeldingerConsumer.getSykmeldingerForVarslingDato(varselDato, fnr)
        val sykmeldingsperioder: List<SykmeldingPeriode> = sykmeldinger?.map { it.sykmeldingsperioder }?.flatten() ?: listOf()

        return sykmeldingsperioder.filter { it.gradert!!.grad == 100 }.isEmpty()
    }
}
