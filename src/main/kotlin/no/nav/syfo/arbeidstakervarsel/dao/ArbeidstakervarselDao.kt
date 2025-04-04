package no.nav.syfo.arbeidstakervarsel.dao

import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerVarsel

enum class Kanal {
    BRUKERNOTIFIKASJON,
    DOKUMENTDISTRIBUSJON,
    DITT_SYKEFRAVAER
}

class ArbeidstakervarselDao {
    fun storeArbeidstakerVarselHendelse(arbeidstakerVarsel: ArbeidstakerVarsel) {
        //TODO: Lagre hendelsen som kom inn fra kafka
    }

    fun storeUtsendtArbeidstakerVarsel(uuid: String, kanal: Kanal) {
        // TODO: Lagre referanse til varsel/hendelse som gikk bra
    }

    fun storeUtsendtArbeidstakerVarselFeilet(uuid: String, kanal: Kanal, error: String) {
        // TODO: Lagre referanse til varsel/hendelse som feilet
    }
}