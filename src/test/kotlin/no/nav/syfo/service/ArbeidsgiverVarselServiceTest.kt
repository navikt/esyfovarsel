package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP
import no.nav.syfo.db.ARBEIDSTAKER_FNR_1
import no.nav.syfo.db.ORGNUMMER_1
import no.nav.syfo.db.fetchUtsendtVarselByFnr
import no.nav.syfo.db.fetchUtsendtVarselFeiletByFnr
import no.nav.syfo.db.getPaagaaendeArbeidsgivernotifikasjonerSakByType
import no.nav.syfo.db.storeArbeidsgivernotifikasjonerSak
import no.nav.syfo.kafka.common.createObjectMapper
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidsgiverNotifikasjonTilAltinnRessursHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NySakAltinnInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.SAK_TYPE_DIALOGMOTE_UTEN_LEDER
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.SakStatus
import no.nav.syfo.testutil.EmbeddedDatabase
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class ArbeidsgiverVarselServiceTest :
    DescribeSpec({
        val embeddedDatabase = EmbeddedDatabase()
        val arbeidsgiverNotifikasjonService = mockk<ArbeidsgiverNotifikasjonService>()
        val service =
            ArbeidsgiverVarselService(
                database = embeddedDatabase,
                arbeidsgiverNotifikasjonService = arbeidsgiverNotifikasjonService,
            )
        val objectMapper = createObjectMapper()

        beforeTest {
            embeddedDatabase.dropData()
            clearAllMocks()
        }

        describe("ArbeidsgiverVarselService") {
            it("sender arbeidsgivervarsel, oppretter ny sak og lagrer utsendt varsel") {
                val hendelse = arbeidsgiverHendelse()
                val eksternSakId = UUID.randomUUID().toString()
                val inputSlot = mutableListOf<ArbeidsgiverNotifikasjonAltinnRessursInput>()

                coEvery { arbeidsgiverNotifikasjonService.createNewSak(any()) } returns eksternSakId
                coEvery { arbeidsgiverNotifikasjonService.sendNotifikasjon(capture(inputSlot)) } returns "notifikasjon-id"

                service.sendVarselTilArbeidsgiver(hendelse)

                val utsendteVarsler = embeddedDatabase.fetchUtsendtVarselByFnr(ARBEIDSTAKER_FNR_1)
                utsendteVarsler shouldHaveSize 1
                utsendteVarsler.single().kanal shouldBe "ARBEIDSGIVERNOTIFIKASJON"
                utsendteVarsler.single().type shouldBe HendelseType.AG_VARSEL_ALTINN_RESSURS.name
                utsendteVarsler.single().eksternReferanse shouldBe hendelse.eksternReferanseId

                val sak =
                    embeddedDatabase.getPaagaaendeArbeidsgivernotifikasjonerSakByType(
                        ansattFnr = hendelse.arbeidstakerFnr,
                        virksomhetsnummer = hendelse.orgnummer,
                        type = SAK_TYPE_DIALOGMOTE_UTEN_LEDER,
                    )
                sak?.eksternSakId shouldBe eksternSakId
                sak?.ressursId shouldBe hendelse.ressursId
                inputSlot.single().uuid shouldBe UUID.fromString(hendelse.eksternReferanseId)
                inputSlot.single().grupperingsid shouldBe sak?.grupperingsid
                inputSlot.single().hardDeleteDate shouldBe
                    LocalDate
                        .now()
                        .atStartOfDay()
                        .plusDays(1)
                        .plusMonths(4)
            }

            it("gjenbruker eksisterende pågående sak") {
                val hendelse = arbeidsgiverHendelse()
                val eksisterendeSak =
                    NySakAltinnInput(
                        grupperingsid = UUID.randomUUID().toString(),
                        merkelapp = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP,
                        virksomhetsnummer = hendelse.orgnummer,
                        ansattFnr = hendelse.arbeidstakerFnr,
                        tittel = "Dialogmøte",
                        lenke = hendelse.ressursUrl,
                        initiellStatus = SakStatus.MOTTATT,
                        hardDeleteDate = LocalDateTime.now().plusDays(1),
                        ressursId = hendelse.ressursId,
                    )
                embeddedDatabase.storeArbeidsgivernotifikasjonerSak(eksisterendeSak, eksternSakId = "sak-1")
                val inputSlot = mutableListOf<ArbeidsgiverNotifikasjonAltinnRessursInput>()

                coEvery { arbeidsgiverNotifikasjonService.sendNotifikasjon(capture(inputSlot)) } returns "notifikasjon-id"

                service.sendVarselTilArbeidsgiver(hendelse)

                coVerify(exactly = 0) { arbeidsgiverNotifikasjonService.createNewSak(any()) }
                inputSlot.single().grupperingsid shouldBe eksisterendeSak.grupperingsid
            }

            it("gjenbruker ikke ferdigstilte saker") {
                val hendelse = arbeidsgiverHendelse()
                val avsluttetSak =
                    NySakAltinnInput(
                        grupperingsid = UUID.randomUUID().toString(),
                        merkelapp = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP,
                        virksomhetsnummer = hendelse.orgnummer,
                        ansattFnr = hendelse.arbeidstakerFnr,
                        tittel = "Dialogmøte",
                        lenke = hendelse.ressursUrl,
                        initiellStatus = SakStatus.FERDIG,
                        hardDeleteDate = LocalDateTime.now().plusDays(1),
                        ressursId = hendelse.ressursId,
                    )
                embeddedDatabase.storeArbeidsgivernotifikasjonerSak(avsluttetSak, eksternSakId = "sak-avsluttet")
                val inputSlot = mutableListOf<ArbeidsgiverNotifikasjonAltinnRessursInput>()

                coEvery { arbeidsgiverNotifikasjonService.createNewSak(any()) } returns "sak-ny"
                coEvery { arbeidsgiverNotifikasjonService.sendNotifikasjon(capture(inputSlot)) } returns "notifikasjon-id"

                service.sendVarselTilArbeidsgiver(hendelse)

                val nySak =
                    embeddedDatabase.getPaagaaendeArbeidsgivernotifikasjonerSakByType(
                        ansattFnr = hendelse.arbeidstakerFnr,
                        virksomhetsnummer = hendelse.orgnummer,
                        type = SAK_TYPE_DIALOGMOTE_UTEN_LEDER,
                    )
                coVerify(exactly = 1) { arbeidsgiverNotifikasjonService.createNewSak(any()) }
                nySak?.grupperingsid shouldBe inputSlot.single().grupperingsid
                nySak?.grupperingsid shouldNotBe avsluttetSak.grupperingsid
            }

            it("registrerer ugyldig eksternReferanseId som feilet utsending med hendelseJson") {
                val hendelse = arbeidsgiverHendelse(eksternReferanseId = "ugyldig-uuid")

                service.sendVarselTilArbeidsgiver(hendelse)

                val feiledeVarsler = embeddedDatabase.fetchUtsendtVarselFeiletByFnr(ARBEIDSTAKER_FNR_1)
                feiledeVarsler shouldHaveSize 1
                feiledeVarsler.single().uuidEksternReferanse shouldBe "ugyldig-uuid"
                feiledeVarsler.single().feilmelding shouldBe "ArbeidsgiverHendelse har ugyldig eksternReferanseId-format"
                feiledeVarsler.single().hendelseJson shouldContain """"eksternReferanseId":"ugyldig-uuid""""
                coVerify(exactly = 0) { arbeidsgiverNotifikasjonService.createNewSak(any()) }
                coVerify(exactly = 0) {
                    arbeidsgiverNotifikasjonService.sendNotifikasjon(
                        any<ArbeidsgiverNotifikasjonAltinnRessursInput>(),
                    )
                }
            }

            it("lagrer ikke feilet utsending dobbelt når sakopprettelse feiler") {
                val hendelse = arbeidsgiverHendelse()
                coEvery { arbeidsgiverNotifikasjonService.createNewSak(any()) } returns null

                service.sendVarselTilArbeidsgiver(hendelse)

                val feiledeVarsler = embeddedDatabase.fetchUtsendtVarselFeiletByFnr(ARBEIDSTAKER_FNR_1)
                feiledeVarsler shouldHaveSize 1
                feiledeVarsler.single().feilmelding shouldBe
                    "ArbeidsgiverNotifikasjonService returnerte null ID ved opprettelse av sak"
                embeddedDatabase.fetchUtsendtVarselByFnr(ARBEIDSTAKER_FNR_1) shouldHaveSize 0
                coVerify(exactly = 0) {
                    arbeidsgiverNotifikasjonService.sendNotifikasjon(
                        any<ArbeidsgiverNotifikasjonAltinnRessursInput>(),
                    )
                }
            }

            it("lagrer feilet utsending når Altinn-send returnerer null ID") {
                val hendelse = arbeidsgiverHendelse()
                coEvery { arbeidsgiverNotifikasjonService.createNewSak(any()) } returns "sak-1"
                coEvery {
                    arbeidsgiverNotifikasjonService.sendNotifikasjon(any<ArbeidsgiverNotifikasjonAltinnRessursInput>())
                } returns null

                service.sendVarselTilArbeidsgiver(hendelse)

                val feiledeVarsler = embeddedDatabase.fetchUtsendtVarselFeiletByFnr(ARBEIDSTAKER_FNR_1)
                feiledeVarsler shouldHaveSize 1
                feiledeVarsler.single().feilmelding shouldBe
                    "ArbeidsgiverNotifikasjonService returnerte null ID ved utsending av arbeidsgivervarsel"
                feiledeVarsler.single().hendelseJson shouldContain """"ressursId":"nav_syfo_dialogmote""""
                embeddedDatabase.fetchUtsendtVarselByFnr(ARBEIDSTAKER_FNR_1) shouldHaveSize 0
            }

            it("saniterer feilmelding når sending feiler med exception") {
                val hendelse = arbeidsgiverHendelse()
                coEvery { arbeidsgiverNotifikasjonService.createNewSak(any()) } returns "sak-1"
                coEvery {
                    arbeidsgiverNotifikasjonService.sendNotifikasjon(any<ArbeidsgiverNotifikasjonAltinnRessursInput>())
                } throws RuntimeException("boom Body 12345678910")

                service.sendVarselTilArbeidsgiver(hendelse)

                val feiledeVarsler = embeddedDatabase.fetchUtsendtVarselFeiletByFnr(ARBEIDSTAKER_FNR_1)
                feiledeVarsler shouldHaveSize 1
                feiledeVarsler.single().hendelseJson shouldContain """"ressursId":"nav_syfo_dialogmote""""
                feiledeVarsler.single().feilmelding shouldBe "Uventet feil (RuntimeException)"
                embeddedDatabase.fetchUtsendtVarselByFnr(ARBEIDSTAKER_FNR_1) shouldHaveSize 0
            }

            it("hopper over ekstern kall når varselet allerede er lagret som sendt") {
                val hendelse = arbeidsgiverHendelse()
                coEvery { arbeidsgiverNotifikasjonService.createNewSak(any()) } returns "sak-1"
                coEvery {
                    arbeidsgiverNotifikasjonService.sendNotifikasjon(any<ArbeidsgiverNotifikasjonAltinnRessursInput>())
                } returns "notifikasjon-id"
                service.sendVarselTilArbeidsgiver(hendelse)
                clearAllMocks()

                service.sendVarselTilArbeidsgiver(hendelse)

                embeddedDatabase.fetchUtsendtVarselByFnr(ARBEIDSTAKER_FNR_1) shouldHaveSize 1
                coVerify(exactly = 0) { arbeidsgiverNotifikasjonService.createNewSak(any()) }
                coVerify(exactly = 0) {
                    arbeidsgiverNotifikasjonService.sendNotifikasjon(
                        any<ArbeidsgiverNotifikasjonAltinnRessursInput>(),
                    )
                }
            }

            it("lagrer feilet utsending når data-felt mangler") {
                service.sendVarselTilArbeidsgiver(arbeidsgiverHendelse(data = null))

                val feiledeVarsler = embeddedDatabase.fetchUtsendtVarselFeiletByFnr(ARBEIDSTAKER_FNR_1)
                feiledeVarsler shouldHaveSize 1
                feiledeVarsler.single().feilmelding shouldContain "ArbeidsgiverHendelse mangler feltet: data"
            }

            it("lagrer feilet utsending når notifikasjonInnhold mangler") {
                service.sendVarselTilArbeidsgiver(
                    arbeidsgiverHendelse(
                        data = objectMapper.readTree("""{}"""),
                    ),
                )

                val feiledeVarsler = embeddedDatabase.fetchUtsendtVarselFeiletByFnr(ARBEIDSTAKER_FNR_1)
                feiledeVarsler shouldHaveSize 1
                feiledeVarsler.single().feilmelding shouldContain "ArbeidsgiverHendelse mangler feltet: data.notifikasjonInnhold"
            }

            it("returnerer permanent feil ved retry når hendelseJson mangler") {
                val result =
                    service.resendVarselTilArbeidsgiver(
                        arbeidsgiverVarselFeilet(hendelseJson = null),
                    )

                result shouldBe ArbeidsgiverVarselResendResult.PERMANENT_FAILURE
                coVerify(exactly = 0) { arbeidsgiverNotifikasjonService.createNewSak(any()) }
                coVerify(exactly = 0) {
                    arbeidsgiverNotifikasjonService.sendNotifikasjon(
                        any<ArbeidsgiverNotifikasjonAltinnRessursInput>(),
                    )
                }
            }

            it("returnerer permanent feil ved retry når hendelseJson er korrupt") {
                val result =
                    service.resendVarselTilArbeidsgiver(
                        arbeidsgiverVarselFeilet(hendelseJson = """{"data":"""),
                    )

                result shouldBe ArbeidsgiverVarselResendResult.PERMANENT_FAILURE
                coVerify(exactly = 0) { arbeidsgiverNotifikasjonService.createNewSak(any()) }
                coVerify(exactly = 0) {
                    arbeidsgiverNotifikasjonService.sendNotifikasjon(
                        any<ArbeidsgiverNotifikasjonAltinnRessursInput>(),
                    )
                }
            }

            it("returnerer permanent feil ved retry når eksternReferanseId er ugyldig") {
                val result =
                    service.resendVarselTilArbeidsgiver(
                        arbeidsgiverVarselFeilet(eksternReferanseId = "ugyldig-uuid"),
                    )

                result shouldBe ArbeidsgiverVarselResendResult.PERMANENT_FAILURE
                coVerify(exactly = 0) { arbeidsgiverNotifikasjonService.createNewSak(any()) }
                coVerify(exactly = 0) {
                    arbeidsgiverNotifikasjonService.sendNotifikasjon(
                        any<ArbeidsgiverNotifikasjonAltinnRessursInput>(),
                    )
                }
            }
        }
    })

