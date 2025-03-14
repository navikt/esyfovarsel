package no.nav.syfo.db.domain

import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import java.time.LocalDateTime

data class PUtsendtVarselFeilet(
    val uuid: String,
    val uuidEksternReferanse: String?,
    val arbeidstakerFnr: String,
    val narmesteLederFnr: String?,
    val orgnummer: String?,
    val hendelsetypeNavn: String,
    val arbeidsgivernotifikasjonMerkelapp: String?,
    val brukernotifikasjonerMeldingType: String?,
    val journalpostId: String?,
    val kanal: String?,
    val feilmelding: String?,
    val utsendtForsokTidspunkt: LocalDateTime,
    val isForcedLetter: Boolean? = false,
    val isResendt: Boolean? = false,
)

fun PUtsendtVarselFeilet.toArbeidstakerHendelse(): ArbeidstakerHendelse {
    return ArbeidstakerHendelse(
        arbeidstakerFnr = arbeidstakerFnr,
        orgnummer = orgnummer,
        type = HendelseType.valueOf(hendelsetypeNavn),
        ferdigstill = false,
        data = null,
    )
}
