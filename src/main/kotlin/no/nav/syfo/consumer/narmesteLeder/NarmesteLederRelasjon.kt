package no.nav.syfo.consumer.narmesteLeder

import java.io.Serializable
import java.time.LocalDate

data class NarmesteLederRelasjon(
    val narmesteLederId: String? = null,
    val fnr: String? = null,
    val orgnummer: String? = null,
    val narmesteLederFnr: String? = null,
    val narmesteLederTelefonnummer: String? = null,
    val narmesteLederEpost: String? = null,
    val aktivFom: LocalDate? = null,
    val aktivTom: LocalDate? = null,
    val arbeidsgiverForskutterer: Boolean = false,
    val skrivetilgang: Boolean = false,
    val tilganger: List<Tilgang>,
    val navn: String?,
) : Serializable

enum class Tilgang : Serializable {
    SYKMELDING,
    SYKEPENGESOKNAD,
    MOTE,
    OPPFOLGINGSPLAN,
}
