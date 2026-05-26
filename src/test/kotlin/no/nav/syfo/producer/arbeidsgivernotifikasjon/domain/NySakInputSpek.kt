package no.nav.syfo.producer.arbeidsgivernotifikasjon.domain

import com.apollographql.apollo.api.Optional
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.util.UUID

class NySakInputSpek :
    DescribeSpec({
        val hardDeleteDate = LocalDateTime.now().plusDays(1)

        describe("toNySakMutation") {
            it("sender lenke for nærmeste leder-saker") {
                val lenke = "https://www.nav.no"
                val input =
                    NySakNarmesteLederInput(
                        grupperingsid = UUID.randomUUID().toString(),
                        narmestelederId = "narmeste-leder-id",
                        merkelapp = "Oppfølging",
                        virksomhetsnummer = "999999999",
                        narmesteLederFnr = "12345678910",
                        ansattFnr = "10987654321",
                        tittel = "Oppfølging av sykmeldt",
                        lenke = lenke,
                        initiellStatus = SakStatus.MOTTATT,
                        hardDeleteDate = hardDeleteDate,
                    )

                val mutation = input.toNySakMutation()

                mutation.lenke.getOrThrow() shouldBe lenke
            }

            it("utelater lenke for Altinn-saker") {
                val input =
                    NySakAltinnInput(
                        grupperingsid = UUID.randomUUID().toString(),
                        merkelapp = "Dialogmøte",
                        virksomhetsnummer = "999999999",
                        ansattFnr = "10987654321",
                        tittel = "Dialogmøte",
                        initiellStatus = SakStatus.MOTTATT,
                        hardDeleteDate = hardDeleteDate,
                        ressursId = "nav_syfo_dialogmote",
                    )

                val mutation = input.toNySakMutation()

                mutation.lenke shouldBe Optional.Absent
            }
        }
    })
