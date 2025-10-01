package no.nav.syfo.kafka.consumers.arbeidstakervarsel.domain

import java.time.OffsetDateTime
import java.util.*

enum class DineSykmeldteEventType {
    ENABLE,
    DISABLE
}

data class DineSykmeldteVarsel(
    val id: UUID,
    val eventType: DineSykmeldteEventType,
    val ansattFnr: String,
    val orgnr: String,
    val oppgavetype: String,
    val lenke: String?,
    val tekst: String,
    val utlopstidspunkt: OffsetDateTime? = null,
    val scheduledRetry: Boolean = true,
)
