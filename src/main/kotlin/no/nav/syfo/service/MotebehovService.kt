package no.nav.syfo.service

import no.nav.syfo.domain.HendelseType.SVAR_MOTEBEHOV
import no.nav.syfo.domain.Sykeforloep
import no.nav.syfo.domain.Sykmelding
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.*

const val SVAR_MOTEBEHOV_DAGER: Long = 112L

class MotebehovService(varselStatusService: VarselStatusService) {
    val LOGGER: Logger = LoggerFactory.getLogger(this::class.simpleName)

    var varselStatusService = varselStatusService

    fun datoForSvarMotebehov(sykmelding: Sykmelding, sykeforloep: Sykeforloep): Optional<LocalDate> {
        val svarmotebehovdato = finnSvarMotebehovdato(sykeforloep)
        if (!erSvarMotebehovDatoPassert(svarmotebehovdato)
                && erSykmeldtPaaDato(sykeforloep, svarmotebehovdato)
                && !erAlleredePlanlagt(sykmelding.bruker.aktoerId, sykeforloep.sykmeldinger)
                && !harAlleredeBlittSendt(sykmelding.bruker.aktoerId, sykeforloep.sykmeldinger)) {
            LOGGER.info("Planlegger svarmotebehovvarsel med dato {}", svarmotebehovdato)
            return Optional.of(svarmotebehovdato)
        }
        return Optional.empty()
    }

    private fun erSvarMotebehovDatoPassert(svarmotebehovDato: LocalDate): Boolean {
        val motebehovdatoErPassert = svarmotebehovDato.isBefore(LocalDate.now())
        if (motebehovdatoErPassert) {
            LOGGER.info("Planlegger ikke svarmotebehovvarsel: MotebehovdatoErPassert er passert!")
        }
        return motebehovdatoErPassert
    }

    fun erSykmeldtPaaDato(sykeforloep: Sykeforloep, svarmotebehovdato: LocalDate): Boolean {
        val aktiveSykmeldingerVedSvarMotebehov: List<Sykmelding> = sykeforloep.hentSykmeldingerGittDato(svarmotebehovdato)
        val erIkkeSykmeldtPaaDato = aktiveSykmeldingerVedSvarMotebehov.isNotEmpty()
        if (erIkkeSykmeldtPaaDato) {
            LOGGER.info("Planlegger ikke svarmotebehovvarsel: Ikke sykmeldt på svarmotebehovdato!")
        }
        return erIkkeSykmeldtPaaDato
    }

    private fun finnSvarMotebehovdato(sykeforloep: Sykeforloep): LocalDate {
        return sykeforloep.oppfolgingsdato.plusDays(SVAR_MOTEBEHOV_DAGER.toLong())
    }

    private fun harAlleredeBlittSendt(aktoerId: String, sykmeldinger: List<Sykmelding>): Boolean {
        return varselStatusService.harAlleredeBlittSendt(aktoerId, sykmeldinger, SVAR_MOTEBEHOV)
    }

    private fun erAlleredePlanlagt(aktoerId: String, sykmeldinger: List<Sykmelding>): Boolean {
        return varselStatusService.erAlleredePlanlagt(aktoerId, sykmeldinger, SVAR_MOTEBEHOV)
    }
}
