package no.nav.syfo.consumer.domain

import java.io.Serializable

data class DigitalKontaktinfo (
    val personident: String,
    val aktiv: Boolean,
    val kanVarsles: Boolean? = null,
    val reservert: Boolean? = null,
    val spraak: String? = null,
    val epostadresse: String? = null,
    val mobiltelefonnummer: String? = null,
    val sikkerDigitalPostkasse: SikkerDigitalPostkasse? = null
): Serializable

data class SikkerDigitalPostkasse (
    val adresse: String,
    val leverandoerAdresse: String,
    val leverandoerSertifikat: String
): Serializable
