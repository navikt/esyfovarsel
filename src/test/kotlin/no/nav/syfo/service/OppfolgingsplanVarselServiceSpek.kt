package no.nav.syfo.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP
import no.nav.syfo.BRUKERNOTIFIKASJONER_OPPFOLGINGSPLANER_SYKMELDT_URL
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederRelasjon
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.consumer.narmesteLeder.Tilgang
import no.nav.syfo.consumer.pdl.Foedselsdato
import no.nav.syfo.consumer.pdl.HentPerson
import no.nav.syfo.consumer.pdl.HentPersonData
import no.nav.syfo.consumer.pdl.Navn
import no.nav.syfo.consumer.pdl.PdlClient
import no.nav.syfo.db.domain.PUtsendtVarselFeilet
import no.nav.syfo.db.fetchUtsendtVarselByFnr
import no.nav.syfo.db.fetchUtsendtVarselFeiletByFnr
import no.nav.syfo.db.storeArbeidsgivernotifikasjonerSak
import no.nav.syfo.kafka.common.createObjectMapper
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.DineSykmeldteHendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.DittSykefravaerMeldingKafkaProducer
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NySakNarmesteLederInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NyStatusSakInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.SakStatus
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.mocks.FNR_1
import no.nav.syfo.testutil.mocks.FNR_2
import no.nav.syfo.testutil.mocks.ORGNUMMER
import org.amshove.kluent.shouldBeEqualTo
import java.net.URI
import java.time.LocalDateTime
import java.util.UUID

