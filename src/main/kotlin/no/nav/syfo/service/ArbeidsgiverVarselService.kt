package no.nav.syfo.service

import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidsgiverHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.getArbeidsgiverAltinnRessurs
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ArbeidsgiverVarselService {
    private val log: Logger = LoggerFactory.getLogger(ArbeidsgiverVarselService::class.qualifiedName)

    fun sendVarselTilArbeidsgiver(arbeidsgiverHendelse: ArbeidsgiverHendelse) {
        val altinnRessurs = arbeidsgiverHendelse.getArbeidsgiverAltinnRessurs()

        log.info(
            "Stubbet arbeidsgivervarsel kalt: type={}, orgnummer={}, altinnRessursId={}, altinnRessursUrl={}",
            arbeidsgiverHendelse.type,
            arbeidsgiverHendelse.orgnummer,
            altinnRessurs.id,
            altinnRessurs.url,
        )
    }
}
