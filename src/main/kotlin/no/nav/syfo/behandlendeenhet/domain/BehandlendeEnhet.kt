package no.nav.syfo.behandlendeenhet.domain

data class BehandlendeEnhet(
    var enhetId: String,
    var navn: String,
)

fun BehandlendeEnhet.isPilot(): Boolean {
    return listOf("0314").contains(this.enhetId)
}
