package no.nav.syfo.service

import no.nav.syfo.domain.*
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.*
import java.util.function.Predicate

const val SVAR_MOTEBEHOV_DAGER = 112

class MotebehovService {
    val LOG = LoggerFactory.getLogger(this::class.simpleName)

    fun datoForSvarMotebehov(sykmelding: Sykmelding, sykeforloep: Sykeforloep?): Optional<LocalDate> {
        val svarmotebehovdato = finnSvarMotebehovdato(sykeforloep!!)
        if (!erSvarMotebehovDatoPassert(svarmotebehovdato)
                && erSykmeldtPaaDato(sykeforloep, svarmotebehovdato!!)
                && !erAlleredePlanlagt(sykmelding.bruker.aktoerId, sykeforloep)
                && !harAlleredeBlittSendt(sykmelding.bruker.aktoerId, sykeforloep)) {
            LOG.info("Planlegger svarmotebehovvarsel med dato {}", svarmotebehovdato)
            return Optional.of(svarmotebehovdato)
        }
        return Optional.empty()
    }

    private fun erSvarMotebehovDatoPassert(svarmotebehovDato: LocalDate?): Boolean {
        val motebehovdatoErPassert = svarmotebehovDato!!.isBefore(LocalDate.now())
        if (motebehovdatoErPassert) {
            LOG.info("Planlegger ikke svarmotebehovvarsel: MotebehovdatoErPassert er passert!")
        }
        return motebehovdatoErPassert
    }

    fun erSykmeldtPaaDato(sykeforloep: Sykeforloep, svarmotebehovdato: LocalDate): Boolean {
        val aktiveSykmeldingerVedSvarMotebehov: List<Sykmelding> = sykeforloep.hentSykmeldingerGittDato(svarmotebehovdato)
        val erIkkeSykmeldtPaaDato = aktiveSykmeldingerVedSvarMotebehov.isNotEmpty()
        if (erIkkeSykmeldtPaaDato) {
            LOG.info("Planlegger ikke svarmotebehovvarsel: Ikke sykmeldt på svarmotebehovdato!")
        }
        return erIkkeSykmeldtPaaDato
    }

    private fun erAlleredePlanlagt(aktoerId: String, sykeforloep: Sykeforloep): Boolean {
        return false
    }

    private fun harAlleredeBlittSendt(aktoerId: String, sykeforloep: Sykeforloep): Boolean {
        return false
    }

    private fun finnSvarMotebehovdato(sykeforloep: Sykeforloep): LocalDate {
        return sykeforloep.oppfolgingsdato.plusDays(SVAR_MOTEBEHOV_DAGER.toLong())
    }
}