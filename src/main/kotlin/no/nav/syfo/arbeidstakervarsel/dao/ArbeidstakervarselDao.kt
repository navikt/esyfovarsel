package no.nav.syfo.arbeidstakervarsel.dao

import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerVarsel

enum class Kanal {
    BRUKERNOTIFIKASJON,
    BREV,
    DITT_SYKEFRAVAER
}

class ArbeidstakervarselDao {
    fun storeArbeidstakerVarselHendelse(arbeidstakerVarsel: ArbeidstakerVarsel) {
        // Implement the logic to store the arbeidstakerVarsel in the database
        // This could involve using a database connection, preparing a statement, and executing it
    }

    fun storeUtsendtArbeidstakerVarsel(uuid: String, kanal: Kanal) {
        // Implement the logic to store the utsendt arbeidstaker varsel in the database
        // This could involve using a database connection, preparing a statement, and executing it
    }

    fun storeUtsendArbeidstakerVarselFeilet(uuid: String, kanal: Kanal, error: String) {
        // Implement the logic to store the utsendt arbeidstaker varsel feilet in the database
        // This could involve using a database connection, preparing a statement, and executing it
    }
}