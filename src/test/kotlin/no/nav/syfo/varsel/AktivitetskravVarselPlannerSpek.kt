package no.nav.syfo.varsel

import io.ktor.util.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.consumer.SykmeldingerConsumer
import no.nav.syfo.consumer.domain.OppfolgingstilfellePerson
import no.nav.syfo.consumer.domain.Syketilfellebit
import no.nav.syfo.consumer.domain.Syketilfelledag
import no.nav.syfo.consumer.syfosmregister.SykmeldtStatusResponse
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchPlanlagtVarselByFnr
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.service.SykmeldingService
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime

const val SYKEFORLOP_MIN_DIFF_DAGER: Long = 16L
const val AKTIVITETSKRAV_DAGER: Long = 42L
const val arbeidstakerFnr1 = "07088621268"
const val arbeidstakerFnr2 = "23456789012"
const val arbeidstakerAktorId1 = "1234567890123"
const val arbeidstakerAktorId2 = "2345678901234"

@KtorExperimentalAPI
object AktivitetskravVarselPlannerSpek : Spek({
    //The default timeout of 10 seconds is not sufficient to initialise the embedded database
    defaultTimeout = 20000L

    val embeddedDatabase by lazy { EmbeddedDatabase() }
    val sykmeldingerConsumer = mockk<SykmeldingerConsumer>()

    val aktivitetskravVarselPlanner = AktivitetskravVarselPlanner(embeddedDatabase, SykmeldingService(sykmeldingerConsumer))

    describe("AktivitetskravVarselPlannerSpek") {
        val planlagtVarselToStore2 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("1"), VarselType.MER_VEILEDNING)
        val planlagtVarselToStore3 = PlanlagtVarsel(arbeidstakerFnr2, arbeidstakerAktorId2, setOf("2"), VarselType.AKTIVITETSKRAV)

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
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("ANNET_FRAVAR", "SENDT"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit2 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("ANNET_FRAVAR", "UTDANNING"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit3 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("ANNET_FRAVAR", "BEKREFTET"), "3", LocalDateTime.now(), LocalDateTime.now())

            val syketilfelledag1 = Syketilfelledag(LocalDate.now().minusDays(4), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.now().minusDays(2), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now().plusDays(100), syketilfellebit3)

            val sykmeldtStatusResponse = SykmeldtStatusResponse(erSykmeldt = true, gradert = false, fom = LocalDate.now(), tom = LocalDate.now())

            coEvery { sykmeldingerConsumer.getSykmeldtStatusPaDato(any(), any()) } returns sykmeldtStatusResponse

            runBlocking {
                val oppfolgingstilfellePerson =
                    OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3), syketilfelledag1, 0, false, LocalDateTime.now())
                aktivitetskravVarselPlanner.processOppfolgingstilfelle(oppfolgingstilfellePerson, arbeidstakerFnr1)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                val nrOfRowsFetchedTotal = lagreteVarsler.size

                nrOfRowsFetchedTotal shouldEqual 1

                lagreteVarsler.filter { it.type == VarselType.AKTIVITETSKRAV.name } shouldEqual listOf()
            }
        }

        it("Beregnet aktivitetskrav varsel dato er før dagensdato, varsel skal ikke opprettes") {
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            val syketilfellebit1 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("SYKMELDING", "SENDT"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit2 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("SYKMELDING", "UTDANNING"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit3 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("PAPIRSYKMELDING", "BEKREFTET"), "3", LocalDateTime.now(), LocalDateTime.now())

            val syketilfelledag1 = Syketilfelledag(LocalDate.now().minusDays(60), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.now().minusDays(2), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now().plusDays(100), syketilfellebit3)

            val sykmeldtStatusResponse = SykmeldtStatusResponse(erSykmeldt = true, gradert = false, fom = LocalDate.now(), tom = LocalDate.now())

            coEvery { sykmeldingerConsumer.getSykmeldtStatusPaDato(any(), any()) } returns sykmeldtStatusResponse

            runBlocking {
                val oppfolgingstilfellePerson =
                    OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3), syketilfelledag1, 0, false, LocalDateTime.now())
                aktivitetskravVarselPlanner.processOppfolgingstilfelle(oppfolgingstilfellePerson, arbeidstakerFnr1)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                val nrOfRowsFetchedTotal = lagreteVarsler.size

                nrOfRowsFetchedTotal shouldEqual 1

                lagreteVarsler.filter { it.type == VarselType.AKTIVITETSKRAV.name } shouldEqual listOf()
            }
        }

        it("Beregnet aktivitetskrav varsel dato er etter siste tilfelle dato, varsel skal ikke opprettes") {
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            val syketilfellebit1 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("SYKMELDING", "SENDT"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit2 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("SYKMELDING", "UTDANNING"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit3 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("PAPIRSYKMELDING", "BEKREFTET"), "3", LocalDateTime.now(), LocalDateTime.now())

            val syketilfelledag1 = Syketilfelledag(LocalDate.now().minusDays(4), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.now().minusDays(2), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now().plusDays(6), syketilfellebit3)

            val sykmeldtStatusResponse = SykmeldtStatusResponse(erSykmeldt = true, gradert = false, fom = LocalDate.now(), tom = LocalDate.now())

            coEvery { sykmeldingerConsumer.getSykmeldtStatusPaDato(any(), any()) } returns sykmeldtStatusResponse

            runBlocking {
                val oppfolgingstilfellePerson =
                    OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3), syketilfelledag1, 0, false, LocalDateTime.now())
                aktivitetskravVarselPlanner.processOppfolgingstilfelle(oppfolgingstilfellePerson, arbeidstakerFnr1)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                val nrOfRowsFetchedTotal = lagreteVarsler.size

                nrOfRowsFetchedTotal shouldEqual 1

                lagreteVarsler.filter { it.type == VarselType.AKTIVITETSKRAV.name } shouldEqual listOf()
            }
        }

        it("Sykmeldingsgrad er < enn 100% på beregnet varslingsdato, varsel skal ikke opprettes") {
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            val syketilfellebit1 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("SYKMELDING", "SENDT"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit2 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("SYKMELDING", "UTDANNING"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit3 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("PAPIRSYKMELDING", "BEKREFTET"), "3", LocalDateTime.now(), LocalDateTime.now())

            val syketilfelledag1 = Syketilfelledag(LocalDate.now().minusDays(4), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.now().minusDays(2), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now().plusDays(100), syketilfellebit3)

            val sykmeldtStatusResponse = SykmeldtStatusResponse(erSykmeldt = true, gradert = true, fom = LocalDate.now(), tom = LocalDate.now())

            coEvery { sykmeldingerConsumer.getSykmeldtStatusPaDato(any(), any()) } returns sykmeldtStatusResponse

            runBlocking {
                val oppfolgingstilfellePerson =
                    OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3), syketilfelledag1, 0, false, LocalDateTime.now())
                aktivitetskravVarselPlanner.processOppfolgingstilfelle(oppfolgingstilfellePerson, arbeidstakerFnr1)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                val nrOfRowsFetchedTotal = lagreteVarsler.size

                nrOfRowsFetchedTotal shouldEqual 1

                lagreteVarsler.filter { it.type == VarselType.AKTIVITETSKRAV.name } shouldEqual listOf()
            }
        }

        it("Aktivitetskrav varsel er allerede lagret, varsel skal ikke opprettes") {
            val initPlanlagtVarselToStore = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("1", "2"), VarselType.AKTIVITETSKRAV, LocalDate.of(2021, 6, 27))

            embeddedDatabase.storePlanlagtVarsel(initPlanlagtVarselToStore)

            val syketilfellebit1 =
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    listOf("SYKMELDING", "SENDT"),
                    "1",
                    LocalDate.of(2021, 5, 16).atStartOfDay(),//  Varsel blir 27.06.2021
                    LocalDate.of(2021, 8, 1).atStartOfDay()
                )

            val syketilfelledag1 = Syketilfelledag(LocalDate.now(), syketilfellebit1)

            val sykmeldtStatusResponse = SykmeldtStatusResponse(erSykmeldt = true, gradert = false, fom = LocalDate.now(), tom = LocalDate.now())

            coEvery { sykmeldingerConsumer.getSykmeldtStatusPaDato(any(), any()) } returns sykmeldtStatusResponse

            val lagreteVarsler1 = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            val nrOfRowsFetchedTotal1 = lagreteVarsler1.size

            nrOfRowsFetchedTotal1 shouldEqual 1

            val oppfolgingstilfellePerson = OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1), syketilfelledag1, 0, false, LocalDateTime.now())

            // Skal lagre varsel
            runBlocking { aktivitetskravVarselPlanner.processOppfolgingstilfelle(oppfolgingstilfellePerson, arbeidstakerFnr1) }

            val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            val nrOfRowsFetchedTotal = lagreteVarsler.size

            nrOfRowsFetchedTotal shouldEqual 1
        }

        it("Aktivitetskrav varsel er allerede sendt ut, varsel skal ikke opprettes") {
            val planlagtVarselToStore1 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("1"), VarselType.AKTIVITETSKRAV, LocalDate.now().minusDays(7))

            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore1)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            val syketilfellebit1 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("SYKMELDING", "SENDT"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit2 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("ANNET_FRAVAR", "UTDANNING"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit3 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("PAPIRSYKMELDING", "BEKREFTET"), "3", LocalDateTime.now(), LocalDateTime.now())

            val syketilfelledag1 = Syketilfelledag(LocalDate.now().minusDays(4), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.now().minusDays(2), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now().plusDays(100), syketilfellebit3)

            val sykmeldtStatusResponse = SykmeldtStatusResponse(erSykmeldt = true, gradert = false, fom = LocalDate.now(), tom = LocalDate.now())

            coEvery { sykmeldingerConsumer.getSykmeldtStatusPaDato(any(), any()) } returns sykmeldtStatusResponse

            val oppfolgingstilfellePerson = OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3), syketilfelledag1, 0, false, LocalDateTime.now())
            runBlocking { aktivitetskravVarselPlanner.processOppfolgingstilfelle(oppfolgingstilfellePerson, arbeidstakerFnr1) }

            val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            val nrOfRowsFetchedTotal = lagreteVarsler.size

            nrOfRowsFetchedTotal shouldEqual 2
            nrOfRowsFetchedTotal shouldNotEqual 3

            lagreteVarsler.filter { it.type == VarselType.AKTIVITETSKRAV.name }.size shouldEqual 1
        }

        it("AktivitetskravVarsel blir lagret i database, ett sykeforløp") {
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            val syketilfellebit1 =
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    listOf("SYKMELDING", "SENDT"),
                    "3",
                    LocalDate.of(2021, 8, 31).atStartOfDay(),
                    LocalDate.of(2021, 10, 31).atStartOfDay()
                )
            val syketilfellebit2 =
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    listOf("ANNET_FRAVAR", "UTDANNING"),
                    "3",
                    LocalDate.of(2021, 9, 1).atStartOfDay(),
                    LocalDate.of(2021, 10, 10).atStartOfDay()
                )
            //Ny sykmelding, samme forlop
            val syketilfellebit3 =
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    listOf("SYKMELDING", "SENDT"),
                    "4",
                    LocalDate.of(2021, 9, 1).atStartOfDay(),
                    LocalDate.of(2021, 10, 14).atStartOfDay()
                )

            val syketilfelledag1 = Syketilfelledag(LocalDate.now().minusDays(4), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.now().minusDays(2), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now().plusDays(100), syketilfellebit3)

            val sykmeldtStatusResponse = SykmeldtStatusResponse(erSykmeldt = true, gradert = false, fom = LocalDate.now(), tom = LocalDate.now())

            coEvery { sykmeldingerConsumer.getSykmeldtStatusPaDato(any(), any()) } returns sykmeldtStatusResponse

            val oppfolgingstilfellePerson = OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3), syketilfelledag1, 0, false, LocalDateTime.now())
            runBlocking { aktivitetskravVarselPlanner.processOppfolgingstilfelle(oppfolgingstilfellePerson, arbeidstakerFnr1) }

            val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            val nrOfRowsFetchedTotal = lagreteVarsler.size

            nrOfRowsFetchedTotal shouldEqual 2

            lagreteVarsler.filter { it.type == VarselType.AKTIVITETSKRAV.name }.size shouldEqual 1
        }

        it("To AktivitetskravVarsler blir lagret i database ved nytt sykeforløp selv om det er allerede en varsel i DB som ikke er sendt ut") {
            //Gammel varsel
            val planlagtVarselToStore1 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("1"), VarselType.AKTIVITETSKRAV, LocalDate.now().plusDays(2))

            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore1)//Gammel usendt AKTIVITETSKRAV

            val syketilfellebit1 =
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    listOf("SYKMELDING", "SENDT"),
                    "3",
                    LocalDate.now().atStartOfDay(),
                    LocalDate.now().plusMonths(2).atStartOfDay()
                )
            //Ny sykmelding, nytt forlop
            val syketilfellebit3 =
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now().plusDays(2),
                    LocalDateTime.now().plusDays(2),
                    listOf("SYKMELDING", "SENDT"),
                    "4",
                    LocalDate.now().plusMonths(2).plusDays(SYKEFORLOP_MIN_DIFF_DAGER).atStartOfDay(),
                    LocalDate.now().plusMonths(2).plusDays(SYKEFORLOP_MIN_DIFF_DAGER).plusDays(AKTIVITETSKRAV_DAGER).plusDays(2).atStartOfDay()
                )


            val syketilfelledag1 = Syketilfelledag(LocalDate.now(), syketilfellebit1)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now(), syketilfellebit3)

            val sykmeldtStatusResponse = SykmeldtStatusResponse(erSykmeldt = true, gradert = false, fom = LocalDate.now(), tom = LocalDate.now())

            coEvery { sykmeldingerConsumer.getSykmeldtStatusPaDato(any(), any()) } returns sykmeldtStatusResponse

            val oppfolgingstilfellePerson =
                OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag3), syketilfelledag1, 0, false, LocalDateTime.now())

            runBlocking { aktivitetskravVarselPlanner.processOppfolgingstilfelle(oppfolgingstilfellePerson, arbeidstakerFnr1) }

            val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            val nrOfRowsFetchedTotal = lagreteVarsler.size
            nrOfRowsFetchedTotal shouldEqual 3

            val aktivitetskravVarsler = lagreteVarsler.filter { it.type == VarselType.AKTIVITETSKRAV.name }

            val gammeltVarsel = planlagtVarselToStore1.utsendingsdato
            val nyttVarsel = LocalDate.of(2021, 10, 31).plusDays(SYKEFORLOP_MIN_DIFF_DAGER).plusDays(1).plusDays(AKTIVITETSKRAV_DAGER)

            aktivitetskravVarsler.size shouldEqual 3
            aktivitetskravVarsler.filter { it.utsendingsdato == gammeltVarsel }.toList().size shouldEqual 1
            aktivitetskravVarsler.filter { it.utsendingsdato == nyttVarsel }.toList().size shouldEqual 1
        }

        it("AktivitetskravVarsler blir slettet fra database ved nytt sykeforløp hvis sluttdato i ny sykmelding er før sykeforløpets startdato") {
            //Gammel varsel
            val planlagtVarselToStore1 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("3"), VarselType.AKTIVITETSKRAV, LocalDate.of(2021, 6, 27))
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore1)//Gammel AKTIVITETSKRAV
            //Forste sykmelding, varsling lagres
            val syketilfellebit1 =
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now().plusMinutes(1),
                    LocalDateTime.now().plusMinutes(1),
                    listOf("SYKMELDING", "SENDT"),
                    "3",
                    LocalDate.of(2021, 5, 16).atStartOfDay(),//  Varsel blir 27.06.2021
                    LocalDate.of(2021, 8, 1).atStartOfDay()
                )
            val syketilfellebit2 =
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now().plusMinutes(1),
                    LocalDateTime.now().plusMinutes(1),
                    listOf("SYKMELDING", "SENDT"),
                    "3",
                    LocalDate.of(2021, 5, 16).atStartOfDay(),//  Varsel blir 27.06.2021
                    LocalDate.of(2021, 8, 1).atStartOfDay()
                )
            val syketilfellebit3 =
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now().plusMinutes(1),
                    LocalDateTime.now().plusMinutes(1),
                    listOf("SYKMELDING", "SENDT"),
                    "3",
                    LocalDate.of(2021, 5, 16).atStartOfDay(),//  Varsel blir 27.06.2021
                    LocalDate.of(2021, 8, 1).atStartOfDay()
                )
            //Ny sykmelding, Kortere tom
            val syketilfellebit4 =
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now().plusMinutes(10),
                    LocalDateTime.now().plusMinutes(10),
                    listOf("SYKMELDING", "SENDT"),
                    "4",
                    LocalDate.of(2021, 5, 18).atStartOfDay(),
                    LocalDate.of(2021, 5, 20).atStartOfDay()
                )

            val syketilfelledag1 = Syketilfelledag(LocalDate.of(2021, 5, 16), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.of(2021, 5, 16), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.of(2021, 5, 16), syketilfellebit3)
            val syketilfelledag4 = Syketilfelledag(LocalDate.of(2021, 10, 31), syketilfellebit4)

            val oppfolgingstilfellePerson2 =
                OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3, syketilfelledag4), syketilfelledag4, 0, false, LocalDateTime.now())

            val sykmeldtStatusResponse = SykmeldtStatusResponse(erSykmeldt = true, gradert = false, fom = LocalDate.now(), tom = LocalDate.now())
            coEvery { sykmeldingerConsumer.getSykmeldtStatusPaDato(any(), any()) } returns sykmeldtStatusResponse
            runBlocking { aktivitetskravVarselPlanner.processOppfolgingstilfelle(oppfolgingstilfellePerson2, arbeidstakerFnr1) } // Den totale forlopslengde == 4 dager, varsling fjernes fra db

            val lagreteVarsler2 = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            val nrOfRowsFetchedTotal2 = lagreteVarsler2.size

            nrOfRowsFetchedTotal2 shouldEqual 0

        }
    }
})

