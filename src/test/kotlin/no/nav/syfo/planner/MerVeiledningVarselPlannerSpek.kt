package no.nav.syfo.planner

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.consumer.PdlConsumer
import no.nav.syfo.consumer.syfosyketilfelle.SyfosyketilfelleConsumer
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchPlanlagtVarselByFnr
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.db.storeUtsendtVarselTest
import no.nav.syfo.kafka.consumers.oppfolgingstilfelle.domain.Oppfolgingstilfelle39Uker
import no.nav.syfo.service.VarselSendtService
import no.nav.syfo.testutil.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.assertFailsWith

object MerVeiledningVarselPlannerSpek : Spek({

    describe("MerVeiledningVarselPlannerSpek") {
        val embeddedDatabase by lazy { EmbeddedDatabase() }
        val syketilfelleConsumer = mockk<SyfosyketilfelleConsumer>()
        val pdlConsumer = mockk<PdlConsumer>()
        val varselSendtService = VarselSendtService(pdlConsumer, syketilfelleConsumer, embeddedDatabase)

        val merVeiledningVarselPlanner = MerVeiledningVarselPlannerOppfolgingstilfelle(embeddedDatabase, syketilfelleConsumer, varselSendtService)

        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }

        it("Varsel blir planlagt når sykmeldingen strekker seg over 39 uker") {
            val fom = LocalDate.now().minusWeeks(39)
            val tom = LocalDate.now().plusDays(1)

            val oppfolgingstilfelle39Uker = Oppfolgingstilfelle39Uker(
                arbeidstakerAktorId1,
                FULL_AG_PERIODE,
                ChronoUnit.DAYS.between(fom, tom).toInt() + 1,
                fom,
                tom
            )

            coEvery { syketilfelleConsumer.getOppfolgingstilfelle39Uker(any()) } returns oppfolgingstilfelle39Uker

            runBlocking {
                merVeiledningVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1, orgnummer)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                lagreteVarsler.skalHaEt39UkersVarsel()
            }
        }

        it("Skal planlegge varsel dersom tidligere utsendt varsel er i et annet sykeforløp") {
            val idag = LocalDate.now()
            val fom = idag.minusWeeks(39)
            val tom = idag.plusDays(1)

            val oppfolgingstilfelle39Uker = Oppfolgingstilfelle39Uker(
                arbeidstakerAktorId1,
                FULL_AG_PERIODE,
                ChronoUnit.DAYS.between(fom, tom).toInt(),
                fom,
                tom
            )

            coEvery { syketilfelleConsumer.getOppfolgingstilfelle39Uker(any()) } returns oppfolgingstilfelle39Uker

            val dagenForTilfelleStartet = fom.minusDays(1)
            val tidligereUtsendtVarsel = PPlanlagtVarsel(
                UUID.randomUUID().toString(),
                arbeidstakerFnr1,
                orgnummer,
                arbeidstakerAktorId1,
                VarselType.MER_VEILEDNING.name,
                dagenForTilfelleStartet,
                dagenForTilfelleStartet.atStartOfDay(),
                dagenForTilfelleStartet.atStartOfDay()
            )

            embeddedDatabase.storeUtsendtVarselTest(tidligereUtsendtVarsel)

            runBlocking {
                merVeiledningVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1, orgnummer)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                lagreteVarsler.skalHaEt39UkersVarsel()
            }
        }

        it("Skal IKKE planlegge varsel dersom tidligere utsendt varsel er i samme sykeforløp") {
            val idag = LocalDate.now()
            val fom = idag.minusWeeks(39)
            val tom = idag.plusDays(1)

            val oppfolgingstilfelle39Uker = Oppfolgingstilfelle39Uker(
                arbeidstakerAktorId1,
                FULL_AG_PERIODE,
                ChronoUnit.DAYS.between(fom, tom).toInt(),
                fom,
                tom
            )

            coEvery { syketilfelleConsumer.getOppfolgingstilfelle39Uker(any()) } returns oppfolgingstilfelle39Uker

            val tidligereUtsendtVarsel = PPlanlagtVarsel(
                UUID.randomUUID().toString(),
                arbeidstakerFnr1,
                arbeidstakerAktorId1,
                orgnummer,
                VarselType.MER_VEILEDNING.name,
                fom,
                fom.atStartOfDay(),
                fom.atStartOfDay()
            )

            embeddedDatabase.storeUtsendtVarselTest(tidligereUtsendtVarsel)

            runBlocking {
                merVeiledningVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1, orgnummer)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                lagreteVarsler.skalIkkeHa39UkersVarsel()
            }
        }

        it("Varsel blir IKKE planlagt når sykmeldingen ikke strekker seg over 39 uker") {
            val fom = LocalDate.now().minusWeeks(38)
            val tom = LocalDate.now().plusWeeks(1)

            val oppfolgingstilfelle39Uker = Oppfolgingstilfelle39Uker(
                arbeidstakerAktorId1,
                FULL_AG_PERIODE,
                ChronoUnit.DAYS.between(fom, tom).toInt(),
                fom,
                tom
            )

            coEvery { syketilfelleConsumer.getOppfolgingstilfelle39Uker(any()) } returns oppfolgingstilfelle39Uker

            runBlocking {
                merVeiledningVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1, orgnummer)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                lagreteVarsler.skalIkkeHa39UkersVarsel()
            }
        }

        it("Varsel skal IKKE planlegges dersom utsendingsdatoen er eldre enn dagens dato") {

            val fom = LocalDate.now().minusWeeks(40)
            val tom = LocalDate.now().plusWeeks(1)

            val oppfolgingstilfelle39Uker = Oppfolgingstilfelle39Uker(
                arbeidstakerAktorId1,
                FULL_AG_PERIODE,
                ChronoUnit.DAYS.between(fom, tom).toInt(),
                fom,
                tom
            )

            coEvery { syketilfelleConsumer.getOppfolgingstilfelle39Uker(any()) } returns oppfolgingstilfelle39Uker

            runBlocking {
                merVeiledningVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1, orgnummer)
                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)

                lagreteVarsler.skalIkkeHa39UkersVarsel()
            }
        }

        it("Tidligere usendt varsel er allerede planlagt og blir korrigert av nytt Oppfolgingstilfelle") {
            val fom = LocalDate.now()
            val tom = LocalDate.now().plusWeeks(41)
            val utsendingsdato = fom.plusWeeks(39)

            val fomTidligereVarsel = fom.plusWeeks(1)
            val utsendingsdatoTidligereVarsel = fomTidligereVarsel.plusWeeks(39)

            val tidligerePlanlagtVarsel = PlanlagtVarsel(
                arbeidstakerFnr1,
                arbeidstakerAktorId1,
                orgnummer,
                emptySet(),
                VarselType.MER_VEILEDNING,
                utsendingsdatoTidligereVarsel
            )

            embeddedDatabase.storePlanlagtVarsel(tidligerePlanlagtVarsel)

            val lagredeVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)

            lagredeVarsler.skalHaEt39UkersVarsel()
            lagredeVarsler.skalHaUtsendingPaDato(utsendingsdatoTidligereVarsel)

            val oppfolgingstilfelle39Uker = Oppfolgingstilfelle39Uker(
                arbeidstakerAktorId1,
                FULL_AG_PERIODE,
                ChronoUnit.DAYS.between(fom, tom).toInt() + 1,
                fom,
                tom
            )

            coEvery { syketilfelleConsumer.getOppfolgingstilfelle39Uker(any()) } returns oppfolgingstilfelle39Uker

            runBlocking {
                merVeiledningVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1, orgnummer)
                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)

                lagreteVarsler.skalHaEt39UkersVarsel()
                lagreteVarsler.skalHaUtsendingPaDato(utsendingsdato)
            }
        }

        it("MerVeiledningVarselPlanner kaster RuntimeException dersom syfosyketilfelleConsumer kaster exception") {

            assertFailsWith<RuntimeException> {
                coEvery { syketilfelleConsumer.getOppfolgingstilfelle39Uker(any()) } throws RuntimeException()
                runBlocking {
                    merVeiledningVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1, orgnummer)
                }
            }
        }

        it("MerVeiledningVarselPlanner dropper planlegging når syfosyketilfelle returnerer null") {

            coEvery { syketilfelleConsumer.getOppfolgingstilfelle39Uker(any()) } returns null

            runBlocking {
                merVeiledningVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1, orgnummer)
                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)

                lagreteVarsler.skalIkkeHa39UkersVarsel()
            }
        }
    }
})
