package no.nav.syfo.service

import no.nav.syfo.consumer.syfosmregister.SykmeldingDTO
import no.nav.syfo.consumer.syfosmregister.SykmeldingerConsumer
import java.time.LocalDate

data class SykmeldingStatus(val gradert: Boolean, val sendtArbeidsgiver: Boolean)

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

    private fun isGradert(
        sykmeldingerPaaVarseldato: List<SykmeldingDTO>,
        virksomhetsnummer: String?,
    ): Boolean {
        val sykmelding = if (virksomhetsnummer == null) {
            sykmeldingerPaaVarseldato.first()
        } else {
            sykmeldingerPaaVarseldato.find { sykmeldingDTO -> sykmeldingDTO.sykmeldingStatus.arbeidsgiver?.orgnummer == virksomhetsnummer }
        }

        return sykmelding?.sykmeldingsperioder?.firstOrNull { it.gradert != null }?.gradert != null
    }

    suspend fun checkSykmeldingStatus(
        varselDato: LocalDate,
        fnr: String,
        virksomhetsnummer: String?
    ): SykmeldingStatus {
        val sykmeldingerPaaVarseldato: List<SykmeldingDTO> = sykmeldingerConsumer.getSykmeldingerPaDato(varselDato, fnr)
            ?: return SykmeldingStatus(gradert = false, sendtArbeidsgiver = false)

        val isSendtAG = isSendtAG(sykmeldingerPaaVarseldato, virksomhetsnummer)

        val isGradert = isGradert(sykmeldingerPaaVarseldato, virksomhetsnummer)

        return SykmeldingStatus(gradert = isGradert, sendtArbeidsgiver = isSendtAG)
    }
}
