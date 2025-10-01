package no.nav.syfo.arbeidstakervarsel.service

import no.nav.syfo.arbeidstakervarsel.dao.ArbeidstakerKanal
import no.nav.syfo.arbeidstakervarsel.dao.ArbeidstakervarselDao
import no.nav.syfo.kafka.consumers.arbeidstakervarsel.domain.ArbeidstakerVarsel

class ArbeidstakervarselService(
    private val brukernotifikasjonService: BrukernotifikasjonService,
    private val arbeidstakervarselDao: ArbeidstakervarselDao,

) {
    fun processVarsel(arbeidstakerVarsel: ArbeidstakerVarsel) {
        arbeidstakervarselDao.storeArbeidstakerVarselHendelse(arbeidstakerVarsel)

        if (arbeidstakerVarsel.brukernotifikasjonVarsel != null) {
            brukernotifikasjonService.sendBrukernotifikasjon(
                mottakerFnr = arbeidstakerVarsel.mottakerFnr,
                brukernotifikasjonVarsel = arbeidstakerVarsel.brukernotifikasjonVarsel
            ).also {
                storeSendResult(it)
            }
        }
    }

    private fun storeSendResult(sendResult: SendResult) {
        if (sendResult.success) {
            arbeidstakervarselDao.storeUtsendtArbeidstakerVarsel(
                uuid = sendResult.uuid,
                kanal = sendResult.kanal
            )
        } else {
            arbeidstakervarselDao.storeUtsendtArbeidstakerVarselFeilet(
                uuid = sendResult.uuid,
                kanal = sendResult.kanal,
                error = sendResult.exception.toString()

            )
        }
    }

    data class SendResult(
        val success: Boolean,
        val uuid: String,
        val kanal: ArbeidstakerKanal,
        val exception: Exception? = null
    )
}
