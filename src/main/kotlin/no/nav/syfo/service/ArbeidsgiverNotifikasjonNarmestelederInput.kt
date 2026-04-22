package no.nav.syfo.service

import no.nav.syfo.service.Meldingstype.BESKJED
import java.time.LocalDateTime
import java.util.UUID

sealed class IArbeidsgiverNotifikasjonInput {
    abstract val uuid: UUID
    abstract val virksomhetsnummer: String
    abstract val merkelapp: String
    abstract val messageText: String
    abstract val epostTittel: String
    abstract val epostHtmlBody: String
    abstract val hardDeleteDate: LocalDateTime
    abstract val meldingstype: Meldingstype
    abstract val grupperingsid: String
    abstract val link: String?
}

data class ArbeidsgiverNotifikasjonNarmestelederInput(
    override val uuid: UUID,
    override val virksomhetsnummer: String,
    val narmesteLederFnr: String?,
    val ansattFnr: String,
    override val merkelapp: String,
    override val messageText: String,
    override val epostTittel: String,
    override val epostHtmlBody: String,
    override val hardDeleteDate: LocalDateTime,
    override val meldingstype: Meldingstype = BESKJED,
    override val grupperingsid: String,
    override val link: String? = null,
) : IArbeidsgiverNotifikasjonInput()

data class ArbeidsgiverNotifikasjonAltinnRessursInput(
    override val uuid: UUID,
    override val virksomhetsnummer: String,
    override val merkelapp: String,
    override val messageText: String,
    override val epostTittel: String,
    override val epostHtmlBody: String,
    override val hardDeleteDate: LocalDateTime,
    override val meldingstype: Meldingstype = BESKJED,
    override val grupperingsid: String,
    override val link: String? = null,
    val ressursId: String,
    val ressursUrl: String,
) : IArbeidsgiverNotifikasjonInput()
