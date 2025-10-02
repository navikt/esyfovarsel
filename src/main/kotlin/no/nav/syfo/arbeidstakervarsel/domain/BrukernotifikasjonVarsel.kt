package no.nav.syfo.arbeidstakervarsel.domain

import java.net.URL

enum class BrukernotifikasjonType {
    OPPGAVE,
    BESKJED,
    DONE
}

sealed class BrukernotifikasjonVarsel {
    abstract val uuid: String
    abstract val varseltype: BrukernotifikasjonType
    abstract val scheduledRetry: Boolean

    data class Done(
        override val uuid: String
    ) : BrukernotifikasjonVarsel() {
        override val varseltype = BrukernotifikasjonType.DONE
        override val scheduledRetry: Boolean = true
    }

    data class Oppgave(
        override val uuid: String,
        val content: String,
        val url: URL,
        val smsContent: String,
        val eksternVarsling: Boolean,
        val dagerTilDeaktivering: Long? = null,
    ) : BrukernotifikasjonVarsel() {
        override val varseltype = BrukernotifikasjonType.OPPGAVE
        override val scheduledRetry: Boolean = true
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
        override val scheduledRetry: Boolean = true
    }
}
