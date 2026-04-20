package no.nav.syfo.service

import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidsgiverHendelse
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DialogmoteInnkallingArbeidsgiverVarselService {
    private val log: Logger = LoggerFactory.getLogger(DialogmoteInnkallingArbeidsgiverVarselService::class.qualifiedName)

    fun sendVarselTilArbeidsgiver(arbeidsgiverHendelse: ArbeidsgiverHendelse) {
        val altinnRessurs =
            try {
                arbeidsgiverHendelse.dataToVarselDataAltinnRessurs()
            } catch (e: Exception) {
                log.error("Feil ved konvertering av ArbeidsgiverHendelse til VarselDataAltinnRessurs", e)
                null
            }

        log.info(
            "Stubbet arbeidsgivervarsel kalt: type={}, orgnummer={}, altinnRessursId={}, altinnRessursUrl={}",
            arbeidsgiverHendelse.type,
            arbeidsgiverHendelse.orgnummer,
            altinnRessurs?.id ?: "null",
            altinnRessurs?.url ?: "null",
        )
    }
}
