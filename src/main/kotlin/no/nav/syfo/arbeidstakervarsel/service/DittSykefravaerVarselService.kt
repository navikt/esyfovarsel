package no.nav.syfo.arbeidstakervarsel.service

import no.nav.syfo.arbeidstakervarsel.dao.ArbeidstakerKanal
import no.nav.syfo.arbeidstakervarsel.domain.ArbeidstakerVarselSendResult
import no.nav.syfo.arbeidstakervarsel.domain.DittSykefravaerVarsel
import no.nav.syfo.arbeidstakervarsel.domain.DittSykefravaerVarselType
import no.nav.syfo.kafka.producers.dittsykefravaer.DittSykefravaerMeldingKafkaProducer
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.OpprettMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.Variant
import org.slf4j.LoggerFactory
import java.util.*

class DittSykefravaerVarselService(
    private val dittSykefravaerMeldingKafkaProducer: DittSykefravaerMeldingKafkaProducer
) {
    private val log = LoggerFactory.getLogger(DittSykefravaerVarselService::class.java)

    fun sendVarsel(mottakerFnr: String, dittSykefravaerVarsel: DittSykefravaerVarsel): ArbeidstakerVarselSendResult {
        val uuid = dittSykefravaerVarsel.id
        val uuidString = uuid.toString()
        return try {
            when (dittSykefravaerVarsel.varselType) {
                DittSykefravaerVarselType.SEND -> sendMelding(mottakerFnr, dittSykefravaerVarsel, uuidString)
                DittSykefravaerVarselType.FERDIGSTILL -> ferdigstillMelding(mottakerFnr, uuid)
            }
            log.info("Successfully sent DittSykefravaer varsel with uuid={}", uuidString)
            ArbeidstakerVarselSendResult(
                success = true,
                uuid = uuidString,
                kanal = ArbeidstakerKanal.DITT_SYKEFRAVAER,
                exception = null
            )
        } catch (e: Exception) {
            log.error("Failed to send DittSykefravaer varsel with uuid={}", uuidString, e)
            ArbeidstakerVarselSendResult(
                success = false,
                uuid = uuidString,
                kanal = ArbeidstakerKanal.DITT_SYKEFRAVAER,
                exception = e
            )
        }
    }

    private fun sendMelding(
        mottakerFnr: String,
        dittSykefravaerVarsel: DittSykefravaerVarsel,
        uuidString: String
    ) {
        val melding = DittSykefravaerMelding(
            opprettMelding = OpprettMelding(
                tekst = dittSykefravaerVarsel.tekst,
                lenke = dittSykefravaerVarsel.lenke,
                variant = Variant.INFO,
                lukkbar = dittSykefravaerVarsel.lukkbar,
                meldingType = dittSykefravaerVarsel.meldingType,
                synligFremTil = dittSykefravaerVarsel.synligFremTil
            ),
            lukkMelding = null,
            fnr = mottakerFnr
        )
        dittSykefravaerMeldingKafkaProducer.sendMelding(
            melding = melding,
            uuid = uuidString
        )
    }

    private fun ferdigstillMelding(mottakerFnr: String, uuid: UUID) {
        val eksternReferanse = "esyfovarsel-$uuid"
        dittSykefravaerMeldingKafkaProducer.ferdigstillMelding(
            eksternReferanse = eksternReferanse,
            fnr = mottakerFnr
        )
    }
}
