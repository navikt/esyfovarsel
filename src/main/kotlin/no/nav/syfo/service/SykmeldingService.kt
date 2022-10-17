package no.nav.syfo.service

import no.nav.syfo.consumer.syfosmregister.SykmeldingDTO
import no.nav.syfo.consumer.syfosmregister.SykmeldingerConsumer
import java.time.LocalDate

data class SykmeldingStatus(val isSykmeldtIJobb: Boolean, val sendtArbeidsgiver: Boolean)

class SykmeldingService constructor(private val sykmeldingerConsumer: SykmeldingerConsumer) {
    private fun isSendtAG(
        sykmeldingerPaaVarseldato: List<SykmeldingDTO>,
        virksomhetsnummer: String?,
    ): Boolean {
        if (virksomhetsnummer == null) return false

        val sendtSykmelding: SykmeldingDTO? = sykmeldingerPaaVarseldato
            .filter { sykmelding -> sykmelding.sykmeldingStatus.statusEvent == "SENDT" }
            .firstOrNull { sykmeldingDTO -> sykmeldingDTO.sykmeldingStatus.arbeidsgiver?.orgnummer == virksomhetsnummer }

        return sendtSykmelding !== null
    }

    private fun isSykmeldtIJobb(
        sykmeldingerPaaVarseldato: List<SykmeldingDTO>,
    ): Boolean {
        return sykmeldingerPaaVarseldato.any { sykmeldingDTO ->
            sykmeldingDTO.sykmeldingsperioder.firstOrNull { it.gradert != null }?.gradert != null
        }
    }

    suspend fun checkSykmeldingStatus(
        varselDato: LocalDate,
        fnr: String,
        virksomhetsnummer: String?
    ): SykmeldingStatus {
        val sykmeldingerPaaVarseldato: List<SykmeldingDTO> = sykmeldingerConsumer.getSykmeldingerPaDato(varselDato, fnr)
            ?: return SykmeldingStatus(isSykmeldtIJobb = false, sendtArbeidsgiver = false)

        val isSendtAG = isSendtAG(sykmeldingerPaaVarseldato, virksomhetsnummer)

        val isSykmeldtIJobb = isSykmeldtIJobb(sykmeldingerPaaVarseldato)

        return SykmeldingStatus(isSykmeldtIJobb = isSykmeldtIJobb, sendtArbeidsgiver = isSendtAG)
    }
}
