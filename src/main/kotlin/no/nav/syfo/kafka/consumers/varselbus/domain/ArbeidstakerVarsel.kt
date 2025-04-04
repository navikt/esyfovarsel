package no.nav.syfo.kafka.consumers.varselbus.domain

import java.net.URL

data class ArbeidstakerVarsel(
    val mottakerFnr: String,
    val brukernotifikasjonVarsel: BrukernotifikasjonVarsel? = null,
    val dinesykmeldteVarsel: //TODO
)

enum class BrukernotifikasjonType {
    OPPGAVE,
    BESKJED,
    DONE
}

sealed class BrukernotifikasjonVarsel {
    abstract val uuid: String
    abstract val varseltype: BrukernotifikasjonType

    data class Done(
        override val uuid: String,
    ) : BrukernotifikasjonVarsel() {
        override val varseltype = BrukernotifikasjonType.DONE
    }

    data class Oppgave(
        override val uuid: String,
        val content: String,
        val url: URL,
        val smsContent: String,
        val eksternVarsling: Boolean,
        val dagerTilDeaktivering: Long? = null,
        val reservert: Reservert? = null,

    ) : BrukernotifikasjonVarsel() {
        override val varseltype = BrukernotifikasjonType.OPPGAVE
    }

    data class Beskjed(
        override val uuid: String,
        val content: String,
        val url: URL?,
        val eksternVarsling: Boolean,
        val smsContent: String? = null,
        val dagerTilDeaktivering: Long? = null
    ) : BrukernotifikasjonVarsel() {
        override val varseltype = BrukernotifikasjonType.BESKJED
    }

    data class Reservert(
        val sendBrevHvisReservert: Boolean,
        val journalpostId: String,
    )
}
