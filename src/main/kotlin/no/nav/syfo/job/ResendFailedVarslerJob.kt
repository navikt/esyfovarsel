package no.nav.syfo.job

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.fetchUtsendtBrukernotifikasjonVarselFeilet
import no.nav.syfo.db.updateUtsendtVarselFeiletToResendt
import no.nav.syfo.service.DialogmoteInnkallingSykmeldtVarselService
import no.nav.syfo.service.MerVeiledningVarselService
import no.nav.syfo.service.MotebehovVarselService
import org.slf4j.LoggerFactory

class ResendFailedVarslerJob(
    private val db: DatabaseInterface,
    private val motebehovVarselService: MotebehovVarselService,
    private val dialogmoteInnkallingSykmeldtVarselService: DialogmoteInnkallingSykmeldtVarselService,
    private val merVeiledningVarselService: MerVeiledningVarselService
) {
    private val log = LoggerFactory.getLogger(ResendFailedVarslerJob::class.java)

    suspend fun resendFailedBrukernotifikasjonVarsler(): Int {
        val failedVarsler = db.fetchUtsendtBrukernotifikasjonVarselFeilet()

        log.info(
            "Attempting to resend ${failedVarsler.size} failed brukernotifikasjon varsler"
        )
        var resentVarsler = 0

        failedVarsler.forEach { failedVarsel ->
            when (failedVarsel.hendelsetypeNavn) {
                "SM_DIALOGMOTE_SVAR_MOTEBEHOV" -> {
                    val isResendt = motebehovVarselService.resendVarselTilBrukernotifikasjoner(
                        failedVarsel
                    )
                    if (isResendt) {
                        db.updateUtsendtVarselFeiletToResendt(failedVarsel.uuid)
                        resentVarsler++
                    }
                }

                "SM_DIALOGMOTE_INNKALT" -> {
                    val isResendt =
                        dialogmoteInnkallingSykmeldtVarselService.revarsleArbeidstakerViaBrukernotifikasjoner(
                            failedVarsel
                        )
                    if (isResendt) {
                        db.updateUtsendtVarselFeiletToResendt(failedVarsel.uuid)
                        resentVarsler++
                    }
                }

                "SM_DIALOGMOTE_AVLYST" -> {
                    val isResendt =
                        dialogmoteInnkallingSykmeldtVarselService.revarsleArbeidstakerViaBrukernotifikasjoner(
                            failedVarsel
                        )
                    if (isResendt) {
                        db.updateUtsendtVarselFeiletToResendt(failedVarsel.uuid)
                        resentVarsler++
                    }
                }

                "SM_DIALOGMOTE_NYTT_TID_STED" -> {
                    val isResendt =
                        dialogmoteInnkallingSykmeldtVarselService.revarsleArbeidstakerViaBrukernotifikasjoner(
                            failedVarsel
                        )
                    if (isResendt) {
                        db.updateUtsendtVarselFeiletToResendt(failedVarsel.uuid)
                        resentVarsler++
                    }
                }

                "SM_MER_VEILEDNING" -> {
                    val isResendt = merVeiledningVarselService.resendDigitaltVarselTilArbeidstaker(failedVarsel)
                    if (isResendt) {
                        db.updateUtsendtVarselFeiletToResendt(failedVarsel.uuid)
                        resentVarsler++
                    }
                }

                else -> log.warn("Not sending varsel for hendelsetypeNavn: ${failedVarsel.hendelsetypeNavn}")
            }
        }

        if (resentVarsler > 0) {
            log.info("Successfully resent $resentVarsler brukernotifikasjon varsler")
        } else {
            log.info("No brukernotifikasjon varsler to resend")
        }

        return resentVarsler
    }
}
