package no.nav.syfo.testutil

fun String.extractPortFromUrl() : Int {
    val portIndexStart = lastIndexOf(':') + 1
    val urlLastPortion = subSequence(portIndexStart, length)
    var portIndexEnd = urlLastPortion.indexOf('/')
    if (portIndexEnd == -1)
        portIndexEnd = portIndexStart + urlLastPortion.length
    else
        portIndexEnd += portIndexStart
    return subSequence(portIndexStart, portIndexEnd).toString().toInt()
}

