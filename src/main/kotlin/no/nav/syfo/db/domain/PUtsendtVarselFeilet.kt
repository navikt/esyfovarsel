package no.nav.syfo.db.domain

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.kafka.common.createObjectMapper
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidsgiverNotifikasjonTilAltinnRessursHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import java.time.LocalDateTime

private val objectMapper = createObjectMapper()

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
    val resendExhausted: Boolean? = null,
    val hendelseJson: String? = null,
)

fun PUtsendtVarselFeilet.toArbeidstakerHendelse(): ArbeidstakerHendelse =
    ArbeidstakerHendelse(
        arbeidstakerFnr = arbeidstakerFnr,
        orgnummer = orgnummer,
        type = HendelseType.valueOf(hendelsetypeNavn),
        ferdigstill = false,
        data = null,
    )

fun PUtsendtVarselFeilet.toArbeidsgiverNotifikasjonTilAltinnRessursHendelse(): ArbeidsgiverNotifikasjonTilAltinnRessursHendelse {
    val hendelseJson =
        requireNotNull(hendelseJson) {
            "Mangler hendelseJson for feilet arbeidsgiverhendelse"
        }
    return objectMapper.readValue(hendelseJson)
}

fun PUtsendtVarselFeilet.toNarmesteLederHendelse(): NarmesteLederHendelse {
    val hendelseJson =
        requireNotNull(hendelseJson) {
            "Mangler hendelseJson for feilet nærmeste-leder-hendelse"
        }
    val rootNode = objectMapper.readTree(hendelseJson)
    return objectMapper.readValue<NarmesteLederHendelse>(hendelseJson).also {
        it.data = rootNode["data"]
    }
}
