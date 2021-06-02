package no.nav.syfo.varsel

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.common.KafkaEnvironment
import no.nav.syfo.consumer.SykmeldingerConsumer
import no.nav.syfo.consumer.domain.*
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchPlanlagtVarselByFnr
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.kafka.topicOppfolgingsTilfelle
import no.nav.syfo.service.SykmeldingService
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime

const val arbeidstakerFnr1 = "07088621268"
const val arbeidstakerFnr2 = "23456789012"
const val arbeidstakerAktorId1 = "1234567890123"
const val arbeidstakerAktorId2 = "2345678901234"


object AktivitetskravVarselPlannerSpek : Spek({
     val AKTIVITETSKRAV_DAGER: Long = 42;

    //The default timeout of 10 seconds is not sufficient to initialise the embedded database
    defaultTimeout = 20000L

    val embeddedKafkaEnv = KafkaEnvironment(
        topicNames = listOf(topicOppfolgingsTilfelle)
    )
    val embeddedDatabase by lazy { EmbeddedDatabase() }
    val sykmeldingerConsumer = mockk<SykmeldingerConsumer>()

    val aktivitetskravVarselPlanner = AktivitetskravVarselPlanner(embeddedDatabase, SykmeldingService(sykmeldingerConsumer))

    describe("AktivitetskravVarselPlannerSpek") {
        val planlagtVarselToStore2 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, VarselType.MER_VEILEDNING)
        val planlagtVarselToStore3 = PlanlagtVarsel(arbeidstakerFnr2, arbeidstakerAktorId2, VarselType.AKTIVITETSKRAV)

        beforeGroup {
            embeddedKafkaEnv.start()
        }

        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
            embeddedKafkaEnv.tearDown()
        }

        it("Kun sykmeldingtilfeller blir brukt for beregning av varslingdato") {
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            val syketilfellebit1 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("ANNET_FRAVAR", "SENDT"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit2 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("ANNET_FRAVAR", "UTDANNING"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit3 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("ANNET_FRAVAR", "BEKREFTET"), "3", LocalDateTime.now(), LocalDateTime.now())

            val syketilfelledag1 = Syketilfelledag(LocalDate.now().minusDays(4), syketilfellebit1)// start
            val syketilfelledag2 = Syketilfelledag(LocalDate.now().minusDays(2), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now().plusDays(100), syketilfellebit3)// slutt

            val sykmelding = Sykmelding("1", listOf(SykmeldingPeriode("", "", Gradert(100))), SykmeldingStatus())

            coEvery { sykmeldingerConsumer.getSykmeldingerForVarslingDato(any<LocalDate>(), any<String>()) } returns listOf(sykmelding)
            runBlocking {
                val oppfolgingstilfellePerson =
                    OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3), syketilfelledag1, 0, false, LocalDateTime.now())
                aktivitetskravVarselPlanner.processOppfolgingstilfelle(oppfolgingstilfellePerson)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                val nrOfRowsFetchedTotal = lagreteVarsler.size
                nrOfRowsFetchedTotal shouldEqual 1

                lagreteVarsler.filter { it.type == VarselType.AKTIVITETSKRAV.name } shouldEqual listOf()
            }
        }

        it("Beregnet aktivitetskrav varsel dato er før dagensdato") {
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            val syketilfellebit1 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("SYKMELDING", "SENDT"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit2 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("SYKMELDING", "UTDANNING"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit3 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("PAPIRSYKMELDING", "BEKREFTET"), "3", LocalDateTime.now(), LocalDateTime.now())

            val syketilfelledag1 = Syketilfelledag(LocalDate.now().minusDays(60), syketilfellebit1)// start
            val syketilfelledag2 = Syketilfelledag(LocalDate.now().minusDays(2), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now().plusDays(100), syketilfellebit3)// slutt

            val sykmelding = Sykmelding("1", listOf(SykmeldingPeriode("", "", Gradert(100))), SykmeldingStatus())

            coEvery { sykmeldingerConsumer.getSykmeldingerForVarslingDato(any<LocalDate>(), any<String>()) } returns listOf(sykmelding)

            runBlocking {
                val oppfolgingstilfellePerson = OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3), syketilfelledag1, 0, false, LocalDateTime.now())
                aktivitetskravVarselPlanner.processOppfolgingstilfelle(oppfolgingstilfellePerson)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                val nrOfRowsFetchedTotal = lagreteVarsler.size
                nrOfRowsFetchedTotal shouldEqual 1

                lagreteVarsler.filter { it.type == VarselType.AKTIVITETSKRAV.name } shouldEqual listOf()
            }
        }

        it("Beregnet aktivitetskrav varsel dato er etter siste tilfelle dato") {
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            val syketilfellebit1 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("SYKMELDING", "SENDT"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit2 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("SYKMELDING", "UTDANNING"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit3 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("PAPIRSYKMELDING", "BEKREFTET"), "3", LocalDateTime.now(), LocalDateTime.now())

            val syketilfelledag1 = Syketilfelledag(LocalDate.now().minusDays(4), syketilfellebit1)// start
            val syketilfelledag2 = Syketilfelledag(LocalDate.now().minusDays(2), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now().plusDays(6), syketilfellebit3)// slutt

            val sykmelding = Sykmelding("1", listOf(SykmeldingPeriode("", "", Gradert(100))), SykmeldingStatus())

            coEvery { sykmeldingerConsumer.getSykmeldingerForVarslingDato(any(), any()) } returns listOf(sykmelding)
            runBlocking {
                val oppfolgingstilfellePerson =
                    OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3), syketilfelledag1, 0, false, LocalDateTime.now())
                aktivitetskravVarselPlanner.processOppfolgingstilfelle(oppfolgingstilfellePerson)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                val nrOfRowsFetchedTotal = lagreteVarsler.size
                nrOfRowsFetchedTotal shouldEqual 1

                lagreteVarsler.filter { it.type == VarselType.AKTIVITETSKRAV.name } shouldEqual listOf()
            }
        }

        it("Sykmeldingsgrad er < enn 100% på beregnet varslingsdato") {
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            val syketilfellebit1 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("SYKMELDING", "SENDT"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit2 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("SYKMELDING", "UTDANNING"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit3 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("PAPIRSYKMELDING", "BEKREFTET"), "3", LocalDateTime.now(), LocalDateTime.now())

            val syketilfelledag1 = Syketilfelledag(LocalDate.now().minusDays(4), syketilfellebit1)// start
            val syketilfelledag2 = Syketilfelledag(LocalDate.now().minusDays(2), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now().plusDays(100), syketilfellebit3)// slutt

            val sykmelding = Sykmelding("1", listOf(SykmeldingPeriode("", "", Gradert(50))), SykmeldingStatus())
            coEvery { sykmeldingerConsumer.getSykmeldingerForVarslingDato(any(), any()) } returns listOf(sykmelding)

            runBlocking {
                val oppfolgingstilfellePerson =
                    OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3), syketilfelledag1, 0, false, LocalDateTime.now())
                aktivitetskravVarselPlanner.processOppfolgingstilfelle(oppfolgingstilfellePerson)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                val nrOfRowsFetchedTotal = lagreteVarsler.size
                nrOfRowsFetchedTotal shouldEqual 1

                lagreteVarsler.filter { it.type == VarselType.AKTIVITETSKRAV.name } shouldEqual listOf()
            }
        }

        it("Aktivitetskrav varsel er allerede lagret") {
            val planlagtVarselToStore1 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, VarselType.AKTIVITETSKRAV)

            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore1)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            val syketilfellebit1 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("SYKMELDING", "SENDT"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit2 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("ANNET_FRAVAR", "UTDANNING"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit3 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("PAPIRSYKMELDING", "BEKREFTET"), "3", LocalDateTime.now(), LocalDateTime.now())

            val syketilfelledag1 = Syketilfelledag(LocalDate.now().minusDays(4), syketilfellebit1)// start
            val syketilfelledag2 = Syketilfelledag(LocalDate.now().minusDays(2), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now().plusDays(100), syketilfellebit3)// slutt

            val sykmelding = Sykmelding("1", listOf(SykmeldingPeriode("", "", Gradert(100))), SykmeldingStatus())

            coEvery { sykmeldingerConsumer.getSykmeldingerForVarslingDato(any(), any()) } returns listOf(sykmelding)

            val oppfolgingstilfellePerson = OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3), syketilfelledag1, 0, false, LocalDateTime.now())
            runBlocking { aktivitetskravVarselPlanner.processOppfolgingstilfelle(oppfolgingstilfellePerson) }

            val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            val nrOfRowsFetchedTotal = lagreteVarsler.size
            nrOfRowsFetchedTotal shouldEqual 2
            nrOfRowsFetchedTotal shouldNotEqual 3

            lagreteVarsler.filter { it.type == VarselType.AKTIVITETSKRAV.name }.size shouldEqual  1
        }

        it("Aktivitetskrav varsel er allerede sendt ut") {
            val planlagtVarselToStore1 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, VarselType.AKTIVITETSKRAV, LocalDate.now().minusDays(7))

            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore1)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            val syketilfellebit1 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("SYKMELDING", "SENDT"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit2 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("ANNET_FRAVAR", "UTDANNING"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit3 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("PAPIRSYKMELDING", "BEKREFTET"), "3", LocalDateTime.now(), LocalDateTime.now())

            val syketilfelledag1 = Syketilfelledag(LocalDate.now().minusDays(4), syketilfellebit1)// start
            val syketilfelledag2 = Syketilfelledag(LocalDate.now().minusDays(2), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now().plusDays(100), syketilfellebit3)// slutt

            val sykmelding = Sykmelding("1", listOf(SykmeldingPeriode("", "", Gradert(100))), SykmeldingStatus())

            coEvery { sykmeldingerConsumer.getSykmeldingerForVarslingDato(any(), any()) } returns listOf(sykmelding)

            val oppfolgingstilfellePerson = OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3), syketilfelledag1, 0, false, LocalDateTime.now())
            runBlocking { aktivitetskravVarselPlanner.processOppfolgingstilfelle(oppfolgingstilfellePerson) }

            val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            val nrOfRowsFetchedTotal = lagreteVarsler.size
            nrOfRowsFetchedTotal shouldEqual 2
            nrOfRowsFetchedTotal shouldNotEqual 3

            lagreteVarsler.filter { it.type == VarselType.AKTIVITETSKRAV.name }.size shouldEqual  1
        }

        it("AktivitetskravVarsel blir lagret i database") {
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            val syketilfellebit1 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("SYKMELDING", "SENDT"), "3",LocalDateTime.now().minusDays(4), LocalDateTime.now())
            val syketilfellebit2 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("ANNET_FRAVAR", "UTDANNING"), "3", LocalDateTime.now(), LocalDateTime.now())
           //Ny sykmelding, samme forlop
            val syketilfellebit3 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("PAPIRSYKMELDING", "BEKREFTET", "SENDT"), "5", LocalDateTime.now().plusDays(15), LocalDateTime.now().plusDays(100))

            val syketilfelledag1 = Syketilfelledag(LocalDate.now().minusDays(4), syketilfellebit1)// start
            val syketilfelledag2 = Syketilfelledag(LocalDate.now().minusDays(2), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now().plusDays(100), syketilfellebit3)// slutt

            val sykmelding = Sykmelding("1", listOf(SykmeldingPeriode("", "", Gradert(100))), SykmeldingStatus())

            coEvery { sykmeldingerConsumer.getSykmeldingerForVarslingDato(any(), any()) } returns listOf(sykmelding)

            val oppfolgingstilfellePerson = OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3), syketilfelledag1, 0, false, LocalDateTime.now())
            runBlocking { aktivitetskravVarselPlanner.processOppfolgingstilfelle(oppfolgingstilfellePerson) }

            val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            val nrOfRowsFetchedTotal = lagreteVarsler.size
            nrOfRowsFetchedTotal shouldEqual 2

            lagreteVarsler.filter { it.type == VarselType.AKTIVITETSKRAV.name }.size shouldEqual  1
        }

        it("AktivitetskravVarsler blir lagret i database ved nytt sykeforløp selv om det er allerede en varsel i DB som ikke er sendt ut") {
            //Gammelt varsel
            val planlagtVarselToStore1 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, VarselType.AKTIVITETSKRAV, LocalDate.now().plusDays(AKTIVITETSKRAV_DAGER))

            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore1)//Gammel usendt AKTIVITETSKRAV
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            val syketilfellebit1 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("SYKMELDING", "SENDT"), "3", LocalDateTime.now().minusDays(4), LocalDateTime.now())
            val syketilfellebit2 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("ANNET_FRAVAR", "UTDANNING"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit3 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("PAPIRSYKMELDING", "BEKREFTET", "SENDT"), "3", LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(50))
            // Ny sykmelding, nytt forlop
            val syketilfellebit4 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("PAPIRSYKMELDING", "BEKREFTET", "SENDT"), "5", LocalDateTime.now().plusDays(70), LocalDateTime.now().plusDays(140))

            val syketilfelledag1 = Syketilfelledag(LocalDate.now().minusDays(4), syketilfellebit1)// start forlop1
            val syketilfelledag2 = Syketilfelledag(LocalDate.now().minusDays(2), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now().plusDays(50), syketilfellebit3)// slutt forlop1
            val syketilfelledag4 = Syketilfelledag(LocalDate.now().plusDays(140), syketilfellebit4)// slutt forlop1

            val sykmelding = Sykmelding("1", listOf(SykmeldingPeriode("", "", Gradert(100))), SykmeldingStatus())

            coEvery { sykmeldingerConsumer.getSykmeldingerForVarslingDato(any(), any()) } returns listOf(sykmelding)

            val oppfolgingstilfellePerson = OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3, syketilfelledag4), syketilfelledag1, 0, false, LocalDateTime.now())
            runBlocking { aktivitetskravVarselPlanner.processOppfolgingstilfelle(oppfolgingstilfellePerson) }

            val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            val nrOfRowsFetchedTotal = lagreteVarsler.size
            nrOfRowsFetchedTotal shouldEqual 3

            val aktivitetskravVarsler = lagreteVarsler.filter { it.type == VarselType.AKTIVITETSKRAV.name }
            aktivitetskravVarsler.size shouldEqual  2
            aktivitetskravVarsler.filter { it.utsendingsdato == LocalDate.now().plusDays(AKTIVITETSKRAV_DAGER)}.size shouldEqual  1
            aktivitetskravVarsler.filter { it.utsendingsdato == LocalDate.now().plusDays(70).plusDays(AKTIVITETSKRAV_DAGER)}.size shouldEqual  1
        }
    }
})

