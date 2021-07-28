package no.nav.syfo.varsel

import io.ktor.util.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.consumer.SyfosyketilfelleConsumer
import no.nav.syfo.consumer.SykmeldingerConsumer
import no.nav.syfo.consumer.domain.OppfolgingstilfellePerson
import no.nav.syfo.consumer.domain.Syketilfellebit
import no.nav.syfo.consumer.domain.Syketilfelledag
import no.nav.syfo.consumer.syfosmregister.SykmeldtStatusResponse
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchAllSykmeldingIdsAndCount
import no.nav.syfo.db.fetchPlanlagtVarselByFnr
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.service.SykmeldingService
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertFailsWith

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
    val syfosyketilfelleConsumer = mockk<SyfosyketilfelleConsumer>()

    val aktivitetskravVarselPlanner = AktivitetskravVarselPlanner(embeddedDatabase, syfosyketilfelleConsumer, SykmeldingService(sykmeldingerConsumer))

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
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("ANNET_FRAVAR", "SENDT"), "3", LocalDateTime.now(), LocalDateTime.now().plusDays(7))
            val syketilfellebit2 =
                Syketilfellebit("2", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("ANNET_FRAVAR", "SENDT"), "3", LocalDateTime.now().plusDays(8), LocalDateTime.now().plusDays(16))
            val syketilfellebit3 =
                Syketilfellebit("3", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("ANNET_FRAVAR", "SENDT"), "3", LocalDateTime.now().plusDays(17), LocalDateTime.now().plusDays(50))

            val syketilfelledag1 = Syketilfelledag(LocalDate.now(), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.now().plusDays(16), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now().plusDays(50), syketilfellebit3)

            val sykmeldtStatusResponse = SykmeldtStatusResponse(erSykmeldt = true, gradert = false, fom = LocalDate.now(), tom = LocalDate.now().plusDays(50))
            val oppfolgingstilfellePerson =
                OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3), syketilfelledag1, 0, false, LocalDateTime.now())

            coEvery { sykmeldingerConsumer.getSykmeldtStatusPaDato(any(), any()) } returns sykmeldtStatusResponse
            coEvery { syfosyketilfelleConsumer.getOppfolgingstilfelle(any()) } returns oppfolgingstilfellePerson

            runBlocking {
                aktivitetskravVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                val nrOfRowsFetchedTotal = lagreteVarsler.size

                nrOfRowsFetchedTotal shouldEqual 1

                lagreteVarsler.filter { it.type == VarselType.AKTIVITETSKRAV.name } shouldEqual listOf()
            }
        }

        it("Beregnet aktivitetskrav varsel dato er før dagensdato, varsel skal ikke opprettes") {
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)

            val syketilfellebit1 =
                Syketilfellebit("1",
                    arbeidstakerAktorId1,
                    "2", LocalDateTime.now(),
                    LocalDateTime.now(),
                    listOf("SYKMELDING", "SENDT"),
                    "3",
                    LocalDateTime.now().minusDays(100),
                    LocalDateTime.now().minusDays(100))

            val syketilfelledag1 = Syketilfelledag(LocalDate.now().minusDays(60), syketilfellebit1)
            val oppfolgingstilfellePerson =
                OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1), syketilfelledag1, 0, false, LocalDateTime.now())

            val sykmeldtStatusResponse = SykmeldtStatusResponse(erSykmeldt = true, gradert = false, fom = LocalDate.now(), tom = LocalDate.now())

            coEvery { sykmeldingerConsumer.getSykmeldtStatusPaDato(any(), any()) } returns sykmeldtStatusResponse
            coEvery { syfosyketilfelleConsumer.getOppfolgingstilfelle(any()) } returns oppfolgingstilfellePerson

            runBlocking {
                aktivitetskravVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1)

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
                Syketilfellebit("2", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("SYKMELDING", "SENDT"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit3 =
                Syketilfellebit("3", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("PAPIRSYKMELDING", "SENDT"), "3", LocalDateTime.now(), LocalDateTime.now())

            val syketilfelledag1 = Syketilfelledag(LocalDate.now().minusDays(4), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.now().minusDays(2), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now().plusDays(6), syketilfellebit3)

            val sykmeldtStatusResponse = SykmeldtStatusResponse(erSykmeldt = true, gradert = false, fom = LocalDate.now(), tom = LocalDate.now())
            val oppfolgingstilfellePerson =
                OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3), syketilfelledag1, 0, false, LocalDateTime.now())

            coEvery { sykmeldingerConsumer.getSykmeldtStatusPaDato(any(), any()) } returns sykmeldtStatusResponse
            coEvery { syfosyketilfelleConsumer.getOppfolgingstilfelle(any()) } returns oppfolgingstilfellePerson

            runBlocking {

                aktivitetskravVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1)

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
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("SYKMELDING", "SENDT"), "3", LocalDateTime.now(), LocalDateTime.now().plusDays(7))
            val syketilfellebit2 =
                Syketilfellebit("2", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("SYKMELDING", "SENDT"), "3", LocalDateTime.now().plusDays(8), LocalDateTime.now().plusDays(16))
            val syketilfellebit3 =
                Syketilfellebit("3", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("SYKMELDING", "SENDT"), "3", LocalDateTime.now().plusDays(17), LocalDateTime.now().plusDays(50))

            val syketilfelledag1 = Syketilfelledag(LocalDate.now(), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.now().plusDays(16), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now().plusDays(50), syketilfellebit3)

            val sykmeldtStatusResponse = SykmeldtStatusResponse(erSykmeldt = true, gradert = true, fom = LocalDate.now(), tom = LocalDate.now().plusDays(50))
            val oppfolgingstilfellePerson =
                OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3), syketilfelledag1, 0, false, LocalDateTime.now())

            coEvery { sykmeldingerConsumer.getSykmeldtStatusPaDato(any(), any()) } returns sykmeldtStatusResponse
            coEvery { syfosyketilfelleConsumer.getOppfolgingstilfelle(any()) } returns oppfolgingstilfellePerson

            runBlocking {
                aktivitetskravVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                val nrOfRowsFetchedTotal = lagreteVarsler.size

                nrOfRowsFetchedTotal shouldEqual 1

                lagreteVarsler.filter { it.type == VarselType.AKTIVITETSKRAV.name } shouldEqual listOf()
            }
        }

        it("Aktivitetskrav varsel er allerede lagret, varsel skal ikke opprettes") {
            val utsendingsdato = LocalDate.now().plusDays(1)
            val initPlanlagtVarselToStore = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("1", "2"), VarselType.AKTIVITETSKRAV, utsendingsdato)

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
                    utsendingsdato.minusDays(AKTIVITETSKRAV_DAGER).atStartOfDay(),
                    utsendingsdato.plusDays(30).atStartOfDay()
                )

            val syketilfelledag1 = Syketilfelledag(LocalDate.now(), syketilfellebit1)

            val sykmeldtStatusResponse = SykmeldtStatusResponse(erSykmeldt = true, gradert = false, fom = LocalDate.now(), tom = LocalDate.now())
            val oppfolgingstilfellePerson = OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1), syketilfelledag1, 0, false, LocalDateTime.now())

            coEvery { sykmeldingerConsumer.getSykmeldtStatusPaDato(any(), any()) } returns sykmeldtStatusResponse
            coEvery { syfosyketilfelleConsumer.getOppfolgingstilfelle(any()) } returns oppfolgingstilfellePerson

            val lagreteVarsler1 = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            val nrOfRowsFetchedTotal1 = lagreteVarsler1.size

            nrOfRowsFetchedTotal1 shouldEqual 1

            // Skal lagre varsel
            runBlocking { aktivitetskravVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1) }

            val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            val nrOfRowsFetchedTotal = lagreteVarsler.size

            nrOfRowsFetchedTotal shouldEqual 1
        }

        it("Aktivitetskrav varsel blir lagret i database, ett sykeforløp") {
            val utsendingsdato1 = LocalDate.now().plusDays(2)
            //Gammel varsel
            val planlagtVarselToStore1 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("1"), VarselType.AKTIVITETSKRAV, utsendingsdato1)

            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore1)        //Gammel usendt AKTIVITETSKRAV
            val fom1 = utsendingsdato1.minusDays(AKTIVITETSKRAV_DAGER)
            val tom1 = utsendingsdato1.plusDays(20)

            val syketilfellebit1 = // Tilsvarer den som er allerede lagret
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    listOf("SYKMELDING", "SENDT"),
                    "1",
                    fom1.atStartOfDay(),
                    tom1.atStartOfDay()
                )

            //Ny sykmelding, nytt forlop
            val fom2 = tom1.plusDays(SYKEFORLOP_MIN_DIFF_DAGER + 2L)
            val tom2 = fom2.plusDays(AKTIVITETSKRAV_DAGER + 2L)
            val syketilfellebit2 =
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now().plusDays(1),
                    LocalDateTime.now().plusDays(1),
                    listOf("SYKMELDING", "SENDT"),
                    "2",
                    fom2.atStartOfDay(),
                    tom2.atStartOfDay()
                )

            //Ny sykmelding, samme forlop
            val fom3 = tom2.plusDays(2)
            val tom3 = fom3.plusDays(AKTIVITETSKRAV_DAGER).plusDays(2)

            val syketilfellebit3 =
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now().plusDays(2),
                    LocalDateTime.now().plusDays(2),
                    listOf("SYKMELDING", "SENDT"),
                    "4",
                    fom3.atStartOfDay(),
                    tom3.atStartOfDay()
                )

            val syketilfelledag1 = Syketilfelledag(LocalDate.now(), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.now(), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now(), syketilfellebit3)

            val sykmeldtStatusResponse = SykmeldtStatusResponse(erSykmeldt = true, gradert = false, fom = LocalDate.now(), tom = LocalDate.now())

            coEvery { sykmeldingerConsumer.getSykmeldtStatusPaDato(any(), any()) } returns sykmeldtStatusResponse

            val oppfolgingstilfellePerson2 =
                OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2), syketilfelledag1, 0, false, LocalDateTime.now())

            coEvery { syfosyketilfelleConsumer.getOppfolgingstilfelle(any()) } returns oppfolgingstilfellePerson2
            runBlocking { aktivitetskravVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1) }

            val oppfolgingstilfellePerson3 =
                OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3), syketilfelledag1, 0, false, LocalDateTime.now())

            coEvery { syfosyketilfelleConsumer.getOppfolgingstilfelle(any()) } returns oppfolgingstilfellePerson3
            runBlocking { aktivitetskravVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1) }

            val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            val nrOfRowsFetchedTotal = lagreteVarsler.size
            nrOfRowsFetchedTotal shouldEqual 2

            val aktivitetskravVarsler = lagreteVarsler.filter { it.type == VarselType.AKTIVITETSKRAV.name }

            val varsel1 = planlagtVarselToStore1.utsendingsdato
            val varsel2 = fom2.plusDays(AKTIVITETSKRAV_DAGER)
            val varsel3 = fom3.plusDays(AKTIVITETSKRAV_DAGER)

            aktivitetskravVarsler.size shouldEqual 2
            aktivitetskravVarsler.filter { it.utsendingsdato == varsel1 }.toList().size shouldEqual 1
            aktivitetskravVarsler.filter { it.utsendingsdato == varsel2 }.toList().size shouldEqual 1
            aktivitetskravVarsler.filter { it.utsendingsdato == varsel3 }.toList().size shouldEqual 0
        }

        it("Aktivitetskrav varsel blir lagret i database for hvert beregnet sykeforlop") {
            val utsendingsdato1 = LocalDate.now().plusDays(2)
            //Gammel varsel
            val planlagtVarselToStore1 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("1"), VarselType.AKTIVITETSKRAV, utsendingsdato1)

            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore1)//Gammel usendt AKTIVITETSKRAV

            val fom1 = utsendingsdato1.minusDays(AKTIVITETSKRAV_DAGER)
            val tom1 = utsendingsdato1.plusDays(20)

            val syketilfellebit1 = // Tilsvarer den som er allerede lagret
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    listOf("SYKMELDING", "SENDT"),
                    "1",
                    fom1.atStartOfDay(),
                    tom1.atStartOfDay()
                )

            //Ny sykmelding, nytt forlop
            val fom2 = tom1.plusDays(SYKEFORLOP_MIN_DIFF_DAGER + 2L)
            val tom2 = fom2.plusDays(AKTIVITETSKRAV_DAGER + 2L)
            val syketilfellebit2 =
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now().plusDays(1),
                    LocalDateTime.now().plusDays(1),
                    listOf("SYKMELDING", "SENDT"),
                    "2",
                    fom2.atStartOfDay(),
                    tom2.atStartOfDay()
                )

            //Ny sykmelding, nytt forlop
            val fom3 = tom2.plusDays(SYKEFORLOP_MIN_DIFF_DAGER + 2L)
            val tom3 = fom3.plusDays(AKTIVITETSKRAV_DAGER + 2L)

            val syketilfellebit3 =
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now().plusDays(2),
                    LocalDateTime.now().plusDays(2),
                    listOf("SYKMELDING", "SENDT"),
                    "4",
                    fom3.atStartOfDay(),
                    tom3.atStartOfDay()
                )

            val syketilfelledag1 = Syketilfelledag(LocalDate.now(), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.now(), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now(), syketilfellebit3)

            val sykmeldtStatusResponse = SykmeldtStatusResponse(erSykmeldt = true, gradert = false, fom = LocalDate.now(), tom = LocalDate.now())

            coEvery { sykmeldingerConsumer.getSykmeldtStatusPaDato(any(), any()) } returns sykmeldtStatusResponse

            val oppfolgingstilfellePerson2 =
                OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2), syketilfelledag1, 0, false, LocalDateTime.now())
            coEvery { syfosyketilfelleConsumer.getOppfolgingstilfelle(any()) } returns oppfolgingstilfellePerson2
            runBlocking { aktivitetskravVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1) }

            val oppfolgingstilfellePerson3 =
                OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3), syketilfelledag1, 0, false, LocalDateTime.now())
            coEvery { syfosyketilfelleConsumer.getOppfolgingstilfelle(any()) } returns oppfolgingstilfellePerson3
            runBlocking { aktivitetskravVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1) }

            val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            val nrOfRowsFetchedTotal = lagreteVarsler.size
            nrOfRowsFetchedTotal shouldEqual 3

            val aktivitetskravVarsler = lagreteVarsler.filter { it.type == VarselType.AKTIVITETSKRAV.name }

            val varsel1dato = planlagtVarselToStore1.utsendingsdato
            val varsel2dato = fom2.plusDays(AKTIVITETSKRAV_DAGER)
            val varsel3dato = fom3.plusDays(AKTIVITETSKRAV_DAGER)

            aktivitetskravVarsler.size shouldEqual 3
            aktivitetskravVarsler.filter { it.utsendingsdato == varsel1dato }.toList().size shouldEqual 1
            aktivitetskravVarsler.filter { it.utsendingsdato == varsel2dato }.toList().size shouldEqual 1
            aktivitetskravVarsler.filter { it.utsendingsdato == varsel3dato }.toList().size shouldEqual 1
        }

        it("Aktivitetskrav varsel blir slettet fra database ved nytt sykeforløp hvis sluttdato i ny sykmelding er før sykeforløpets startdato") {
            val utsendingsdato1 = LocalDate.now().plusDays(2)
            //Gammel varsel
            val planlagtVarselToStore1 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("3"), VarselType.AKTIVITETSKRAV, utsendingsdato1)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore1)//Gammel AKTIVITETSKRAV

            embeddedDatabase.fetchAllSykmeldingIdsAndCount()

            val fom1 = utsendingsdato1.minusDays(AKTIVITETSKRAV_DAGER)
            val tom1 = utsendingsdato1.plusDays(20)

            // Tilsvarer varsel som er allerede i DB
            val syketilfellebit1 =
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().minusDays(1),
                    listOf("SYKMELDING", "SENDT"),
                    "3",
                    fom1.atStartOfDay(),
                    tom1.atStartOfDay()
                )

            //Skal være samme forlop, men med tidligere slutt, før varslingdato
            val fom2 = fom1
            val tom2 = fom1.plusDays(7)

            val syketilfellebit2 =
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    listOf("SYKMELDING", "SENDT"),
                    "3",
                    fom2.atStartOfDay(),
                    tom2.atStartOfDay()
                )

            val syketilfelledag1 = Syketilfelledag(LocalDate.now().minusDays(1), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.now(), syketilfellebit2)

            val sykmeldtStatusResponse = SykmeldtStatusResponse(erSykmeldt = true, gradert = false, fom = LocalDate.now(), tom = LocalDate.now())
            val oppfolgingstilfellePerson2 =
                OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2), syketilfelledag2, 0, false, LocalDateTime.now())

            coEvery { syfosyketilfelleConsumer.getOppfolgingstilfelle(any()) } returns oppfolgingstilfellePerson2
            coEvery { sykmeldingerConsumer.getSykmeldtStatusPaDato(any(), any()) } returns sykmeldtStatusResponse

            runBlocking { aktivitetskravVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1) }

            val lagreteVarsler2 = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            val nrOfRowsFetchedTotal2 = lagreteVarsler2.size

            nrOfRowsFetchedTotal2 shouldEqual 0

        }

        it("Aktivitetskrav varsel dato blir beregnet på nytt hvis nyere sykmelding har senere fom dato") {
            val utsendingsdato1 = LocalDate.now().plusDays(2)
            //Gammel varsel
            val planlagtVarselToStore1 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("3"), VarselType.AKTIVITETSKRAV, utsendingsdato1)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore1)//Gammel AKTIVITETSKRAV

            val fom1 = utsendingsdato1.minusDays(AKTIVITETSKRAV_DAGER)
            val tom1 = utsendingsdato1.plusDays(20)

            // Tilsvarer varsel som er allerede i DB
            val syketilfellebit1 =
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().minusDays(1),
                    listOf("SYKMELDING", "SENDT"),
                    "3",
                    fom1.atStartOfDay(),
                    tom1.atStartOfDay()
                )

            //Ny sykmelding. Skal være samme forlop, men med senere fom
            val fom2 = fom1.plusDays(2)
            val tom2 = fom2.plusDays(AKTIVITETSKRAV_DAGER + 2L)
            val utsendingsdato2 = fom2.plusDays(AKTIVITETSKRAV_DAGER)

            val syketilfellebit2 =
                Syketilfellebit(
                    "1",
                    arbeidstakerAktorId1,
                    "2",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    listOf("SYKMELDING", "SENDT"),
                    "2",
                    fom2.atStartOfDay(),
                    tom2.atStartOfDay()
                )

            val syketilfelledag1 = Syketilfelledag(LocalDate.now().minusDays(1), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.now(), syketilfellebit2)

            val sykmeldtStatusResponse = SykmeldtStatusResponse(erSykmeldt = true, gradert = false, fom = LocalDate.now(), tom = LocalDate.now())
            val oppfolgingstilfellePerson2 =
                OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2), syketilfelledag2, 0, false, LocalDateTime.now())

            coEvery { sykmeldingerConsumer.getSykmeldtStatusPaDato(any(), any()) } returns sykmeldtStatusResponse
            coEvery { syfosyketilfelleConsumer.getOppfolgingstilfelle(any()) } returns oppfolgingstilfellePerson2

            runBlocking { aktivitetskravVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1) }

            val lagreteVarsler2 = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)

            lagreteVarsler2.size shouldEqual 1
            lagreteVarsler2[0].utsendingsdato shouldEqual utsendingsdato2
        }

        it("AkvitietskravVarselPlanner kaster RuntimeException dersom syfosyketilfelleConsumer returnerer 'null'") {
            assertFailsWith(RuntimeException::class) {
                coEvery { syfosyketilfelleConsumer.getOppfolgingstilfelle(any()) } returns null
                runBlocking { aktivitetskravVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1) }
            }
        }
    }
})