class OppfolgingsplanVarselServiceSpek :
    DescribeSpec({
        val accessControlService = mockk<AccessControlService>()
        val dineSykmeldteHendelseKafkaProducer = mockk<DineSykmeldteHendelseKafkaProducer>()
        val dittSykefravaerMeldingKafkaProducer = mockk<DittSykefravaerMeldingKafkaProducer>()
        val brukernotifikasjonerService = mockk<BrukernotifikasjonerService>()
        val arbeidsgiverNotifikasjonService = mockk<ArbeidsgiverNotifikasjonService>()
        val fysiskBrevUtsendingService = mockk<FysiskBrevUtsendingService>()
        val embeddedDatabase = EmbeddedDatabase()
        val fakeOppfolgingsplanerUrl = "http://localhost/oppfolgingsplaner"
        val fakeDinesykmeldteUrl = "http://localhost/dinesykmeldte"
        val narmesteLederService = mockk<NarmesteLederService>()
        val pdlClient = mockk<PdlClient>()

        val senderFacade =
            SenderFacade(
                dineSykmeldteHendelseKafkaProducer,
                dittSykefravaerMeldingKafkaProducer,
                brukernotifikasjonerService,
                arbeidsgiverNotifikasjonService,
                fysiskBrevUtsendingService,
                embeddedDatabase,
            )
        val oppfolgingsplanVarselService =
            OppfolgingsplanVarselService(
                senderFacade,
                accessControlService,
                fakeOppfolgingsplanerUrl,
                fakeDinesykmeldteUrl,
                narmesteLederService,
                pdlClient,
            )

        describe("OppfolgingsplanVarselServiceSpek") {
            beforeTest {
                embeddedDatabase.dropData()
                clearAllMocks()
                justRun {
                    brukernotifikasjonerService.sendBrukernotifikasjonVarsel(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                }
                justRun { dineSykmeldteHendelseKafkaProducer.sendVarsel(any()) }
            }

            it("Non-reserved users should be notified externally") {
                coEvery { accessControlService.canUserBeNotifiedByEmailOrSMS(FNR_1) } returns true
                val varselHendelse =
                    ArbeidstakerHendelse(
                        HendelseType.SM_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING,
                        false,
                        null,
                        FNR_1,
                        ORGNUMMER,
                    )
                oppfolgingsplanVarselService.sendVarselTilArbeidstaker(varselHendelse)
                verify(exactly = 1) {
                    brukernotifikasjonerService.sendBrukernotifikasjonVarsel(
                        any(),
                        FNR_1,
                        any(),
                        any(),
                        any(),
                        true,
                    )
                }
            }

            it("Reserved users only notified on 'Min side'") {
                coEvery { accessControlService.canUserBeNotifiedByEmailOrSMS(FNR_2) } returns false
                val varselHendelse =
                    ArbeidstakerHendelse(
                        HendelseType.SM_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING,
                        false,
                        null,
                        FNR_2,
                        ORGNUMMER,
                    )
                oppfolgingsplanVarselService.sendVarselTilArbeidstaker(varselHendelse)
                verify(exactly = 1) {
                    brukernotifikasjonerService.sendBrukernotifikasjonVarsel(
                        any(),
                        FNR_2,
                        any(),
                        URI(fakeOppfolgingsplanerUrl + BRUKERNOTIFIKASJONER_OPPFOLGINGSPLANER_SYKMELDT_URL).toURL(),
                        any(),
                        false,
                    )
                }
            }

            it("Oppfolgingsplan foresporsel should create new sak and reuse sak values in notifikasjon") {
                val narmesteLederId = "1234"
                val expectedUrl = "$fakeDinesykmeldteUrl/$narmesteLederId"
                val notifikasjonInputSlot = slot<ArbeidsgiverNotifikasjonNarmestelederInput>()

                coEvery { narmesteLederService.getNarmesteLederRelasjon(FNR_1, ORGNUMMER) } returns narmesteLederRelasjon(narmesteLederId)
                coEvery { pdlClient.hentPerson(FNR_1) } returns personData()
                coEvery { arbeidsgiverNotifikasjonService.createNewSak(any()) } returns UUID.randomUUID().toString()
                coEvery { arbeidsgiverNotifikasjonService.sendNotifikasjon(any<ArbeidsgiverNotifikasjonNarmestelederInput>()) } returns true

                oppfolgingsplanVarselService.sendOppfolgingsplanForesporselVarselTilNarmesteLeder(
                    oppfolgingsplanForesporselHendelse(),
                )

                coVerify(exactly = 1) {
                    arbeidsgiverNotifikasjonService.sendNotifikasjon(capture(notifikasjonInputSlot))
                }
                coVerify(exactly = 1) {
                    arbeidsgiverNotifikasjonService.createNewSak(any())
                }
                coVerify(exactly = 1) { narmesteLederService.getNarmesteLederRelasjon(FNR_1, ORGNUMMER) }

                val storedSak =
                    senderFacade.getPaagaaendeSak(
                        narmesteLederId = narmesteLederId,
                        merkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                    )

                notifikasjonInputSlot.captured.link shouldBeEqualTo expectedUrl
                notifikasjonInputSlot.captured.grupperingsid shouldBeEqualTo storedSak?.grupperingsid
                storedSak?.lenke shouldBeEqualTo expectedUrl
                storedSak?.hardDeleteDate shouldBeEqualTo notifikasjonInputSlot.captured.hardDeleteDate
            }

            it("Oppfolgingsplan foresporsel should reuse existing sak and preserve status") {
                val narmesteLederId = "1234"
                val existingUrl = "https://existing.example/sak"
                val eksisterendeSak =
                    NySakNarmesteLederInput(
                        grupperingsid = UUID.randomUUID().toString(),
                        narmestelederId = narmesteLederId,
                        merkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                        virksomhetsnummer = ORGNUMMER,
                        narmesteLederFnr = FNR_2,
                        ansattFnr = FNR_1,
                        tittel = "Oppfølging av sykmeldt",
                        lenke = existingUrl,
                        initiellStatus = SakStatus.UNDER_BEHANDLING,
                        hardDeleteDate = LocalDateTime.now().plusDays(1),
                    )
                val sakId =
                    embeddedDatabase.storeArbeidsgivernotifikasjonerSak(
                        eksisterendeSak,
                        eksternSakId = "sak-1",
                    )
                val notifikasjonInputSlot = slot<ArbeidsgiverNotifikasjonNarmestelederInput>()
                val nyStatusSakInputSlot = slot<NyStatusSakInput>()
                coEvery { narmesteLederService.getNarmesteLederRelasjon(FNR_1, ORGNUMMER) } returns narmesteLederRelasjon(narmesteLederId)
                coEvery { arbeidsgiverNotifikasjonService.nyStatusSak(capture(nyStatusSakInputSlot)) } returns "sak-1"
                coEvery { arbeidsgiverNotifikasjonService.sendNotifikasjon(capture(notifikasjonInputSlot)) } returns true

                oppfolgingsplanVarselService.sendOppfolgingsplanForesporselVarselTilNarmesteLeder(
                    oppfolgingsplanForesporselHendelse(),
                )

                val updatedSak =
                    senderFacade.getPaagaaendeSak(
                        narmesteLederId = narmesteLederId,
                        merkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                    )

                notifikasjonInputSlot.captured.grupperingsid shouldBeEqualTo eksisterendeSak.grupperingsid
                notifikasjonInputSlot.captured.link shouldBeEqualTo existingUrl
                nyStatusSakInputSlot.captured.grupperingsId shouldBeEqualTo eksisterendeSak.grupperingsid
                nyStatusSakInputSlot.captured.sakStatus shouldBeEqualTo
                    eksisterendeSak.initiellStatus.let { SakStatus.valueOf(it.name) }
                nyStatusSakInputSlot.captured.oppdatertHardDeleteDateTime shouldBeEqualTo
                    notifikasjonInputSlot.captured.hardDeleteDate
                updatedSak?.id shouldBeEqualTo sakId
                updatedSak?.hardDeleteDate shouldBeEqualTo notifikasjonInputSlot.captured.hardDeleteDate
                coVerify(exactly = 0) { arbeidsgiverNotifikasjonService.createNewSak(any()) }
                coVerify(exactly = 0) { pdlClient.hentPerson(any()) }
                coVerify(exactly = 1) { narmesteLederService.getNarmesteLederRelasjon(FNR_1, ORGNUMMER) }
            }

            it("Oppfolgingsplan foresporsel should fallback to generated link for existing sak without lenke") {
                val narmesteLederId = "1234"
                val expectedUrl = "$fakeDinesykmeldteUrl/$narmesteLederId"
                val eksisterendeSak =
                    NySakNarmesteLederInput(
                        grupperingsid = UUID.randomUUID().toString(),
                        narmestelederId = narmesteLederId,
                        merkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                        virksomhetsnummer = ORGNUMMER,
                        narmesteLederFnr = FNR_2,
                        ansattFnr = FNR_1,
                        tittel = "Oppfølging av sykmeldt",
                        lenke = expectedUrl,
                        initiellStatus = SakStatus.MOTTATT,
                        hardDeleteDate = LocalDateTime.now().plusDays(1),
                    )
                val sakId =
                    embeddedDatabase.storeArbeidsgivernotifikasjonerSak(
                        eksisterendeSak,
                        eksternSakId = "sak-1",
                    )
                embeddedDatabase.connection.use { connection ->
                    val preparedStatement =
                        connection.prepareStatement(
                            """
                            UPDATE ARBEIDSGIVERNOTIFIKASJONER_SAK
                            SET lenke = NULL
                            WHERE id = ?
                            """.trimIndent(),
                        )
                    preparedStatement.use {
                        preparedStatement.setObject(1, UUID.fromString(sakId))
                        preparedStatement.executeUpdate()
                    }
                    connection.commit()
                }
                val notifikasjonInputSlot = slot<ArbeidsgiverNotifikasjonNarmestelederInput>()
                coEvery { narmesteLederService.getNarmesteLederRelasjon(FNR_1, ORGNUMMER) } returns narmesteLederRelasjon(narmesteLederId)
                coEvery { arbeidsgiverNotifikasjonService.nyStatusSak(any()) } returns "sak-1"
                coEvery { arbeidsgiverNotifikasjonService.sendNotifikasjon(capture(notifikasjonInputSlot)) } returns true

                oppfolgingsplanVarselService.sendOppfolgingsplanForesporselVarselTilNarmesteLeder(
                    oppfolgingsplanForesporselHendelse(),
                )

                notifikasjonInputSlot.captured.grupperingsid shouldBeEqualTo eksisterendeSak.grupperingsid
                notifikasjonInputSlot.captured.link shouldBeEqualTo expectedUrl
                coVerify(exactly = 0) { arbeidsgiverNotifikasjonService.createNewSak(any()) }
                coVerify(exactly = 0) { pdlClient.hentPerson(any()) }
            }

            it("Oppfolgingsplan foresporsel should stop before sak handling when narmeste-lederrelasjon or epost mangler") {
                coEvery { narmesteLederService.getNarmesteLederRelasjon(FNR_1, ORGNUMMER) } returns null andThen
                    NarmesteLederRelasjon(
                        narmesteLederId = "1234",
                        narmesteLederFnr = FNR_2,
                        tilganger = listOf(Tilgang.SYKMELDING),
                        navn = "Test Lansen",
                        narmesteLederEpost = null,
                    )

                oppfolgingsplanVarselService.sendOppfolgingsplanForesporselVarselTilNarmesteLeder(
                    oppfolgingsplanForesporselHendelse(),
                )
                oppfolgingsplanVarselService.sendOppfolgingsplanForesporselVarselTilNarmesteLeder(
                    oppfolgingsplanForesporselHendelse(),
                )

                coVerify(exactly = 0) { arbeidsgiverNotifikasjonService.createNewSak(any()) }
                coVerify(exactly = 0) { arbeidsgiverNotifikasjonService.nyStatusSak(any()) }
                coVerify(exactly = 0) {
                    arbeidsgiverNotifikasjonService.sendNotifikasjon(
                        any<ArbeidsgiverNotifikasjonNarmestelederInput>(),
                    )
                }
                coVerify(exactly = 0) { pdlClient.hentPerson(any()) }
            }

            it("Oppfolgingsplan varselbestilling should create new sak, set link and map payload texts") {
                val narmesteLederId = "1234"
                val expectedUrl = "$fakeDinesykmeldteUrl/$narmesteLederId"
                val dineSykmeldteVarselSlot = slot<DineSykmeldteVarsel>()
                val notifikasjonInputSlot = slot<ArbeidsgiverNotifikasjonNarmestelederInput>()
                justRun { dineSykmeldteHendelseKafkaProducer.sendVarsel(capture(dineSykmeldteVarselSlot)) }
                coEvery { narmesteLederService.getNarmesteLederRelasjon(FNR_1, ORGNUMMER) } returns narmesteLederRelasjon(narmesteLederId)
                coEvery { pdlClient.hentPerson(FNR_1) } returns personData()
                coEvery { arbeidsgiverNotifikasjonService.createNewSak(any()) } returns UUID.randomUUID().toString()
                coEvery { arbeidsgiverNotifikasjonService.sendNotifikasjon(capture(notifikasjonInputSlot)) } returns true

                oppfolgingsplanVarselService.sendVarselbestillingTilNarmesteLeder(oppfolgingsplanVarselbestillingHendelse())

                val storedSak =
                    senderFacade.getPaagaaendeSak(
                        narmesteLederId = narmesteLederId,
                        merkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                    )

                dineSykmeldteVarselSlot.captured.tekst shouldBeEqualTo "Dine Sykmeldte-tekst"
                notifikasjonInputSlot.captured.messageText shouldBeEqualTo "Dine Sykmeldte-tekst"
                notifikasjonInputSlot.captured.epostTittel shouldBeEqualTo "E-posttittel"
                notifikasjonInputSlot.captured.epostHtmlBody shouldBeEqualTo "<p>E-postbody</p>"
                notifikasjonInputSlot.captured.meldingstype shouldBeEqualTo Meldingstype.BESKJED
                notifikasjonInputSlot.captured.link shouldBeEqualTo expectedUrl
                notifikasjonInputSlot.captured.grupperingsid shouldBeEqualTo storedSak?.grupperingsid
                storedSak?.lenke shouldBeEqualTo expectedUrl
                storedSak?.hardDeleteDate shouldBeEqualTo notifikasjonInputSlot.captured.hardDeleteDate
                coVerify(exactly = 1) { arbeidsgiverNotifikasjonService.createNewSak(any()) }
                coVerify(exactly = 0) { arbeidsgiverNotifikasjonService.nyStatusSak(any()) }
            }

            it("Oppfolgingsplan varselbestilling should reuse existing sak, fallback to generated link and update hardDeleteDate locally") {
                val narmesteLederId = "1234"
                val expectedUrl = "$fakeDinesykmeldteUrl/$narmesteLederId"
                val eksisterendeSak =
                    NySakNarmesteLederInput(
                        grupperingsid = UUID.randomUUID().toString(),
                        narmestelederId = narmesteLederId,
                        merkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                        virksomhetsnummer = ORGNUMMER,
                        narmesteLederFnr = FNR_2,
                        ansattFnr = FNR_1,
                        tittel = "Oppfølging av sykmeldt",
                        lenke = "https://unused.example",
                        initiellStatus = SakStatus.UNDER_BEHANDLING,
                        hardDeleteDate = LocalDateTime.now().plusDays(1),
                    )
                val sakId =
                    embeddedDatabase.storeArbeidsgivernotifikasjonerSak(
                        eksisterendeSak,
                        eksternSakId = "sak-1",
                    )
                embeddedDatabase.connection.use { connection ->
                    val preparedStatement =
                        connection.prepareStatement(
                            """
                            UPDATE ARBEIDSGIVERNOTIFIKASJONER_SAK
                            SET lenke = NULL
                            WHERE id = ?
                            """.trimIndent(),
                        )
                    preparedStatement.use {
                        preparedStatement.setObject(1, UUID.fromString(sakId))
                        preparedStatement.executeUpdate()
                    }
                    connection.commit()
                }
                val notifikasjonInputSlot = slot<ArbeidsgiverNotifikasjonNarmestelederInput>()
                val nyStatusSakInputSlot = slot<NyStatusSakInput>()
                coEvery { narmesteLederService.getNarmesteLederRelasjon(FNR_1, ORGNUMMER) } returns
                    narmesteLederRelasjon(narmesteLederId)
                coEvery { arbeidsgiverNotifikasjonService.nyStatusSak(capture(nyStatusSakInputSlot)) } returns "sak-1"
                coEvery { arbeidsgiverNotifikasjonService.sendNotifikasjon(capture(notifikasjonInputSlot)) } returns true

                oppfolgingsplanVarselService.sendVarselbestillingTilNarmesteLeder(
                    oppfolgingsplanVarselbestillingHendelse(),
                )

                val updatedSak =
                    senderFacade.getPaagaaendeSak(
                        narmesteLederId = narmesteLederId,
                        merkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                    )

                notifikasjonInputSlot.captured.grupperingsid shouldBeEqualTo eksisterendeSak.grupperingsid
                notifikasjonInputSlot.captured.link shouldBeEqualTo expectedUrl
                nyStatusSakInputSlot.captured.grupperingsId shouldBeEqualTo eksisterendeSak.grupperingsid
                nyStatusSakInputSlot.captured.sakStatus shouldBeEqualTo
                    eksisterendeSak.initiellStatus.let { SakStatus.valueOf(it.name) }
                nyStatusSakInputSlot.captured.oppdatertHardDeleteDateTime shouldBeEqualTo
                    notifikasjonInputSlot.captured.hardDeleteDate
                updatedSak?.hardDeleteDate shouldBeEqualTo notifikasjonInputSlot.captured.hardDeleteDate
                coVerify(exactly = 0) { arbeidsgiverNotifikasjonService.createNewSak(any()) }
            }

            it("Oppfolgingsplan varselbestilling should skip NL lookup and sak handling for Dine Sykmeldte only") {
                oppfolgingsplanVarselService.sendVarselbestillingTilNarmesteLeder(
                    oppfolgingsplanVarselbestillingHendelse(arbeidsgiverMeldingType = null),
                )

                verify(exactly = 1) { dineSykmeldteHendelseKafkaProducer.sendVarsel(any()) }
                coVerify(exactly = 0) { narmesteLederService.getNarmesteLederRelasjon(any(), any()) }
                coVerify(exactly = 0) { arbeidsgiverNotifikasjonService.createNewSak(any()) }
                coVerify(exactly = 0) {
                    arbeidsgiverNotifikasjonService.sendNotifikasjon(
                        any<ArbeidsgiverNotifikasjonNarmestelederInput>(),
                    )
                }
            }

            it("Oppfolgingsplan varselbestilling should store retryable AG failure when narmeste-lederrelasjon mangler") {
                coEvery { narmesteLederService.getNarmesteLederRelasjon(FNR_1, ORGNUMMER) } returns null

                oppfolgingsplanVarselService.sendVarselbestillingTilNarmesteLeder(oppfolgingsplanVarselbestillingHendelse())

                val utsendteVarsler = embeddedDatabase.fetchUtsendtVarselByFnr(FNR_1)
                val feiledeVarsler = embeddedDatabase.fetchUtsendtVarselFeiletByFnr(FNR_1)

                utsendteVarsler.count { it.kanal == "DINE_SYKMELDTE" } shouldBeEqualTo 1
                feiledeVarsler.size shouldBeEqualTo 1
                feiledeVarsler.single().kanal shouldBeEqualTo "ARBEIDSGIVERNOTIFIKASJON"
                feiledeVarsler.single().hendelseJson shouldBeEqualTo
                    createObjectMapper().writeValueAsString(
                        oppfolgingsplanVarselbestillingHendelse(),
                    )
                val eksternReferanse = requireNotNull(feiledeVarsler.single().uuidEksternReferanse)
                UUID.fromString(eksternReferanse).toString() shouldBeEqualTo eksternReferanse
                coVerify(exactly = 0) {
                    arbeidsgiverNotifikasjonService.sendNotifikasjon(
                        any<ArbeidsgiverNotifikasjonNarmestelederInput>(),
                    )
                }
            }

            it("Oppfolgingsplan varselbestilling should store retryable AG failure when AG send is skipped after sak is prepared") {
                coEvery { narmesteLederService.getNarmesteLederRelasjon(FNR_1, ORGNUMMER) } returns
                    narmesteLederRelasjon("1234")
                coEvery { pdlClient.hentPerson(FNR_1) } returns personData()
                coEvery { arbeidsgiverNotifikasjonService.createNewSak(any()) } returns "sak-1"
                coEvery {
                    arbeidsgiverNotifikasjonService.sendNotifikasjon(
                        any<ArbeidsgiverNotifikasjonNarmestelederInput>(),
                    )
                } returns false

                oppfolgingsplanVarselService.sendVarselbestillingTilNarmesteLeder(
                    oppfolgingsplanVarselbestillingHendelse(),
                )

                val utsendteVarsler = embeddedDatabase.fetchUtsendtVarselByFnr(FNR_1)
                val feiledeVarsler = embeddedDatabase.fetchUtsendtVarselFeiletByFnr(FNR_1)

                utsendteVarsler.count { it.kanal == "DINE_SYKMELDTE" } shouldBeEqualTo 1
                utsendteVarsler.count { it.kanal == "ARBEIDSGIVERNOTIFIKASJON" } shouldBeEqualTo 0
                feiledeVarsler.size shouldBeEqualTo 1
                feiledeVarsler.single().hendelseJson shouldBeEqualTo
                    createObjectMapper().writeValueAsString(
                        oppfolgingsplanVarselbestillingHendelse(),
                    )
            }

            it("Oppfolgingsplan varselbestilling should reject unsupported varselType") {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        oppfolgingsplanVarselService.sendVarselbestillingTilNarmesteLeder(
                            oppfolgingsplanVarselbestillingHendelse(arbeidsgiverMeldingType = "KORT_BESKJED"),
                        )
                    }

                exception.message shouldBeEqualTo "Oppfølgingsplanvarsel har ugyldig data.varselType=KORT_BESKJED"
            }

            it("Oppfolgingsplan varselbestilling should reject missing notifikasjonInnhold") {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        oppfolgingsplanVarselService.sendVarselbestillingTilNarmesteLeder(
                            oppfolgingsplanVarselbestillingHendelse(
                                rawData = """{"varselType":"BESKJED"}""",
                            ),
                        )
                    }

                exception.message shouldBeEqualTo "Oppfølgingsplanvarsel mangler feltet: data.notifikasjonInnhold"
            }

            it("Oppfolgingsplan varselbestilling should reject missing explicit varselTekst") {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        oppfolgingsplanVarselService.sendVarselbestillingTilNarmesteLeder(
                            oppfolgingsplanVarselbestillingHendelse(
                                rawData =
                                    """
                                    {
                                      "arbeidsgiverMeldingType": "BESKJED",
                                      "notifikasjonInnhold": {
                                        "epostTittel": "E-posttittel",
                                        "epostBody": "<p>E-postbody</p>"
                                      }
                                    }
                                    """.trimIndent(),
                            ),
                        )
                    }

                exception.message shouldBeEqualTo
                    "Oppfølgingsplanvarsel mangler feltet: data.notifikasjonInnhold.varselTekst"
            }

            it("Oppfolgingsplan varselbestilling retry should reuse uuidEksternReferanse, reuse sak and not resend Dine Sykmeldte") {
                val notifikasjonInputSlot = slot<ArbeidsgiverNotifikasjonNarmestelederInput>()
                val nyStatusSakInputSlot = slot<NyStatusSakInput>()
                val eksternReferanse = UUID.randomUUID()
                val narmesteLederId = "1234"
                val eksisterendeSak =
                    NySakNarmesteLederInput(
                        grupperingsid = UUID.randomUUID().toString(),
                        narmestelederId = narmesteLederId,
                        merkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                        virksomhetsnummer = ORGNUMMER,
                        narmesteLederFnr = FNR_2,
                        ansattFnr = FNR_1,
                        tittel = "Oppfølging av sykmeldt",
                        lenke = "$fakeDinesykmeldteUrl/$narmesteLederId",
                        initiellStatus = SakStatus.MOTTATT,
                        hardDeleteDate = LocalDateTime.now().plusDays(1),
                    )
                embeddedDatabase.storeArbeidsgivernotifikasjonerSak(eksisterendeSak, eksternSakId = "sak-1")
                coEvery { narmesteLederService.getNarmesteLederRelasjon(FNR_1, ORGNUMMER) } returns narmesteLederRelasjon(narmesteLederId)
                coEvery { arbeidsgiverNotifikasjonService.nyStatusSak(capture(nyStatusSakInputSlot)) } returns "sak-1"
                coEvery { arbeidsgiverNotifikasjonService.sendNotifikasjon(capture(notifikasjonInputSlot)) } returns true

                val result =
                    oppfolgingsplanVarselService.resendVarselbestillingTilArbeidsgiverNotifikasjon(
                        PUtsendtVarselFeilet(
                            uuid = UUID.randomUUID().toString(),
                            uuidEksternReferanse = eksternReferanse.toString(),
                            arbeidstakerFnr = FNR_1,
                            narmesteLederFnr = FNR_2,
                            orgnummer = ORGNUMMER,
                            hendelsetypeNavn = HendelseType.NL_OPPFOLGINGSPLAN_VARSELBESTILLING.name,
                            arbeidsgivernotifikasjonMerkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                            brukernotifikasjonerMeldingType = null,
                            journalpostId = null,
                            kanal = "ARBEIDSGIVERNOTIFIKASJON",
                            feilmelding = "noe galt",
                            utsendtForsokTidspunkt = LocalDateTime.now(),
                            hendelseJson =
                                createObjectMapper().writeValueAsString(
                                    oppfolgingsplanVarselbestillingHendelse(),
                                ),
                        ),
                    )

                result shouldBeEqualTo ArbeidsgiverVarselResendResult.RESENT
                notifikasjonInputSlot.captured.uuid shouldBeEqualTo eksternReferanse
                notifikasjonInputSlot.captured.grupperingsid shouldBeEqualTo eksisterendeSak.grupperingsid
                nyStatusSakInputSlot.captured.grupperingsId shouldBeEqualTo eksisterendeSak.grupperingsid
                verify(exactly = 0) { dineSykmeldteHendelseKafkaProducer.sendVarsel(any()) }
                coVerify(exactly = 0) { arbeidsgiverNotifikasjonService.createNewSak(any()) }
            }

            it("Oppfolgingsplan varselbestilling retry should stay retryable when narmeste-lederrelasjon mangler") {
                coEvery { narmesteLederService.getNarmesteLederRelasjon(FNR_1, ORGNUMMER) } returns null

                val result =
                    oppfolgingsplanVarselService.resendVarselbestillingTilArbeidsgiverNotifikasjon(
                        PUtsendtVarselFeilet(
                            uuid = UUID.randomUUID().toString(),
                            uuidEksternReferanse = UUID.randomUUID().toString(),
                            arbeidstakerFnr = FNR_1,
                            narmesteLederFnr = FNR_2,
                            orgnummer = ORGNUMMER,
                            hendelsetypeNavn = HendelseType.NL_OPPFOLGINGSPLAN_VARSELBESTILLING.name,
                            arbeidsgivernotifikasjonMerkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                            brukernotifikasjonerMeldingType = null,
                            journalpostId = null,
                            kanal = "ARBEIDSGIVERNOTIFIKASJON",
                            feilmelding = "noe galt",
                            utsendtForsokTidspunkt = LocalDateTime.now(),
                            hendelseJson =
                                createObjectMapper().writeValueAsString(
                                    oppfolgingsplanVarselbestillingHendelse(),
                                ),
                        ),
                    )

                result shouldBeEqualTo ArbeidsgiverVarselResendResult.RETRYABLE_FAILURE
                coVerify(exactly = 0) {
                    arbeidsgiverNotifikasjonService.sendNotifikasjon(
                        any<ArbeidsgiverNotifikasjonNarmestelederInput>(),
                    )
                }
            }

            it("Oppfolgingsplan varselbestilling retry should ignore extra smsTekst in stored hendelseJson") {
                val notifikasjonInputSlot = slot<ArbeidsgiverNotifikasjonNarmestelederInput>()
                coEvery { narmesteLederService.getNarmesteLederRelasjon(FNR_1, ORGNUMMER) } returns narmesteLederRelasjon("1234")
                coEvery { pdlClient.hentPerson(FNR_1) } returns personData()
                coEvery { arbeidsgiverNotifikasjonService.createNewSak(any()) } returns UUID.randomUUID().toString()
                coEvery { arbeidsgiverNotifikasjonService.sendNotifikasjon(capture(notifikasjonInputSlot)) } returns true

                val result =
                    oppfolgingsplanVarselService.resendVarselbestillingTilArbeidsgiverNotifikasjon(
                        PUtsendtVarselFeilet(
                            uuid = UUID.randomUUID().toString(),
                            uuidEksternReferanse = UUID.randomUUID().toString(),
                            arbeidstakerFnr = FNR_1,
                            narmesteLederFnr = FNR_2,
                            orgnummer = ORGNUMMER,
                            hendelsetypeNavn = HendelseType.NL_OPPFOLGINGSPLAN_VARSELBESTILLING.name,
                            arbeidsgivernotifikasjonMerkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                            brukernotifikasjonerMeldingType = null,
                            journalpostId = null,
                            kanal = "ARBEIDSGIVERNOTIFIKASJON",
                            feilmelding = "noe galt",
                            utsendtForsokTidspunkt = LocalDateTime.now(),
                            hendelseJson =
                                """
                                {
                                  "@type": "NarmesteLederHendelse",
                                  "type": "NL_OPPFOLGINGSPLAN_VARSELBESTILLING",
                                  "ferdigstill": false,
                                  "narmesteLederFnr": "$FNR_2",
                                  "arbeidstakerFnr": "$FNR_1",
                                  "orgnummer": "$ORGNUMMER",
                                  "data": {
                                    "arbeidsgiverMeldingType": "BESKJED",
                                    "dineSykmeldteHendelseType": "OPPFOLGINGSPLAN_OPPRETTET",
                                    "notifikasjonInnhold": {
                                      "epostTittel": "E-posttittel",
                                      "epostBody": "<p>E-postbody</p>",
                                      "smsTekst": "Gammel sms-tekst",
                                      "varselTekst": "Dine Sykmeldte-tekst"
                                    }
                                  }
                                }
                                """.trimIndent(),
                        ),
                    )

                result shouldBeEqualTo ArbeidsgiverVarselResendResult.RESENT
                notifikasjonInputSlot.captured.messageText shouldBeEqualTo "Dine Sykmeldte-tekst"
            }
        }
    })

