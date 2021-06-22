package no.nav.syfo.service

import io.ktor.util.*
import no.nav.syfo.consumer.SykmeldingerConsumer
import no.nav.syfo.consumer.syfosmregister.SykmeldtStatusResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate

@KtorExperimentalAPI
class SykmeldingService constructor(private val sykmeldingerConsumer: SykmeldingerConsumer) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.service.SykmeldingService")

    suspend fun isNot100SykmeldtPaVarlingsdato(varselDato: LocalDate, fnr: String): Boolean? {
        val sykmeldtStatus: SykmeldtStatusResponse? = sykmeldingerConsumer.getSykmeldtStatusPaDato(varselDato, fnr)
        return if (sykmeldtStatus!!.gradert != null) {
            sykmeldtStatus.gradert
        } else {
            null
        }
    }
}
