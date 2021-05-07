package no.nav.syfo.testutil

fun String.extractPortFromUrl() : Int {
    return subSequence(lastIndexOf(':') + 1, length).toString().toInt()
}