private fun oppfolgingsplanVarselbestillingHendelse(
    arbeidsgiverMeldingType: String? = Meldingstype.BESKJED.name,
    dineSykmeldteHendelseType: String? = DineSykmeldteHendelseType.OPPFOLGINGSPLAN_PAAMINNELSE.name,
    rawData: String? = null,
) = NarmesteLederHendelse(
    type = HendelseType.NL_OPPFOLGINGSPLAN_VARSELBESTILLING,
    ferdigstill = false,
    data =
        createObjectMapper().readTree(
            rawData ?: defaultOppfolgingsplanVarselbestillingData(arbeidsgiverMeldingType, dineSykmeldteHendelseType),
        ),
    narmesteLederFnr = FNR_2,
    arbeidstakerFnr = FNR_1,
    orgnummer = ORGNUMMER,
)

private fun oppfolgingsplanForesporselHendelse() =
    NarmesteLederHendelse(
        type = HendelseType.NL_OPPFOLGINGSPLAN_FORESPORSEL,
        ferdigstill = false,
        data = null,
        narmesteLederFnr = FNR_2,
        arbeidstakerFnr = FNR_1,
        orgnummer = ORGNUMMER,
    )

private fun defaultOppfolgingsplanVarselbestillingData(
    arbeidsgiverMeldingType: String?,
    dineSykmeldteHendelseType: String?,
) = """
    {
     "arbeidsgiverMeldingType": ${arbeidsgiverMeldingType?.let { "\"$it\"" } ?: "null"},
     "dineSykmeldteHendelseType": ${dineSykmeldteHendelseType?.let { "\"$it\"" } ?: "null"},
     "notifikasjonInnhold": {
       "epostTittel": "E-posttittel",
       "epostBody": "<p>E-postbody</p>",
       "varselTekst": "Dine Sykmeldte-tekst"
     }
    }
    """.trimIndent()

private fun narmesteLederRelasjon(narmesteLederId: String) =
    NarmesteLederRelasjon(
        narmesteLederId = narmesteLederId,
        narmesteLederFnr = FNR_2,
        tilganger = listOf(Tilgang.SYKMELDING),
        navn = "Test Lansen",
        narmesteLederEpost = "test@test.no",
    )

private fun personData() =
    HentPersonData(
        hentPerson =
            HentPerson(
                foedselsdato = listOf(Foedselsdato(foedselsdato = "1990-01-01")),
                navn = listOf(Navn(fornavn = "Test", mellomnavn = null, etternavn = "Testesen")),
            ),
    )
