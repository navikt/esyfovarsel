package no.nav.syfo.consumer.domain

import java.io.Serializable

data class DigitalKontaktinfo (
    val kontaktinfo: Kontaktinfo?
): Serializable

data class Kontaktinfo (
    val ident: Ident
)

data class Ident(
    val personident: String,
    val kanVarsles: Boolean,
    val reservert: Boolean,
    val epostadresse: String?,
    val mobiltelefonnummer: String?,
    val spraak: String?
)

fun DigitalKontaktinfo.kanVarsle(): Boolean {
    return (this.kontaktinfo?.ident?.kanVarsles) ?: false
}
