package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDateTime
import java.util.UUID
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederRelasjon
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.consumer.narmesteLeder.Tilgang
import no.nav.syfo.consumer.pdl.Foedselsdato
import no.nav.syfo.consumer.pdl.HentPerson
import no.nav.syfo.consumer.pdl.HentPersonData
import no.nav.syfo.consumer.pdl.Navn
import no.nav.syfo.consumer.pdl.PdlClient
import no.nav.syfo.kafka.common.createObjectMapper
import no.nav.syfo.kafka.consumers.varselbus.domain.DialogmoteSvarType
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselData
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselDataDialogmoteSvar
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselDataMotetidspunkt
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselDataNarmesteLeder
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dittsykefravaer.DittSykefravaerMeldingKafkaProducer
import no.nav.syfo.planner.arbeidstakerFnr1
import no.nav.syfo.planner.narmesteLederFnr1
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.KalenderTilstand
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.mocks.orgnummer
import org.amshove.kluent.shouldBeEqualTo

class DialogmoteInnkallingNarmesteLederVarselServiceSpek :
    DescribeSpec({
        val dineSykmeldteHendelseKafkaProducer = mockk<DineSykmeldteHendelseKafkaProducer>()
        val dittSykefravaerMeldingKafkaProducer = mockk<DittSykefravaerMeldingKafkaProducer>(relaxed = true)
        val brukernotifikasjonerService = mockk<BrukernotifikasjonerService>(relaxed = true)
        val arbeidsgiverNotifikasjonService = mockk<ArbeidsgiverNotifikasjonService>(relaxed = true)
        val fysiskBrevUtsendingService = mockk<FysiskBrevUtsendingService>()
        val narmesteLederService = mockk<NarmesteLederService>()
        val pdlClient = mockk<PdlClient>()
        val embeddedDatabase = EmbeddedDatabase()
        val fakeDialogmoterUrl = "http://localhost/dialogmoter"

        val senderFacade =
            SenderFacade(
                dineSykmeldteHendelseKafkaProducer,
                dittSykefravaerMeldingKafkaProducer,
                brukernotifikasjonerService,
                arbeidsgiverNotifikasjonService,
                fysiskBrevUtsendingService,
                embeddedDatabase,
            )
        val dialogmoteInnkallingNarmesteLederVarselService =
            DialogmoteInnkallingNarmesteLederVarselService(
                senderFacade,
                fakeDialogmoterUrl,
                narmesteLederService,
                pdlClient,
            )

        describe("DialogmoteInnkallingVarselServiceSpek") {
            beforeTest {
                clearAllMocks()
                embeddedDatabase.dropData()

                coEvery { narmesteLederService.getNarmesteLederRelasjon(any(), any()) } returns
                    NarmesteLederRelasjon(
                        narmesteLederId = "1234",
                        tilganger = listOf(Tilgang.SYKMELDING),
                        navn = "Hest hestesen",
                        narmesteLederEpost = "egg@egg.no",
                    )
                coEvery { pdlClient.hentPerson(any()) } returns
                    HentPersonData(
                        hentPerson =
                            HentPerson(
                                foedselsdato = listOf(Foedselsdato(foedselsdato = "1990-01-01")),
                                navn = listOf(Navn(fornavn = "Test", mellomnavn = null, etternavn = "Testesen")),
                            ),
                    )
                coEvery { arbeidsgiverNotifikasjonService.createNewSak(any()) } returns UUID.randomUUID().toString()
            }

            it("Should create sak and kalenderavtale for innkalling, but not send notifikasjon") {
                val varselHendelseInnkalling = createHendelse(type = HendelseType.NL_DIALOGMOTE_INNKALT)
                dialogmoteInnkallingNarmesteLederVarselService.sendVarselTilNarmesteLeder(varselHendelseInnkalling)

                coVerify(exactly = 1) {
                    arbeidsgiverNotifikasjonService.createNewSak(any())
                }
                coVerify(exactly = 1) {
                    arbeidsgiverNotifikasjonService.createNewKalenderavtale(any())
                }
                coVerify(exactly = 0) {
                    arbeidsgiverNotifikasjonService.sendNotifikasjon(any())
                }
            }

            it("Referat should send notifikasjon") {
                val varselHendelseInnkalling = createHendelse(type = HendelseType.NL_DIALOGMOTE_INNKALT)
                dialogmoteInnkallingNarmesteLederVarselService.sendVarselTilNarmesteLeder(varselHendelseInnkalling)

                val varselHendelseReferat = createHendelse(type = HendelseType.NL_DIALOGMOTE_REFERAT)
                dialogmoteInnkallingNarmesteLederVarselService.sendVarselTilNarmesteLeder(varselHendelseReferat)

                coVerify(exactly = 1) {
                    arbeidsgiverNotifikasjonService.sendNotifikasjon(any())
                }
            }

            it("Should update kalenderavtale correctly on svar") {
                val varselHendelseInnkalling = createHendelse(type = HendelseType.NL_DIALOGMOTE_INNKALT)
                dialogmoteInnkallingNarmesteLederVarselService.sendVarselTilNarmesteLeder(varselHendelseInnkalling)

                val varselHendelseSvar =
                    createHendelse(type = HendelseType.NL_DIALOGMOTE_SVAR).copy(
                        data =
                            createObjectMapper().writeValueAsString(
                                VarselData(
                                    narmesteLeder = VarselDataNarmesteLeder(navn = "Test Testesen"),
                                    dialogmoteSvar =
                                        VarselDataDialogmoteSvar(
                                            svar = DialogmoteSvarType.KOMMER,
                                        ),
                                ),
                            ),
                    )
                dialogmoteInnkallingNarmesteLederVarselService.sendVarselTilNarmesteLeder(varselHendelseSvar)

                coVerify(exactly = 1) {
                    arbeidsgiverNotifikasjonService.updateKalenderavtale(
                        withArg { input ->
                            input.nyTilstand shouldBeEqualTo KalenderTilstand.ARBEIDSGIVER_HAR_GODTATT
                        },
                    )
                }
            }

            it("Nytt tid/sted should delete existing kalenderavtale and create new") {
                val varselHendelseInnkalling = createHendelse(type = HendelseType.NL_DIALOGMOTE_INNKALT)
                dialogmoteInnkallingNarmesteLederVarselService.sendVarselTilNarmesteLeder(varselHendelseInnkalling)

                val varselHendelseEndring = createHendelse(type = HendelseType.NL_DIALOGMOTE_NYTT_TID_STED)
                dialogmoteInnkallingNarmesteLederVarselService.sendVarselTilNarmesteLeder(varselHendelseEndring)

                coVerify(exactly = 1) {
                    arbeidsgiverNotifikasjonService.updateKalenderavtale(any())
                }
                coVerify(exactly = 2) {
                    arbeidsgiverNotifikasjonService.createNewKalenderavtale(any())
                }
            }

            it("Should avlysning should not create notifikasjon, but should update kalenderavtale") {
                val varselHendelseInnkalling = createHendelse(type = HendelseType.NL_DIALOGMOTE_INNKALT)
                dialogmoteInnkallingNarmesteLederVarselService.sendVarselTilNarmesteLeder(varselHendelseInnkalling)

                val varselHendelseAvlysning = createHendelse(type = HendelseType.NL_DIALOGMOTE_AVLYST)
                dialogmoteInnkallingNarmesteLederVarselService.sendVarselTilNarmesteLeder(varselHendelseAvlysning)

                coVerify(exactly = 1) {
                    arbeidsgiverNotifikasjonService.updateKalenderavtale(any())
                }
                coVerify(exactly = 0) {
                    arbeidsgiverNotifikasjonService.sendNotifikasjon(any())
                }
            }

            it("Sends varsel the old way for changes, if there is no sak") {
                coEvery { arbeidsgiverNotifikasjonService.createNewSak(any()) } returns null

                val varselHendelseInnkalling = createHendelse(type = HendelseType.NL_DIALOGMOTE_NYTT_TID_STED)
                dialogmoteInnkallingNarmesteLederVarselService.sendVarselTilNarmesteLeder(varselHendelseInnkalling)

                coVerify(exactly = 1) {
                    arbeidsgiverNotifikasjonService.sendNotifikasjon(any())
                }
                coVerify(exactly = 0) {
                    arbeidsgiverNotifikasjonService.createNewKalenderavtale(any())
                }
            }
        }
    })

fun createHendelse(type: HendelseType): NarmesteLederHendelse =
    NarmesteLederHendelse(
        type = type,
        ferdigstill = false,
        data =
            createObjectMapper().writeValueAsString(
                VarselData(
                    narmesteLeder = VarselDataNarmesteLeder(navn = "Test Testesen"),
                    motetidspunkt = VarselDataMotetidspunkt(tidspunkt = LocalDateTime.now().plusWeeks(1)),
                ),
            ),
        narmesteLederFnr = narmesteLederFnr1,
        arbeidstakerFnr = arbeidstakerFnr1,
        orgnummer = orgnummer,
    )
