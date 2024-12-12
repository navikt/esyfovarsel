package no.nav.syfo.producer.arbeidsgivernotifikasjon

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun LocalDateTime.formatAsISO8601DateTime(): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    return this.format(formatter)
}
