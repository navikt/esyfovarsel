package no.nav.syfo.producer.arbeidsgivernotifikasjon.domain

internal fun String.splitEpostadresser(): List<String> =
    split(";")
        .map { it.trim() }
        .filter { it.isNotBlank() }
