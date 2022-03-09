package no.nav.syfo.service

import no.nav.syfo.consumer.syfosmregister.SykmeldingerConsumer
import no.nav.syfo.consumer.syfosmregister.SykmeldtStatusResponse
import java.time.LocalDate

class SykmeldingService constructor(private val sykmeldingerConsumer: SykmeldingerConsumer) {
    suspend fun isNot100SykmeldtPaVarlingsdato(varselDato: LocalDate, fnr: String): Boolean? {
        val sykmeldtStatus: SykmeldtStatusResponse? = sykmeldingerConsumer.getSykmeldtStatusPaDato(varselDato, fnr)
        return if (sykmeldtStatus!!.gradert != null) {
            sykmeldtStatus.gradert
        } else {
            null
        }
    }
}
