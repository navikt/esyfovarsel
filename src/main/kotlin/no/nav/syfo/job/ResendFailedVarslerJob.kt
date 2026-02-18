package no.nav.syfo.job

import java.util.UUID
import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PUtsendtVarselFeilet
import no.nav.syfo.db.domain.toArbeidstakerHendelse
import no.nav.syfo.db.fetchDineSykemeldteMotebehovOppgaverFor
import no.nav.syfo.db.fetchUtsendtArbeidsgivernotifikasjonVarselFeilet
import no.nav.syfo.db.fetchUtsendtBrukernotifikasjonVarselFeilet
import no.nav.syfo.db.fetchUtsendtDokDistVarselFeilet
import no.nav.syfo.db.updateUtsendtVarselFeiletToResendt
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.service.DialogmoteInnkallingSykmeldtVarselService
import no.nav.syfo.service.KartleggingssporsmalVarselService
import no.nav.syfo.service.MerVeiledningVarselService
import no.nav.syfo.service.MotebehovVarselService
import no.nav.syfo.service.SenderFacade
import org.slf4j.LoggerFactory

class ResendFailedVarslerJob(
    private val db: DatabaseInterface,
    private val motebehovVarselService: MotebehovVarselService,
    private val dialogmoteInnkallingSykmeldtVarselService: DialogmoteInnkallingSykmeldtVarselService,
    private val merVeiledningVarselService: MerVeiledningVarselService,
    private val kartleggingVarselService: KartleggingssporsmalVarselService,
    private val senderFacade: SenderFacade,
) {
    private val log = LoggerFactory.getLogger(ResendFailedVarslerJob::class.java)

    suspend fun resendFailedBrukernotifikasjonVarsler(): Int {
        val failedVarsler = db.fetchUtsendtBrukernotifikasjonVarselFeilet()

        log.info(
            "Attempting to resend ${failedVarsler.size} failed brukernotifikasjon varsler",
        )
        var resentCount = 0

        failedVarsler.forEach { failedVarsel ->
            when (failedVarsel.hendelsetypeNavn) {
                "SM_DIALOGMOTE_SVAR_MOTEBEHOV" -> {
                    val isResendt =
                        motebehovVarselService.resendVarselTilBrukernotifikasjoner(
                            failedVarsel,
                        )
                    if (isResendt) {
                        db.updateUtsendtVarselFeiletToResendt(failedVarsel.uuid)
                        resentCount++
                    }
                }

                "SM_DIALOGMOTE_INNKALT" -> {
                    val isResendt =
                        dialogmoteInnkallingSykmeldtVarselService.revarsleArbeidstakerViaBrukernotifikasjoner(
                            failedVarsel,
                        )
                    if (isResendt) {
                        db.updateUtsendtVarselFeiletToResendt(failedVarsel.uuid)
                        resentCount++
                    }
                }

                "SM_DIALOGMOTE_AVLYST" -> {
                    val isResendt =
                        dialogmoteInnkallingSykmeldtVarselService.revarsleArbeidstakerViaBrukernotifikasjoner(
                            failedVarsel,
                        )
                    if (isResendt) {
                        db.updateUtsendtVarselFeiletToResendt(failedVarsel.uuid)
                        resentCount++
                    }
                }

                "SM_DIALOGMOTE_NYTT_TID_STED" -> {
                    val isResendt =
                        dialogmoteInnkallingSykmeldtVarselService.revarsleArbeidstakerViaBrukernotifikasjoner(
                            failedVarsel,
                        )
                    if (isResendt) {
                        db.updateUtsendtVarselFeiletToResendt(failedVarsel.uuid)
                        resentCount++
                    }
                }

                "SM_MER_VEILEDNING" -> {
                    val isResendt = merVeiledningVarselService.resendDigitaltVarselTilArbeidstaker(failedVarsel)
                    if (isResendt) {
                        db.updateUtsendtVarselFeiletToResendt(failedVarsel.uuid)
                        resentCount++
                    }
                }

                "SM_KARTLEGGINGSSPORSMAL" -> {
                    val isResendt = kartleggingVarselService.resendDigitaltVarselTilArbeidstaker(failedVarsel)
                    if (isResendt) {
                        db.updateUtsendtVarselFeiletToResendt(failedVarsel.uuid)
                        resentCount++
                    }
                }

                else -> log.warn("Not sending varsel for hendelsetypeNavn: ${failedVarsel.hendelsetypeNavn}")
            }
        }

        if (resentCount > 0) {
            log.info(
                "Successfully resent $resentCount " +
                    "brukernotifikasjon varsler of ${failedVarsler.size} selected varsler",
            )
        } else {
            log.info("No brukernotifikasjon varsler to resend")
        }

        return resentCount
    }

    suspend fun resendFailedArbeidsgivernotifikasjonVarsler(): Int {
        val failedVarsler = db.fetchUtsendtArbeidsgivernotifikasjonVarselFeilet()
        log.info(
            "Attempting to resend ${failedVarsler.size} failed arbeidsgivernotifikasjon varsler",
        )
        val resentCount: Int =
            failedVarsler
                .map { failedVarsel ->
                    when (failedVarsel.hendelsetypeNavn) {
                        "NL_DIALOGMOTE_SVAR_MOTEBEHOV" -> tryResendArbeidsgivernotifikasjonMoteBehov(failedVarsel)
                        else -> {
                            log.warn("Not resending varsel for hendelsetypeNavn: ${failedVarsel.hendelsetypeNavn}")
                            return@map 0
                        }
                    }
                }.sum()

        log.info("Resent $resentCount arbeidsgivernotifikasjon varsler of ${failedVarsler.size} failed varsler")
        return resentCount
    }

    suspend fun resendFailedDokDistVarsler(): Int {
        val failedVarsler = db.fetchUtsendtDokDistVarselFeilet()
        var resentCount = 0

        log.info(
            "Attempting to resend ${failedVarsler.size} failed dokdist varsler",
        )

        failedVarsler.forEach { failedVarsel ->
            if (failedVarsel.journalpostId == null) {
                log.error(
                    "Not resending dokdist varsel: " +
                        "JournalpostId is null for failedVarsel with uuid ${failedVarsel.uuid}",
                )
                return@forEach
            }

            if (failedVarsel.uuidEksternReferanse == null) {
                log.error(
                    "Not resending dokdist varsel: " +
                        "uuidEksternReferanse is null for failedVarsel with uuid ${failedVarsel.uuid}",
                )
                return@forEach
            }

            val varselHendelse = failedVarsel.toArbeidstakerHendelse()
            val isResendt =
                senderFacade.sendBrevTilFysiskPrint(
                    uuid = failedVarsel.uuidEksternReferanse,
                    varselHendelse = varselHendelse,
                    journalpostId = failedVarsel.journalpostId,
                    distribusjonsType = HendelseType.valueOf(failedVarsel.hendelsetypeNavn).toDistribusjonsType(),
                    failedUtsendingUUID = UUID.fromString(failedVarsel.uuid),
                )
            if (isResendt) {
                db.updateUtsendtVarselFeiletToResendt(failedVarsel.uuid)
                resentCount++
            }
        }

        if (resentCount > 0) {
            log.info(
                "Successfully resent $resentCount " +
                    "dokdist varsler of ${failedVarsler.size} selected varsler",
            )
        } else {
            log.info("No dokdist varsler to resend")
        }

        return resentCount
    }

    private suspend fun tryResendArbeidsgivernotifikasjonMoteBehov(failedVarsel: PUtsendtVarselFeilet): Int {
        if (failedVarsel.narmesteLederFnr == null || failedVarsel.orgnummer == null) {
            log.error(
                "Skip resending arbeidsgivernotifikasjon varsel:" +
                    " narmesteLederFnr or orgnummer is null for failedVarsel with uuid ${failedVarsel.uuid}",
            )
            return 0
        }
        val dineSykemeldteUtsendtVarsel =
            db.fetchDineSykemeldteMotebehovOppgaverFor(
                PersonIdent(failedVarsel.arbeidstakerFnr),
                PersonIdent(failedVarsel.narmesteLederFnr),
                failedVarsel.orgnummer,
            )
        if (dineSykemeldteUtsendtVarsel == null) {
            log.error(
                "Skip resending arbeidsgivernotifikasjon varsel:" +
                    " DineSykemeldteUtsendtVarsel is null for " +
                    "failedVarsel with uuid ${failedVarsel.uuid}",
            )
            return 0
        }
        if (dineSykemeldteUtsendtVarsel.ferdigstiltTidspunkt != null) {
            // mark as resendt, since we don't want to notify arbeidsgiver if it is already ferdigstilt
            db.updateUtsendtVarselFeiletToResendt(failedVarsel.uuid)
            log.info(
                "Skip resending arbeidsgivernotifikasjon varsel:" +
                    " DineSykemeldteUtsendtVarsel is already ferdigstilt" +
                    " for failedVarsel with uuid ${failedVarsel.uuid}",
            )
            return 0
        }
        val isResendt =
            motebehovVarselService.resendVarselTilArbeidsgiverNotifikasjon(
                failedVarsel,
            )
        if (isResendt) {
            db.updateUtsendtVarselFeiletToResendt(failedVarsel.uuid)
            return 1
        }
        return 0
    }
}

private fun HendelseType.toDistribusjonsType() =
    when (this) {
        HendelseType.SM_MER_VEILEDNING -> DistibusjonsType.VIKTIG
        HendelseType.SM_VEDTAK_FRISKMELDING_TIL_ARBEIDSFORMIDLING -> DistibusjonsType.VIKTIG
        HendelseType.SM_AKTIVITETSPLIKT -> DistibusjonsType.VIKTIG
        HendelseType.SM_FORHANDSVARSEL_MANGLENDE_MEDVIRKNING -> DistibusjonsType.VIKTIG
        HendelseType.SM_ARBEIDSUFORHET_FORHANDSVARSEL -> DistibusjonsType.VIKTIG
        else -> DistibusjonsType.ANNET
    }
