package no.nav.syfo.arbeidstakervarsel.dao

import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerVarsel

enum class ArbeidstakerKanal {
    BRUKERNOTIFIKASJON,
    DOKUMENTDISTRIBUSJON,
    DITT_SYKEFRAVAER
}

class ArbeidstakervarselDao {
    fun storeArbeidstakerVarselHendelse(arbeidstakerVarsel: ArbeidstakerVarsel) {
        //TODO: Lagre hendelsen som kom inn fra kafka
    }

    fun storeUtsendtArbeidstakerVarsel(uuid: String, kanal: ArbeidstakerKanal) {
        // TODO: Lagre referanse til varsel/hendelse som gikk bra
    }

    fun storeUtsendtArbeidstakerVarselFeilet(uuid: String, kanal: ArbeidstakerKanal, error: String) {
        // TODO: Lagre referanse til varsel/hendelse som feilet
    }
}