private fun arbeidsgiverHendelse(
    eksternReferanseId: String = UUID.randomUUID().toString(),
    data: Any? =
        createObjectMapper().readTree(
            """
            {
              "motetidspunkt": {
                "tidspunkt": "${hendelseMotetidspunkt()}"
              },
              "notifikasjonInnhold": {
                "epostTittel": "Dialogmøte",
                "epostBody": "Body",
                "smsTekst": "SMS"
              }
            }
            """.trimIndent(),
        ),
) = ArbeidsgiverNotifikasjonTilAltinnRessursHendelse(
    type = HendelseType.AG_VARSEL_ALTINN_RESSURS,
    ferdigstill = false,
    data = data,
    orgnummer = ORGNUMMER_1,
    ressursId = "nav_syfo_dialogmote",
    ressursUrl = "https://www.altinn.no",
    arbeidstakerFnr = ARBEIDSTAKER_FNR_1,
    kilde = "tjeneste.type",
    eksternReferanseId = eksternReferanseId,
)

private fun hendelseMotetidspunkt(): LocalDateTime = LocalDateTime.of(2030, 1, 15, 10, 0)

private fun arbeidsgiverVarselFeilet(
    eksternReferanseId: String = UUID.randomUUID().toString(),
    hendelseJson: String? = createObjectMapper().writeValueAsString(arbeidsgiverHendelse(eksternReferanseId = eksternReferanseId)),
) = no.nav.syfo.db.domain.PUtsendtVarselFeilet(
    uuid = UUID.randomUUID().toString(),
    uuidEksternReferanse = eksternReferanseId,
    arbeidstakerFnr = ARBEIDSTAKER_FNR_1,
    narmesteLederFnr = null,
    orgnummer = ORGNUMMER_1,
    hendelsetypeNavn = HendelseType.AG_VARSEL_ALTINN_RESSURS.name,
    arbeidsgivernotifikasjonMerkelapp = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP,
    brukernotifikasjonerMeldingType = null,
    journalpostId = null,
    kanal = "ARBEIDSGIVERNOTIFIKASJON",
    feilmelding = "noe gikk galt",
    utsendtForsokTidspunkt = LocalDateTime.now(),
    hendelseJson = hendelseJson,
)
