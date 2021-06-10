package no.nav.syfo.service

import io.ktor.util.*
import no.nav.syfo.consumer.SykmeldingerConsumer
import no.nav.syfo.consumer.syfosmregister.SykmeldtStatusResponse
import java.time.LocalDate

class SykmeldingService @KtorExperimentalAPI constructor(val sykmeldingerConsumer: SykmeldingerConsumer) {

    @KtorExperimentalAPI
    suspend fun isNot100SykmeldtPaVarlingsdato(varselDato: LocalDate, fnr: String): Boolean {
        val sykmeldtStatus: SykmeldtStatusResponse? = sykmeldingerConsumer.getSykmeldingerForVarslingDato(varselDato, fnr)

        return if (sykmeldtStatus?.gradert != null) {
            sykmeldtStatus.gradert
        } else {
            true
        }
    }
}
