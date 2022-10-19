package no.nav.syfo.kafka.consumers.utbetaling.domain

import java.time.LocalDate

data class UtbetalingUtbetalt(
    val fødselsnummer: String,
    val foreløpigBeregnetSluttPåSykepenger: LocalDate? = null,
)
