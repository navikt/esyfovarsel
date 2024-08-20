package no.nav.syfo.behandlendeenhet.domain

data class BehandlendeEnhet(
    var enhetId: String,
    var navn: String,
)

fun BehandlendeEnhet.isPilot(isProduction: Boolean): Boolean {
    if (isProduction) {
        return listOf("0624").contains(this.enhetId)
    }

    return listOf("0314").contains(this.enhetId)
}
