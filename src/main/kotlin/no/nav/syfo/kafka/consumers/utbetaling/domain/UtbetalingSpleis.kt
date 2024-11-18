package no.nav.syfo.kafka.consumers.utbetaling.domain

import java.time.LocalDate

data class UtbetalingSpleis(
    val fødselsnummer: String,
    val organisasjonsnummer: String? = null,
    val event: String,
    val type: String? = null,
    val foreløpigBeregnetSluttPåSykepenger: LocalDate? = null,
    val forbrukteSykedager: Int? = null,
    val gjenståendeSykedager: Int? = null,
    val stønadsdager: Int? = null,
    val antallVedtak: Int? = null,
    val fom: LocalDate,
    val tom: LocalDate,
    val utbetalingId: String,
    val korrelasjonsId: String,
)

const val UTBETALING_UTBETALT = "utbetaling_utbetalt"
const val UTBETALING_UTEN_UTBETALING = "utbetaling_uten_utbetaling"
