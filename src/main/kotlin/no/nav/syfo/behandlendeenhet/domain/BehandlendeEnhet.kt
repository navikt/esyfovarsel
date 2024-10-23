package no.nav.syfo.behandlendeenhet.domain

const val OVRE_EIKER_ENHETSNUMMER = "0624"
const val ASKER_ENHETSNUMMER = "0220"
const val NORDRE_AKER_ENHETSNUMMER = "0331"
const val TEST_ENHETSNUMMER = "0314"
const val TEST_ENHETSNUMMER_2 = "1203"

data class BehandlendeEnhet(
    var enhetId: String,
    var navn: String,
)

fun BehandlendeEnhet.isPilot(isProd: Boolean): Boolean {
    if (isProd) {
        return listOf(OVRE_EIKER_ENHETSNUMMER, ASKER_ENHETSNUMMER, NORDRE_AKER_ENHETSNUMMER).contains(this.enhetId)
    }

    return listOf(TEST_ENHETSNUMMER, TEST_ENHETSNUMMER_2).contains(this.enhetId)
}
