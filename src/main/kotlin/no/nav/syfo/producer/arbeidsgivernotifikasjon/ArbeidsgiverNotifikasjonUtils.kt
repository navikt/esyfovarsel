package no.nav.syfo.producer.arbeidsgivernotifikasjon

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

fun LocalDateTime.formatAsISO8601(): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    return this.format(formatter)
}

fun OffsetDateTime.formatAsISO8601(): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
    return this.toLocalDateTime().format(formatter)
}
