package no.nav.syfo.testutil

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.fetchMikrofrontendSynlighetEntriesByFnr
import no.nav.syfo.kafka.producers.mineside_microfrontend.Tjeneste
import org.amshove.kluent.should

fun String.extractPortFromUrl(): Int {
    val portIndexStart = lastIndexOf(':') + 1
    val urlLastPortion = subSequence(portIndexStart, length)
    var portIndexEnd = urlLastPortion.indexOf('/')
    if (portIndexEnd == -1) {
        portIndexEnd = portIndexStart + urlLastPortion.length
    } else {
        portIndexEnd += portIndexStart
    }
    return subSequence(portIndexStart, portIndexEnd).toString().toInt()
}

fun DatabaseInterface.shouldContainMikrofrontendEntry(fnr: String, tjeneste: Tjeneste) =
    this.should("Should contain at least one row with specified fnr and 'tjeneste'") {
        this.fetchMikrofrontendSynlighetEntriesByFnr(fnr).any {
            it.synligFor == fnr && it.tjeneste == tjeneste.name
        }
    }

fun DatabaseInterface.shouldContainMikrofrontendEntryWithoutSynligTom(fnr: String, tjeneste: Tjeneste) =
    this.should("Should contain at least one row with specified fnr and 'tjeneste' without synligTom") {
        this.fetchMikrofrontendSynlighetEntriesByFnr(fnr).any {
            it.synligFor == fnr && it.tjeneste == tjeneste.name && it.synligTom == null
        }
    }

fun DatabaseInterface.shouldContainMikrofrontendEntryWithSynligTom(fnr: String, tjeneste: Tjeneste) =
    this.should("Should contain at least one row with specified fnr and 'tjeneste' with synligTom") {
        this.fetchMikrofrontendSynlighetEntriesByFnr(fnr).any {
            it.synligFor == fnr && it.tjeneste == tjeneste.name && it.synligTom != null
        }
    }

fun DatabaseInterface.shouldNotContainMikrofrontendEntryForUser(fnr: String) =
    this.should("Should have no rows with specified fnr") {
        this.fetchMikrofrontendSynlighetEntriesByFnr(fnr).none {
            it.synligFor == fnr
        }
    }
