package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.access.domain.UserAccessStatus
import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederRelasjon
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.consumer.narmesteLeder.Tilgang
import no.nav.syfo.consumer.pdl.Doedsdato
import no.nav.syfo.consumer.pdl.Foedselsdato
import no.nav.syfo.consumer.pdl.HentPerson
import no.nav.syfo.consumer.pdl.HentPersonData
import no.nav.syfo.consumer.pdl.Navn
import no.nav.syfo.consumer.pdl.PdlClient
import no.nav.syfo.db.Database
import no.nav.syfo.db.domain.Kanal
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dittsykefravaer.DittSykefravaerMeldingKafkaProducer
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.OpprettMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.Variant
import no.nav.syfo.planner.arbeidstakerFnr1
import no.nav.syfo.service.SenderFacade.InternalBrukernotifikasjonType.DONE
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.mocks.fnr1
import no.nav.syfo.testutil.mocks.fnr2
import no.nav.syfo.testutil.mocks.fnr3
import no.nav.syfo.testutil.mocks.orgnummer
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo

class DialogmoteInnkallingSykmeldtVarselServiceSpek : DescribeSpec({
    val accessControlService = mockk<AccessControlService>()
    val dineSykmeldteHendelseKafkaProducer = mockk<DineSykmeldteHendelseKafkaProducer>()
    val dittSykefravaerMeldingKafkaProducer = mockk<DittSykefravaerMeldingKafkaProducer>(relaxed = true)
    val brukernotifikasjonerService = mockk<BrukernotifikasjonerService>(relaxed = true)
    val arbeidsgiverNotifikasjonService = mockk<ArbeidsgiverNotifikasjonService>(relaxed = true)
    val fysiskBrevUtsendingService = mockk<FysiskBrevUtsendingService>()
    val narmesteLederService = mockk<NarmesteLederService>()
    val database = mockk<Database>(relaxed = true)
    val pdlClient = mockk<PdlClient>()
    val embeddedDatabase = EmbeddedDatabase()
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
        embeddedDatabase,
        pdlClient,
    )
    val dialogmoteInnkallingSykmeldtVarselService = DialogmoteInnkallingSykmeldtVarselService(
        senderFacade,
        fakeDialogmoterUrl,
        accessControlService,
        database,
    )
    val hendelseType = HendelseType.SM_DIALOGMOTE_INNKALT

    describe("DialogmoteInnkallingVarselServiceSpek") {
        coJustRun { fysiskBrevUtsendingService.sendBrev(any(), any(), DistibusjonsType.ANNET) }

        beforeTest {
            clearAllMocks()
            embeddedDatabase.dropData()

            coEvery { narmesteLederService.getNarmesteLederRelasjon(any(), any()) } returns NarmesteLederRelasjon(
                narmesteLederId = "1234",
                tilganger = listOf(Tilgang.SYKMELDING),
                navn = "Hest hestesen",
            )
            coEvery { pdlClient.hentPerson(any()) } returns HentPersonData(
                hentPerson = HentPerson(
                    foedselsdato = listOf(Foedselsdato(foedselsdato = "1990-01-01")),
                    navn = listOf(Navn(fornavn = "Test", mellomnavn = null, etternavn = "Testesen")),
                    doedsfall = listOf()
                )
            )
            coEvery { arbeidsgiverNotifikasjonService.createNewSak(any()) } returns "123"
        }

        it("Non-reserved users should be notified externally") {
             coEvery { accessControlService.getUserAccessStatus(fnr1) } returns
                     UserAccessStatus(fnr1, true)
            coEvery { pdlClient.isPersonAlive(any()) } returns true

             val varselHendelse = ArbeidstakerHendelse(
                 hendelseType,
                 false,
                 varselData(journalpostUuid, journalpostId),
                 fnr1,
                 orgnummer,
             )
             dialogmoteInnkallingSykmeldtVarselService.sendVarselTilArbeidstaker(varselHendelse)

             coVerify (exactly = 1) {
                 brukernotifikasjonerService.sendBrukernotifikasjonVarsel(
                     uuid = any(),
                     mottakerFnr = fnr1,
                     content = any(),
                     url = dialogmoteInnkallingSykmeldtVarselService.getVarselUrl(varselHendelse, journalpostUuid),
                     smsContent = null,
                     varseltype = any(),
                     eksternVarsling = any(),
                     isPersonAlive = true,
                 )
             }

             coVerify(atLeast = 1) {
                 dittSykefravaerMeldingKafkaProducer.sendMelding(
                     any(),
                     any(),
                 )
             }
        }

        it("Reserved users should be notified physically") {
            coEvery { accessControlService.getUserAccessStatus(fnr2) } returns
                UserAccessStatus(fnr2, canUserBeDigitallyNotified = false)
            coEvery { pdlClient.isPersonAlive(fnr2) } returns true

            val varselHendelse = ArbeidstakerHendelse(
                hendelseType,
                false,
                varselData(journalpostUuid, journalpostId),
                fnr2,
                orgnummer,
            )
            dialogmoteInnkallingSykmeldtVarselService.sendVarselTilArbeidstaker(varselHendelse)

            coVerify(exactly = 1) {
                fysiskBrevUtsendingService.sendBrev(
                    journalpostUuid,
                    journalpostId,
                    DistibusjonsType.ANNET,
                )
            }

            coVerify(exactly = 0) {
                brukernotifikasjonerService.sendBrukernotifikasjonVarsel(
                    uuid = any(),
                    mottakerFnr = fnr2,
                    content = any(),
                    url = any(),
                    varseltype = any(),
                    eksternVarsling = any(),
                    smsContent = any(),
                    isPersonAlive = true,
                )
            }

            verify(atLeast = 1) {
                dittSykefravaerMeldingKafkaProducer.sendMelding(
                    any(),
                    any(),
                )
            }
        }

        it("Reserved users should get brevpost") {
            coEvery { accessControlService.getUserAccessStatus(fnr3) } returns
                UserAccessStatus(fnr3, canUserBeDigitallyNotified = false)
            coEvery { pdlClient.isPersonAlive(fnr3) } returns true

            val varselHendelse = ArbeidstakerHendelse(
                hendelseType,
                false,
                varselData(journalpostUuidAddressProtection, journalpostIdAddressProtection),
                fnr3,
                orgnummer,
            )
            dialogmoteInnkallingSykmeldtVarselService.sendVarselTilArbeidstaker(varselHendelse)
            coVerify(exactly = 0) {
                brukernotifikasjonerService.sendBrukernotifikasjonVarsel(
                    uuid = any(),
                    mottakerFnr = fnr3,
                    content = any(),
                    url = dialogmoteInnkallingSykmeldtVarselService.getVarselUrl(varselHendelse, journalpostUuid),
                    varseltype = any(),
                    eksternVarsling = any(),
                    smsContent = any(),
                    isPersonAlive = true,
                )
            }
            coVerify(exactly = 1) {
                fysiskBrevUtsendingService.sendBrev(
                    journalpostUuidAddressProtection,
                    journalpostIdAddressProtection,
                    DistibusjonsType.ANNET,
                )
            }
            verify(atLeast = 1) {
                dittSykefravaerMeldingKafkaProducer.sendMelding(
                    any(),
                    any(),
                )
            }
        }

        it("Users should not be notified when lest hendelse is sent") {
            coEvery { accessControlService.getUserAccessStatus(arbeidstakerFnr1) } returns
                UserAccessStatus(arbeidstakerFnr1, true)
            coEvery { pdlClient.isPersonAlive(arbeidstakerFnr1) } returns true

            val varselHendelse = ArbeidstakerHendelse(
                type = HendelseType.SM_DIALOGMOTE_LEST,
                false,
                varselData(journalpostUuid, journalpostId),
                arbeidstakerFnr1,
                orgnummer,
            )
            dialogmoteInnkallingSykmeldtVarselService.sendVarselTilArbeidstaker(varselHendelse)

            coVerify(exactly = 1) {
                brukernotifikasjonerService.sendBrukernotifikasjonVarsel(
                    uuid = any(),
                    mottakerFnr = arbeidstakerFnr1,
                    content = any(),
                    url = any(),
                    varseltype = DONE,
                    eksternVarsling = true,
                    isPersonAlive = true,
                )
            }

            verify(exactly = 0) { dittSykefravaerMeldingKafkaProducer.ferdigstillMelding(any(), any()) }

            verify(exactly = 0) {
                dittSykefravaerMeldingKafkaProducer.sendMelding(
                    getDittSykefravaerMelding(),
                    any(),
                )
            }
        }

        it("Non-reserved users should not be notified externally if they passed away") {
            coEvery { accessControlService.getUserAccessStatus(fnr1) } returns
                    UserAccessStatus(fnr1, true)

            coEvery { pdlClient.hentPerson(any()) } returns HentPersonData(
                hentPerson = HentPerson(
                    foedselsdato = listOf(Foedselsdato(foedselsdato = "1990-01-01")),
                    navn = listOf(Navn(fornavn = "Test", mellomnavn = null, etternavn = "Testesen")),
                    doedsfall = listOf(Doedsdato(doedsdato = "01-01-2025"))
                )
            )
            coEvery { pdlClient.isPersonAlive(any()) } returns false

            val varselHendelse = ArbeidstakerHendelse(
                hendelseType,
                false,
                varselData(journalpostUuid, journalpostId),
                fnr1,
                orgnummer,
            )
            dialogmoteInnkallingSykmeldtVarselService.sendVarselTilArbeidstaker(varselHendelse)

            coVerify (exactly = 1) {
                brukernotifikasjonerService.sendBrukernotifikasjonVarsel(
                    uuid = any(),
                    mottakerFnr = fnr1,
                    content = any(),
                    url = dialogmoteInnkallingSykmeldtVarselService.getVarselUrl(varselHendelse, journalpostUuid),
                    smsContent = null,
                    varseltype = any(),
                    eksternVarsling = false,
                    isPersonAlive = false,
                )
            }

            coVerify(atLeast = 1) {
                dittSykefravaerMeldingKafkaProducer.sendMelding(
                    any(),
                    any(),
                )
            }
        }

        it("Reserved users should NOT get brevpost if they passed away") {
            coEvery { accessControlService.getUserAccessStatus(fnr3) } returns
                    UserAccessStatus(fnr3, canUserBeDigitallyNotified = false)

            coEvery { pdlClient.hentPerson(any()) } returns HentPersonData(
                hentPerson = HentPerson(
                    foedselsdato = listOf(Foedselsdato(foedselsdato = "1990-01-01")),
                    navn = listOf(Navn(fornavn = "Test", mellomnavn = null, etternavn = "Testesen")),
                    doedsfall = listOf(Doedsdato(doedsdato = "01-01-2025"))
                )
            )
            coEvery { pdlClient.isPersonAlive(any()) } returns false

            val varselHendelse = ArbeidstakerHendelse(
                hendelseType,
                false,
                varselData(journalpostUuidAddressProtection, journalpostIdAddressProtection),
                fnr3,
                orgnummer,
            )
            dialogmoteInnkallingSykmeldtVarselService.sendVarselTilArbeidstaker(varselHendelse)
            coVerify(exactly = 0) {
                brukernotifikasjonerService.sendBrukernotifikasjonVarsel(
                    uuid = any(),
                    mottakerFnr = fnr3,
                    content = any(),
                    url = dialogmoteInnkallingSykmeldtVarselService.getVarselUrl(varselHendelse, journalpostUuid),
                    varseltype = any(),
                    eksternVarsling = any(),
                    smsContent = any(),
                    isPersonAlive = false,
                )
            }
            coVerify(exactly = 0) {
                fysiskBrevUtsendingService.sendBrev(
                    journalpostUuidAddressProtection,
                    journalpostIdAddressProtection,
                    DistibusjonsType.ANNET,
                )
            }
            verify(atLeast = 1) {
                dittSykefravaerMeldingKafkaProducer.sendMelding(
                    any(),
                    any(),
                )
            }
        }

        it("Endring hendelse skal ferdigstille tidligere innkalling") {
            coEvery { accessControlService.getUserAccessStatus("66666666666") } returns
                UserAccessStatus("66666666666", true)
            coEvery { dittSykefravaerMeldingKafkaProducer.sendMelding(any(), any()) } returns "123"

            val varselHendelseInnkalling = ArbeidstakerHendelse(
                hendelseType,
                false,
                varselData(journalpostUuid, journalpostId),
                "66666666666",
                orgnummer,
            )

            dialogmoteInnkallingSykmeldtVarselService.sendVarselTilArbeidstaker(varselHendelseInnkalling)

            verify(atLeast = 1) {
                dittSykefravaerMeldingKafkaProducer.sendMelding(
                    any(),
                    any(),
                )
            }

            val utsendte = senderFacade.fetchUferdigstilteVarsler(
                arbeidstakerFnr = PersonIdent("66666666666"),
                kanal = Kanal.DITT_SYKEFRAVAER,
            )

            val innkallinger =
                utsendte.find { it.type == HendelseType.SM_DIALOGMOTE_INNKALT.name && it.fnr == "66666666666" }
            innkallinger shouldNotBeEqualTo null

            val endringer =
                utsendte.find { it.type == HendelseType.SM_DIALOGMOTE_NYTT_TID_STED.name && it.fnr == "66666666666" }
            endringer shouldBeEqualTo null

            val varselHendelseEndring = ArbeidstakerHendelse(
                HendelseType.SM_DIALOGMOTE_NYTT_TID_STED,
                false,
                varselData(journalpostUuid, journalpostId),
                "66666666666",
                orgnummer,
            )

            dialogmoteInnkallingSykmeldtVarselService.sendVarselTilArbeidstaker(varselHendelseEndring)

            val utsendte2 = senderFacade.fetchUferdigstilteVarsler(
                arbeidstakerFnr = PersonIdent("66666666666"),
                kanal = Kanal.DITT_SYKEFRAVAER,
            )
            val innkallinger2 =
                utsendte2.find { it.type == HendelseType.SM_DIALOGMOTE_INNKALT.name && it.fnr == "66666666666" }
            val endringer2 =
                utsendte2.find { it.type == HendelseType.SM_DIALOGMOTE_NYTT_TID_STED.name && it.fnr == "66666666666" }

            innkallinger2 shouldBeEqualTo null
            endringer2 shouldNotBeEqualTo null

            dialogmoteInnkallingSykmeldtVarselService.sendVarselTilArbeidstaker(varselHendelseEndring)

            val utsendte3 = senderFacade.fetchUferdigstilteVarsler(
                arbeidstakerFnr = PersonIdent("66666666666"),
                kanal = Kanal.DITT_SYKEFRAVAER,
            )
            val innkallinger3 =
                utsendte3.find { it.type == HendelseType.SM_DIALOGMOTE_INNKALT.name && it.fnr == "66666666666" }
            val endringer3 =
                utsendte3.find { it.type == HendelseType.SM_DIALOGMOTE_NYTT_TID_STED.name && it.fnr == "66666666666" }

            innkallinger3 shouldBeEqualTo null
            endringer3 shouldNotBeEqualTo null

            verify(exactly = 2) { dittSykefravaerMeldingKafkaProducer.ferdigstillMelding("123", "66666666666") }
        }

        it("Avlysning hendelse skal ferdigstille tidligere innkalling") {
            coEvery { accessControlService.getUserAccessStatus("66666666666") } returns
                UserAccessStatus("66666666666", true)
            coEvery { dittSykefravaerMeldingKafkaProducer.sendMelding(any(), any()) } returns "456"

            val varselHendelseInnkalling = ArbeidstakerHendelse(
                hendelseType,
                false,
                varselData(journalpostUuid, journalpostId),
                "66666666666",
                orgnummer,
            )

            dialogmoteInnkallingSykmeldtVarselService.sendVarselTilArbeidstaker(varselHendelseInnkalling)

            verify(atLeast = 1) {
                dittSykefravaerMeldingKafkaProducer.sendMelding(
                    any(),
                    any(),
                )
            }

            val utsendte = senderFacade.fetchUferdigstilteVarsler(
                arbeidstakerFnr = PersonIdent("66666666666"),
                kanal = Kanal.DITT_SYKEFRAVAER,
            )

            val innkallinger =
                utsendte.find { it.type == HendelseType.SM_DIALOGMOTE_INNKALT.name && it.fnr == "66666666666" }
            innkallinger shouldNotBeEqualTo null

            val avlysninger =
                utsendte.find { it.type == HendelseType.SM_DIALOGMOTE_AVLYST.name && it.fnr == "66666666666" }
            avlysninger shouldBeEqualTo null

            val varselHendelseAvlyst = ArbeidstakerHendelse(
                HendelseType.SM_DIALOGMOTE_AVLYST,
                false,
                varselData(journalpostUuid, journalpostId),
                "66666666666",
                orgnummer,
            )

            dialogmoteInnkallingSykmeldtVarselService.sendVarselTilArbeidstaker(varselHendelseAvlyst)

            val utsendte2 = senderFacade.fetchUferdigstilteVarsler(
                arbeidstakerFnr = PersonIdent("66666666666"),
                kanal = Kanal.DITT_SYKEFRAVAER,
            )
            val innkallinger2 =
                utsendte2.find { it.type == HendelseType.SM_DIALOGMOTE_INNKALT.name && it.fnr == "66666666666" }
            val avlysninger2 =
                utsendte2.find { it.type == HendelseType.SM_DIALOGMOTE_AVLYST.name && it.fnr == "66666666666" }

            innkallinger2 shouldBeEqualTo null
            avlysninger2 shouldNotBeEqualTo null

            dialogmoteInnkallingSykmeldtVarselService.sendVarselTilArbeidstaker(varselHendelseAvlyst)

            val utsendte3 = senderFacade.fetchUferdigstilteVarsler(
                arbeidstakerFnr = PersonIdent("66666666666"),
                kanal = Kanal.DITT_SYKEFRAVAER,
            )
            val innkallinger3 =
                utsendte3.find { it.type == HendelseType.SM_DIALOGMOTE_INNKALT.name && it.fnr == "66666666666" }
            val avlysninger3 =
                utsendte3.find { it.type == HendelseType.SM_DIALOGMOTE_AVLYST.name && it.fnr == "66666666666" }

            innkallinger3 shouldBeEqualTo null
            avlysninger3 shouldNotBeEqualTo null

            verify(exactly = 2) { dittSykefravaerMeldingKafkaProducer.ferdigstillMelding("456", "66666666666") }
        }

        it("Ny innkalling hendelse skal ferdigstille tidligere innkalling") {
            coEvery { accessControlService.getUserAccessStatus("66666666666") } returns
                UserAccessStatus("66666666666", true)
            coEvery { dittSykefravaerMeldingKafkaProducer.sendMelding(any(), any()) } returns "789"

            val varselHendelseInnkalling = ArbeidstakerHendelse(
                hendelseType,
                false,
                varselData(journalpostUuid, journalpostId),
                "66666666666",
                orgnummer,
            )

            dialogmoteInnkallingSykmeldtVarselService.sendVarselTilArbeidstaker(varselHendelseInnkalling)

            verify(atLeast = 1) {
                dittSykefravaerMeldingKafkaProducer.sendMelding(
                    any(),
                    any(),
                )
            }

            val utsendte = senderFacade.fetchUferdigstilteVarsler(
                arbeidstakerFnr = PersonIdent("66666666666"),
                kanal = Kanal.DITT_SYKEFRAVAER,
            )

            val innkallinger =
                utsendte.filter { it.type == HendelseType.SM_DIALOGMOTE_INNKALT.name && it.fnr == "66666666666" }
            innkallinger shouldNotBeEqualTo null
            innkallinger.size shouldBe 1

            val varselHendelseEndring = ArbeidstakerHendelse(
                HendelseType.SM_DIALOGMOTE_INNKALT,
                false,
                varselData(journalpostUuid, journalpostId),
                "66666666666",
                orgnummer,
            )

            dialogmoteInnkallingSykmeldtVarselService.sendVarselTilArbeidstaker(varselHendelseEndring)

            val utsendte2 = senderFacade.fetchUferdigstilteVarsler(
                arbeidstakerFnr = PersonIdent("66666666666"),
                kanal = Kanal.DITT_SYKEFRAVAER,
            )
            val innkallinger2 =
                utsendte2.filter { it.type == HendelseType.SM_DIALOGMOTE_INNKALT.name && it.fnr == "66666666666" }

            innkallinger2 shouldNotBeEqualTo null
            innkallinger2.size shouldBeEqualTo 1

            verify(exactly = 1) { dittSykefravaerMeldingKafkaProducer.ferdigstillMelding("789", "66666666666") }
        }
    }
})

fun varselData(journalpostUuid: String, journalpostId: String) = """{
        "journalpost": {
            "uuid": "$journalpostUuid",
            "id": "$journalpostId"
        },
        "aktivitetskrav": {
            "sendForhandsvarsel": true,
            "enableMicrofrontend": true,
            "extendMicrofrontendDuration": false
        }
    }
""".trimIndent()

fun getDittSykefravaerMelding(): DittSykefravaerMelding {
    return DittSykefravaerMelding(
        OpprettMelding(
            tekst = "Du er innkalt til dialogmøte - vi trenger svaret ditt",
            lenke = "http://localhost/dialogmoter/sykmeldt/moteinnkalling",
            variant = Variant.INFO,
            lukkbar = true,
            meldingType = "ESYFOVARSEL_DIALOGMOTE_INNKALT",
            synligFremTil = null,
        ),
        lukkMelding = null,
        fnr = fnr1,
    )
}
