package no.nav.syfo.producer.arbeidsgivernotifikasjon.domain

import com.apollographql.apollo.api.Optional
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.syfo.producer.arbeidsgivernotifikasjon.formatAsISO8601DateTime
import no.nav.syfo.testutil.mocks.FNR_1
import no.nav.syfo.testutil.mocks.FNR_2
import no.nav.syfo.testutil.mocks.ORGNUMMER
import java.time.LocalDateTime
import java.util.UUID

class ArbeidsgiverNotifikasjonSpek :
    DescribeSpec({
        val varselId = UUID.randomUUID().toString()
        val grupperingsid = UUID.randomUUID().toString()
        val messageText = "Dette er en tekst"
        val emailTitle = "Oppfølging"
        val emailBody = "Dette er e-postinnhold"
        val hardDeleteDate = LocalDateTime.now().plusDays(1)

        describe("toNyBeskjedMutation") {
            it("mapper naermeste leder til Apollo-input for dagens flyt") {
                val notifikasjon =
                    ArbeidsgiverNotifikasjonNarmesteLeder(
                        varselId = varselId,
                        virksomhetsnummer = ORGNUMMER,
                        url = "https://arbeidsgiver.nav.no",
                        narmesteLederFnr = FNR_1,
                        ansattFnr = FNR_2,
                        messageText = messageText,
                        narmesteLederEpostadresse = "leder@nav.no",
                        merkelapp = "Oppfølging",
                        emailTitle = emailTitle,
                        emailBody = emailBody,
                        hardDeleteDate = hardDeleteDate,
                        grupperingsid = grupperingsid,
                    )

                val mutation = notifikasjon.toNyBeskjedMutation()
                val mottaker =
                    mutation.nyBeskjed
                        .mottakere
                        .getOrThrow()
                        .single()
                val eksterntVarsel =
                    mutation.nyBeskjed
                        .eksterneVarsler
                        .getOrThrow()
                        .single()
                val naermesteLeder = requireNotNull(mottaker.naermesteLeder.getOrThrow())
                val epost = requireNotNull(eksterntVarsel.epost.getOrThrow())
                val kontaktinfo = requireNotNull(epost.mottaker.kontaktinfo.getOrThrow())
                val hardDelete =
                    requireNotNull(
                        mutation.nyBeskjed.metadata
                            .hardDelete
                            .getOrThrow(),
                    )

                mottaker.altinnRessurs shouldBe Optional.Absent
                naermesteLeder.naermesteLederFnr shouldBe FNR_1
                naermesteLeder.ansattFnr shouldBe FNR_2
                eksterntVarsel.altinnressurs shouldBe Optional.Absent
                kontaktinfo.epostadresse shouldBe "leder@nav.no"
                mutation.nyBeskjed.notifikasjon.tekst shouldBe messageText
                hardDelete.den
                    .getOrThrow() shouldBe hardDeleteDate.formatAsISO8601DateTime()
            }

            it("mapper altinnressurs til Apollo-input") {
                val ressursId = "nav_permittering-og-nedbemanning"
                val notifikasjon =
                    ArbeidsgiverNotifikasjonAltinnRessurs(
                        varselId = varselId,
                        virksomhetsnummer = ORGNUMMER,
                        url = "https://arbeidsgiver.nav.no",
                        messageText = messageText,
                        merkelapp = "Oppfølging",
                        emailTitle = emailTitle,
                        emailBody = emailBody,
                        hardDeleteDate = hardDeleteDate,
                        grupperingsid = grupperingsid,
                        ressursId = ressursId,
                    )

                val mutation = notifikasjon.toNyBeskjedMutation()
                val mottaker =
                    mutation.nyBeskjed
                        .mottakere
                        .getOrThrow()
                        .single()
                val eksterntVarsel =
                    mutation.nyBeskjed
                        .eksterneVarsler
                        .getOrThrow()
                        .single()
                val altinnRessurs = requireNotNull(mottaker.altinnRessurs.getOrThrow())
                val altinnressursVarsel = requireNotNull(eksterntVarsel.altinnressurs.getOrThrow())
                val hardDelete =
                    requireNotNull(
                        mutation.nyBeskjed.metadata
                            .hardDelete
                            .getOrThrow(),
                    )

                mottaker.naermesteLeder shouldBe Optional.Absent
                altinnRessurs.ressursId shouldBe ressursId
                eksterntVarsel.epost shouldBe Optional.Absent
                altinnressursVarsel.mottaker.ressursId shouldBe ressursId
                altinnressursVarsel.smsTekst shouldBe messageText
                hardDelete.den
                    .getOrThrow() shouldBe hardDeleteDate.formatAsISO8601DateTime()
            }
        }
    })
