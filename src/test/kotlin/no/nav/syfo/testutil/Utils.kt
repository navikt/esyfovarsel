package no.nav.syfo.testutil

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchMikrofrontendSynlighetEntriesByFnr
import no.nav.syfo.kafka.producers.minsideMikrofrontend.Tjeneste
import org.amshove.kluent.should
import java.time.LocalDate

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
fun List<PPlanlagtVarsel>.skalHaEt39UkersVarsel() = this.should("Skal ha 39-ukersvarsel") {
    size == 1 && filter { it.type == VarselType.MER_VEILEDNING.name }.size == 1
}

fun List<PPlanlagtVarsel>.skalIkkeHa39UkersVarsel() = this.should("Skal IKKE ha 39-ukersvarsel") {
    size == 0
}

fun List<PPlanlagtVarsel>.skalHaUtsendingPaDato(utsendingsdato: LocalDate) =
    this.should("Skal ha 39-ukersvarsel med utsendingsdato: $utsendingsdato") {
        filter { it.utsendingsdato == utsendingsdato }.size == 1
    }

fun DatabaseInterface.shouldContainMikrofrontendEntry(fnr: String, tjeneste: Tjeneste) =
    this.should("Should contain at least one row with specified fnr and 'tjeneste'") {
        this.fetchMikrofrontendSynlighetEntriesByFnr(fnr).any {
            it.synligFor == fnr && it.tjeneste == tjeneste.name
        }
    }

fun DatabaseInterface.shouldContainMikrofrontendEntryWithoutMotetidspunkt(fnr: String, tjeneste: Tjeneste) =
    this.should("Should contain at least one row with specified fnr and 'tjeneste' without synligTom") {
        this.fetchMikrofrontendSynlighetEntriesByFnr(fnr).any {
            it.synligFor == fnr && it.tjeneste == tjeneste.name && it.synligTom == null
        }
    }

fun DatabaseInterface.shouldContainMikrofrontendEntryWithMotetidspunkt(fnr: String, tjeneste: Tjeneste) =
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
