package no.nav.syfo.utils

import no.nav.syfo.kafka.oppfolgingstilfelle.domain.Oppfolgingstilfelle39Uker
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.varsel.arbeidstakerAktorId1
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import kotlin.math.min

object VarselUtilSpek: Spek( {

    defaultTimeout = 20000L

    val embeddedDatabase by lazy { EmbeddedDatabase() }


    describe("VarselUtilSpek") {
        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }

        val varselUtil = VarselUtil(embeddedDatabase)


        it("Sjekk at varselDato er riktig når arbeidstaker har 100 dager med arbeid i tilfelle") {
            val fom = LocalDate.now()

            val antallDagerSykmeldtPeriode1 = 100
            val antallDagerArbeidPeriode2 = 50
            val antallDagerSykmeldtPeriode3 = 100
            val antallDagerArbeidPeriode4 = 50
            val antallDagerSykmeldtPeriode5 = 100

            val antallDagerSykmeldtTotalt = antallDagerSykmeldtPeriode1 +
                                            antallDagerSykmeldtPeriode3 +
                                            antallDagerSykmeldtPeriode5

            val antallDagerSykmeldtForVarsel = min(antallDagerSykmeldtTotalt, antallDager39UkersVarsel.toInt())

            val antallDagerIArbeidTotalt = antallDagerArbeidPeriode2 +
                                           antallDagerArbeidPeriode4

            val antallDagerFraFomTilVarselDato = antallDagerSykmeldtForVarsel + antallDagerIArbeidTotalt

            val antallDagerITilfelleTotalt = antallDagerSykmeldtPeriode1 +
                                             antallDagerArbeidPeriode2 +
                                             antallDagerSykmeldtPeriode3 +
                                             antallDagerArbeidPeriode4 +
                                             antallDagerSykmeldtPeriode5

            val tom = fom.plusDays(antallDagerITilfelleTotalt.toLong())

            val forventetUtsendingsdato = fom.plusDays(antallDagerFraFomTilVarselDato.toLong())

            val oppfolgingstilfelle = Oppfolgingstilfelle39Uker(
                arbeidstakerAktorId1,
                16,
                antallDagerSykmeldtTotalt,
                fom,
                tom
            )

            val utsendingsdato = varselUtil.varselDate39Uker(oppfolgingstilfelle)

            utsendingsdato shouldBeEqualTo forventetUtsendingsdato
        }


        it("Dersom antall dager sykmeldt er færre enn $antallDager39UkersVarsel skal varselDato returnere null") {
            val fom = LocalDate.now().minusWeeks(39)
            val tom = LocalDate.now().plusWeeks(1)
            val antallDagerSykmeldtTotalt = 273


            val oppfolgingstilfelle = Oppfolgingstilfelle39Uker(
                arbeidstakerAktorId1,
                16,
                antallDagerSykmeldtTotalt,
                fom,
                tom
            )

            val utsendingsdato = varselUtil.varselDate39Uker(oppfolgingstilfelle)

            utsendingsdato shouldBeEqualTo null
        }


        it("Dersom sykeforløpet startet for akkurat 39 uker siden skal varseldato være i dag") {
            val antallDagerSykmeldtTotalt = 306
            val fom = LocalDate.now().minusWeeks(39)
            val tom = LocalDate.now().plusDays(antallDagerSykmeldtTotalt - (39L * 7) - 1)


            val oppfolgingstilfelle = Oppfolgingstilfelle39Uker(
                arbeidstakerAktorId1,
                16,
                antallDagerSykmeldtTotalt,
                fom,
                tom
            )
            val utsendingsdato = varselUtil.varselDate39Uker(oppfolgingstilfelle)

            val forventetUtsendingsdato = LocalDate.now()
            utsendingsdato shouldBeEqualTo forventetUtsendingsdato
        }

        it("Dersom sykefraværet er akkurat 39 uker (273 dager) skal utsendingsdato bli null, fordi varselet sendes dagen etter (på dag 274)") {
            val fom = LocalDate.now().minusWeeks(38)
            val tom = fom.plusWeeks(39)
            val antallDagerSykmeldtTotalt = 273


            val oppfolgingstilfelle = Oppfolgingstilfelle39Uker(
                arbeidstakerAktorId1,
                16,
                antallDagerSykmeldtTotalt,
                fom,
                tom
            )
            val utsendingsdato = varselUtil.varselDate39Uker(oppfolgingstilfelle)
            utsendingsdato shouldBeEqualTo null
        }

        it("Dersom sykefraværet er under 39 uker skal utsendingsdato bli null") {
            val fom = LocalDate.now().minusWeeks(34)
            val tom = LocalDate.now().plusDays(5)
            val antallDagerSykmeldtTotalt = 245


            val oppfolgingstilfelle = Oppfolgingstilfelle39Uker(
                arbeidstakerAktorId1,
                16,
                antallDagerSykmeldtTotalt,
                fom,
                tom
            )
            val utsendingsdato = varselUtil.varselDate39Uker(oppfolgingstilfelle)
            utsendingsdato shouldBeEqualTo null
        }

    }
})
