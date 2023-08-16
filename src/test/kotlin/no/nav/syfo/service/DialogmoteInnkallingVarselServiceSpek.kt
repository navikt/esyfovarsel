package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.coEvery
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.access.domain.UserAccessStatus
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dittsykefravaer.DittSykefravaerMeldingKafkaProducer
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.mocks.fnr1
import no.nav.syfo.testutil.mocks.fnr2
import no.nav.syfo.testutil.mocks.fnr3
import no.nav.syfo.testutil.mocks.orgnummer

class DialogmoteInnkallingVarselServiceSpek : DescribeSpec({
    val accessControlService = mockk<AccessControlService>()
    val dineSykmeldteHendelseKafkaProducer = mockk<DineSykmeldteHendelseKafkaProducer>()
    val dittSykefravaerMeldingKafkaProducer = mockk<DittSykefravaerMeldingKafkaProducer>()
    val brukernotifikasjonerService = mockk<BrukernotifikasjonerService>()
    val arbeidsgiverNotifikasjonService = mockk<ArbeidsgiverNotifikasjonService>()
    val fysiskBrevUtsendingService = mockk<FysiskBrevUtsendingService>()
    val embeddedDatabase by lazy { EmbeddedDatabase() }
    val fakeDialogmoterUrl = "http://localhost/dialogmoter"
    val journalpostUuid = "97b886fe-6beb-40df-af2b-04e504bc340c"
    val journalpostId = "1"
    val journalpostUuidAddressProtection = "c00c88dd-ab2b-404a-807c-3a2ae2581ced"
    val journalpostIdAddressProtection = "2"

    val senderFacade = SenderFacade(
        dineSykmeldteHendelseKafkaProducer,
        dittSykefravaerMeldingKafkaProducer,
        brukernotifikasjonerService,
        arbeidsgiverNotifikasjonService,
        fysiskBrevUtsendingService,
        embeddedDatabase
    )
    val dialogmoteInnkallingVarselService = DialogmoteInnkallingVarselService(
        senderFacade,
        fakeDialogmoterUrl,
        accessControlService
    )
    val hendelseType = HendelseType.SM_DIALOGMOTE_INNKALT

    describe("DialogmoteInnkallingVarselServiceSpek") {
        justRun { brukernotifikasjonerService.sendVarsel(any(), any(), any(), any(), any(), any()) }
        justRun { fysiskBrevUtsendingService.sendBrev(any(), any()) }

        it("Non-reserved users should be notified externally") {
            coEvery { accessControlService.getUserAccessStatus(fnr1) } returns
                    UserAccessStatus(fnr1, true, true)

            val varselHendelse = ArbeidstakerHendelse(
                hendelseType,
                false,
                varselData(journalpostUuid, journalpostId),
                fnr1,
                orgnummer
            )

            dialogmoteInnkallingVarselService.sendVarselTilArbeidstaker(varselHendelse)

            verify(exactly = 1) {
                brukernotifikasjonerService.sendVarsel(
                    any(),
                    fnr1,
                    any(),
                    dialogmoteInnkallingVarselService.getVarselUrl(varselHendelse, journalpostUuid),
                    dialogmoteInnkallingVarselService.getMeldingTypeForSykmeldtVarsling(hendelseType),
                    true
                )
            }
        }

        it("Reserved users should be notified physically") {
            coEvery { accessControlService.getUserAccessStatus(fnr2) } returns
                    UserAccessStatus(fnr2, false, true)
            val varselHendelse = ArbeidstakerHendelse(
                hendelseType,
                false,
                varselData(journalpostUuid, journalpostId),
                fnr2,
                orgnummer
            )
            dialogmoteInnkallingVarselService.sendVarselTilArbeidstaker(varselHendelse)
            verify(exactly = 1) {
                fysiskBrevUtsendingService.sendBrev(
                    journalpostUuid,
                    journalpostId
                )
            }
            verify(exactly = 0) {
                brukernotifikasjonerService.sendVarsel(
                    any(),
                    fnr2,
                    any(),
                    any(),
                    any(),
                    true
                )
            }
        }

        it("Reserved users with address-protection should only be on 'Min-side'") {
            coEvery { accessControlService.getUserAccessStatus(fnr3) } returns
                    UserAccessStatus(fnr3, false, false)
            val varselHendelse = ArbeidstakerHendelse(
                hendelseType,
                false,
                varselData(journalpostUuidAddressProtection, journalpostIdAddressProtection),
                fnr3,
                orgnummer
            )
            dialogmoteInnkallingVarselService.sendVarselTilArbeidstaker(varselHendelse)
            verify(exactly = 1) {
                brukernotifikasjonerService.sendVarsel(
                    any(),
                    fnr3,
                    any(),
                    dialogmoteInnkallingVarselService.getVarselUrl(varselHendelse, journalpostUuid),
                    dialogmoteInnkallingVarselService.getMeldingTypeForSykmeldtVarsling(hendelseType),
                    false
                )
            }
            verify(exactly = 0) {
                fysiskBrevUtsendingService.sendBrev(
                    journalpostUuidAddressProtection,
                    journalpostIdAddressProtection
                )
            }
        }
    }
})

fun varselData(journalpostUuid: String, journalpostId: String) = """{
        "journalpost": {
            "uuid": "$journalpostUuid",
            "id": "$journalpostId"
        },
        "narmesteLeder": null,
        "motetidspunkt": null
    }""".trimIndent()
