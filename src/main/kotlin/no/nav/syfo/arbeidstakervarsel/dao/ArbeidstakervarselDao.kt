package no.nav.syfo.arbeidstakervarsel.dao

import no.nav.syfo.arbeidstakervarsel.domain.ArbeidstakerVarsel
import no.nav.syfo.arbeidstakervarsel.domain.ArbeidstakerVarselSendResult

enum class ArbeidstakerKanal {
    BRUKERNOTIFIKASJON,
    DOKUMENTDISTRIBUSJON,
    DITT_SYKEFRAVAER
}

class ArbeidstakervarselDao {
    fun storeArbeidstakerVarselHendelse(arbeidstakerVarsel: ArbeidstakerVarsel) {
        // TODO: Lagre hendelsen som kom inn fra kafka
    }

    private fun storeUtsendtArbeidstakerVarsel(uuid: String, kanal: ArbeidstakerKanal) {
        // TODO: Lagre referanse til varsel/hendelse som gikk bra
    }

    private fun storeUtsendtArbeidstakerVarselFeilet(uuid: String, kanal: ArbeidstakerKanal, error: String) {
        // TODO: Lagre referanse til varsel/hendelse som feilet
    }

    fun storeSendResult(sendResult: ArbeidstakerVarselSendResult) {
        if (sendResult.success) {
            storeUtsendtArbeidstakerVarsel(
                uuid = sendResult.uuid,
                kanal = sendResult.kanal
            )
        } else {
            storeUtsendtArbeidstakerVarselFeilet(
                uuid = sendResult.uuid,
                kanal = sendResult.kanal,
                error = sendResult.exception.toString()

            )
        }
    }
}
