package no.nav.syfo.producer.arbeidsgivernotifikasjon

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun LocalDateTime.formatAsISO8601(): String = this.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)