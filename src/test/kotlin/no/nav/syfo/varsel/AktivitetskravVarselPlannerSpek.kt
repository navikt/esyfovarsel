package no.nav.syfo.varsel

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.consumer.SyfosyketilfelleConsumer
import no.nav.syfo.consumer.syfosmregister.SykmeldingerConsumer
import no.nav.syfo.kafka.oppfolgingstilfelle.domain.Syketilfelledag
import no.nav.syfo.consumer.syfosmregister.SykmeldtStatusResponse
import no.nav.syfo.syketilfelle.domain.Syketilfellebit
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchPlanlagtVarselByFnr
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.kafka.oppfolgingstilfelle.domain.OppfolgingstilfellePerson
import no.nav.syfo.service.SykmeldingService
import no.nav.syfo.syketilfelle.domain.Tag.*
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.assertFailsWith

object AktivitetskravVarselPlannerSpek : Spek({
    //The default timeout of 10 seconds is not sufficient to initialise the embedded database
    defaultTimeout = 20000L

    val embeddedDatabase by lazy { EmbeddedDatabase() }
    val sykmeldingerConsumer = mockk<SykmeldingerConsumer>()
    val syfosyketilfelleConsumer = mockk<SyfosyketilfelleConsumer>()

    val aktivitetskravVarselPlanner =
        AktivitetskravVarselPlanner(embeddedDatabase, syfosyketilfelleConsumer, SykmeldingService(sykmeldingerConsumer))

    describe("AktivitetskravVarselPlannerSpek") {
        val planlagtVarselToStore2 =
            PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("1"), VarselType.MER_VEILEDNING)
        val planlagtVarselToStore3 =
            PlanlagtVarsel(arbeidstakerFnr2, arbeidstakerAktorId2, setOf("2"), VarselType.AKTIVITETSKRAV)

        beforeGroup {
        }

        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }

        it("Kun sykmeldingtilfeller blir brukt for beregning av varslingdato, varsel skal ikke opprettes") {
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            val syketilfellebit1 =
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    listOf(ANNET_FRAVAR, SENDT),
                    "3",
                    LocalDate.now(),
                    LocalDate.now().plusDays(7)
                )
            val syketilfellebit2 =
                Syketilfellebit(
                    "2",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    listOf(ANNET_FRAVAR, SENDT),
                    "3",
                    LocalDate.now().plusDays(8),
                    LocalDate.now().plusDays(16)
                )
            val syketilfellebit3 =
                Syketilfellebit(
                    "3",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    listOf(ANNET_FRAVAR, SENDT),
                    "3",
                    LocalDate.now().plusDays(17),
                    LocalDate.now().plusDays(50)
                )

            val syketilfelledag1 = Syketilfelledag(LocalDate.now(), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.now().plusDays(16), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now().plusDays(50), syketilfellebit3)

            val sykmeldtStatusResponse = SykmeldtStatusResponse(
                erSykmeldt = true,
                gradert = false,
                fom = LocalDate.now(),
                tom = LocalDate.now().plusDays(50)
            )
            val oppfolgingstilfellePerson =
                OppfolgingstilfellePerson(
                    arbeidstakerFnr1,
                    listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3),
                    syketilfelledag1,
                    0,
                    false,
                    LocalDateTime.now()
                )

            coEvery { sykmeldingerConsumer.getSykmeldtStatusPaDato(any(), any()) } returns sykmeldtStatusResponse
            coEvery { syfosyketilfelleConsumer.getOppfolgingstilfelle(any()) } returns oppfolgingstilfellePerson

            runBlocking {
                aktivitetskravVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                val nrOfRowsFetchedTotal = lagreteVarsler.size

                nrOfRowsFetchedTotal shouldBeEqualTo 1

                lagreteVarsler.filter { it.type == VarselType.AKTIVITETSKRAV.name } shouldBeEqualTo listOf()
            }
        }

        it("Beregnet aktivitetskrav varsel dato er før dagensdato, varsel skal ikke opprettes") {
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)

            val syketilfellebit1 =
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2", LocalDateTime.now(),
                    LocalDateTime.now(),
                    listOf(SYKMELDING, SENDT),
                    "3",
                    LocalDate.now().minusDays(100),
                    LocalDate.now().minusDays(100)
                )

            val syketilfelledag1 = Syketilfelledag(LocalDate.now().minusDays(60), syketilfellebit1)
            val oppfolgingstilfellePerson =
                OppfolgingstilfellePerson(
                    arbeidstakerFnr1,
                    listOf(syketilfelledag1),
                    syketilfelledag1,
                    0,
                    false,
                    LocalDateTime.now()
                )

            val sykmeldtStatusResponse =
                SykmeldtStatusResponse(erSykmeldt = true, gradert = false, fom = LocalDate.now(), tom = LocalDate.now())

            coEvery { sykmeldingerConsumer.getSykmeldtStatusPaDato(any(), any()) } returns sykmeldtStatusResponse
            coEvery { syfosyketilfelleConsumer.getOppfolgingstilfelle(any()) } returns oppfolgingstilfellePerson

            runBlocking {
                aktivitetskravVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                val nrOfRowsFetchedTotal = lagreteVarsler.size

                nrOfRowsFetchedTotal shouldBeEqualTo 1

                lagreteVarsler.filter { it.type == VarselType.AKTIVITETSKRAV.name } shouldBeEqualTo listOf()
            }
        }

        it("Beregnet aktivitetskrav varsel dato er etter siste tilfelle dato, varsel skal ikke opprettes") {
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            val syketilfellebit1 =
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    listOf(SYKMELDING, SENDT),
                    "3",
                    LocalDate.now(),
                    LocalDate.now()
                )
            val syketilfellebit2 =
                Syketilfellebit(
                    "2",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    listOf(SYKMELDING, SENDT),
                    "3",
                    LocalDate.now(),
                    LocalDate.now()
                )
            val syketilfellebit3 =
                Syketilfellebit(
                    "3",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    listOf(PAPIRSYKMELDING, SENDT),
                    "3",
                    LocalDate.now(),
                    LocalDate.now()
                )

            val syketilfelledag1 = Syketilfelledag(LocalDate.now().minusDays(4), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.now().minusDays(2), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now().plusDays(6), syketilfellebit3)

            val sykmeldtStatusResponse =
                SykmeldtStatusResponse(erSykmeldt = true, gradert = false, fom = LocalDate.now(), tom = LocalDate.now())
            val oppfolgingstilfellePerson =
                OppfolgingstilfellePerson(
                    arbeidstakerFnr1,
                    listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3),
                    syketilfelledag1,
                    0,
                    false,
                    LocalDateTime.now()
                )

            coEvery { sykmeldingerConsumer.getSykmeldtStatusPaDato(any(), any()) } returns sykmeldtStatusResponse
            coEvery { syfosyketilfelleConsumer.getOppfolgingstilfelle(any()) } returns oppfolgingstilfellePerson

            runBlocking {

                aktivitetskravVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                val nrOfRowsFetchedTotal = lagreteVarsler.size

                nrOfRowsFetchedTotal shouldBeEqualTo 1

                lagreteVarsler.filter { it.type == VarselType.AKTIVITETSKRAV.name } shouldBeEqualTo listOf()
            }
        }

        it("Sykmeldingsgrad er < enn 100% på beregnet varslingsdato, varsel skal ikke opprettes") {
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            val syketilfellebit1 =
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    listOf(SYKMELDING, SENDT),
                    "3",
                    LocalDate.now(),
                    LocalDate.now().plusDays(7)
                )
            val syketilfellebit2 =
                Syketilfellebit(
                    "2",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    listOf(SYKMELDING, SENDT),
                    "3",
                    LocalDate.now().plusDays(8),
                    LocalDate.now().plusDays(16)
                )
            val syketilfellebit3 =
                Syketilfellebit(
                    "3",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    listOf(SYKMELDING, SENDT),
                    "3",
                    LocalDate.now().plusDays(17),
                    LocalDate.now().plusDays(50)
                )

            val syketilfelledag1 = Syketilfelledag(LocalDate.now(), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.now().plusDays(16), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now().plusDays(50), syketilfellebit3)

            val sykmeldtStatusResponse = SykmeldtStatusResponse(
                erSykmeldt = true,
                gradert = true,
                fom = LocalDate.now(),
                tom = LocalDate.now().plusDays(50)
            )
            val oppfolgingstilfellePerson =
                OppfolgingstilfellePerson(
                    arbeidstakerFnr1,
                    listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3),
                    syketilfelledag1,
                    0,
                    false,
                    LocalDateTime.now()
                )

            coEvery { sykmeldingerConsumer.getSykmeldtStatusPaDato(any(), any()) } returns sykmeldtStatusResponse
            coEvery { syfosyketilfelleConsumer.getOppfolgingstilfelle(any()) } returns oppfolgingstilfellePerson

            runBlocking {
                aktivitetskravVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                val nrOfRowsFetchedTotal = lagreteVarsler.size

                nrOfRowsFetchedTotal shouldBeEqualTo 1

                lagreteVarsler.filter { it.type == VarselType.AKTIVITETSKRAV.name } shouldBeEqualTo listOf()
            }
        }

        it("Aktivitetskrav varsel er allerede lagret, varsel skal ikke opprettes") { //varsel med samme utsendingsdato er allerede planlagt
            val utsendingsdato = LocalDate.now().plusDays(1)
            val initPlanlagtVarselToStore = PlanlagtVarsel(
                arbeidstakerFnr1,
                arbeidstakerAktorId1,
                setOf("1", "2"),
                VarselType.AKTIVITETSKRAV,
                utsendingsdato
            )

            embeddedDatabase.storePlanlagtVarsel(initPlanlagtVarselToStore)

            val syketilfellebit1 =
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    listOf(SYKMELDING, SENDT),
                    "1",
                    utsendingsdato.minusDays(AKTIVITETSKRAV_DAGER).atStartOfDay().toLocalDate(),
                    utsendingsdato.plusDays(30).atStartOfDay().toLocalDate()
                )

            val syketilfelledag1 = Syketilfelledag(utsendingsdato.minusDays(AKTIVITETSKRAV_DAGER), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.now().plusMonths(1), syketilfellebit1)

            val sykmeldtStatusResponse =
                SykmeldtStatusResponse(erSykmeldt = true, gradert = false, fom = LocalDate.now(), tom = LocalDate.now())
            val oppfolgingstilfellePerson = OppfolgingstilfellePerson(
                arbeidstakerFnr1,
                listOf(syketilfelledag1, syketilfelledag2),
                syketilfelledag1,
                0,
                false,
                LocalDateTime.now()
            )

            coEvery { sykmeldingerConsumer.getSykmeldtStatusPaDato(any(), any()) } returns sykmeldtStatusResponse
            coEvery { syfosyketilfelleConsumer.getOppfolgingstilfelle(any()) } returns oppfolgingstilfellePerson

            val lagreteVarsler1 = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            val nrOfRowsFetchedTotal1 = lagreteVarsler1.size

            nrOfRowsFetchedTotal1 shouldBeEqualTo 1

            // Skal lagre varsel
            runBlocking {
                aktivitetskravVarselPlanner.processOppfolgingstilfelle(
                    arbeidstakerAktorId1,
                    arbeidstakerFnr1
                )
            }

            val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            val nrOfRowsFetchedTotal = lagreteVarsler.size

            nrOfRowsFetchedTotal shouldBeEqualTo 1
        }

        it("AkvitietskravVarselPlanner kaster RuntimeException dersom syfosyketilfelleConsumer kaster exception") {
            assertFailsWith(RuntimeException::class) {
                coEvery { syfosyketilfelleConsumer.getOppfolgingstilfelle(any()) } throws RuntimeException()
                runBlocking {
                    aktivitetskravVarselPlanner.processOppfolgingstilfelle(
                        arbeidstakerAktorId1,
                        arbeidstakerFnr1
                    )
                }
            }
        }

        it("AkvitietskravVarselPlanner dropper planlegging når syfosyketilfelle returnerer null") {

            coEvery { syfosyketilfelleConsumer.getOppfolgingstilfelle(any()) } returns null

            runBlocking {
                aktivitetskravVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1)
                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)

                lagreteVarsler.size shouldBeEqualTo 0
            }

        }

        it("AkvitietskravVarselPlanner fjerner perioder uten sykmelding når den faktiske fraværlengde blir beregnet") {

            val validSyketilfelledager: List<Syketilfelledag> = listOf(
                createValidSyketilfelledag(LocalDate.of(2021, 12, 8).atStartOfDay(), LocalDate.of(2021, 12, 10).atStartOfDay()),
                createValidSyketilfelledag(LocalDate.of(2021, 12, 8).atStartOfDay(), LocalDate.of(2021, 12, 10).atStartOfDay()),
                createValidSyketilfelledag(LocalDate.of(2021, 12, 8).atStartOfDay(), LocalDate.of(2021, 12, 10).atStartOfDay()),

                createValidSyketilfelledag(LocalDate.of(2021, 12, 11).atStartOfDay(), LocalDate.of(2021, 12, 23).atStartOfDay()),

                createValidSyketilfelledag(LocalDate.of(2021, 12, 24).atStartOfDay(), LocalDate.of(2022, 1, 2).atStartOfDay()),
                createValidSyketilfelledag(LocalDate.of(2021, 12, 24).atStartOfDay(), LocalDate.of(2022, 1, 2).atStartOfDay()),
                createValidSyketilfelledag(LocalDate.of(2021, 12, 24).atStartOfDay(), LocalDate.of(2022, 1, 2).atStartOfDay()),

                createValidSyketilfelledag(LocalDate.of(2022, 1, 17).atStartOfDay(), LocalDate.of(2022, 1, 23).atStartOfDay()),
            )

            val numberOfDaysActual = aktivitetskravVarselPlanner.calculateActualNumberOfDaysInTimeline(validSyketilfelledager)
            val numberOfDaysExpected = (ChronoUnit.DAYS.between(LocalDate.of(2021, 12, 8), LocalDate.of(2021, 12, 10)).toInt()
                    + ChronoUnit.DAYS.between(LocalDate.of(2021, 12, 11), LocalDate.of(2021, 12, 23)).toInt()
                    + ChronoUnit.DAYS.between(LocalDate.of(2021, 12, 24), LocalDate.of(2022, 1, 2)).toInt()
                    + ChronoUnit.DAYS.between(LocalDate.of(2022, 1, 17), LocalDate.of(2022, 1, 23)).toInt())

            numberOfDaysActual.shouldNotBeEqualTo(ChronoUnit.DAYS.between(LocalDate.of(2021, 12, 8), LocalDate.of(2022, 1, 23)).toInt())
            numberOfDaysActual.shouldBeEqualTo(numberOfDaysExpected)
        }
    }
})

fun createValidSyketilfelledag(fom: LocalDateTime, tom: LocalDateTime): Syketilfelledag {
    return Syketilfelledag(
        LocalDate.now(),
        Syketilfellebit(
            "", "", "", LocalDateTime.now(), LocalDateTime.now(), listOf(SYKMELDING, BEKREFTET), "", fom.toLocalDate(), tom.toLocalDate()
        )
    )
}
