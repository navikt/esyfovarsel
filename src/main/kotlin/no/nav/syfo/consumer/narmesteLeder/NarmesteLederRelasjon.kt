package no.nav.syfo.consumer.narmesteLeder

import java.io.Serializable
import java.time.LocalDate

data class NarmesteLederRelasjon(
    var narmesteLederId: String? = null,
    var fnr: String? = null,
    var orgnummer: String? = null,
    var narmesteLederFnr: String? = null,
    var narmesteLederTelefonnummer: String? = null,
    var narmesteLederEpost: String? = null,
    var aktivFom: LocalDate? = null,
    var aktivTom: LocalDate? = null,
    var arbeidsgiverForskutterer: Boolean = false,
    var skrivetilgang: Boolean = false,
    var tilganger: List<Tilgang>,
    var navn: String?
) : Serializable

enum class Tilgang : Serializable {
    SYKMELDING,
    SYKEPENGESOKNAD,
    MOTE,
    OPPFOLGINGSPLAN,
}
