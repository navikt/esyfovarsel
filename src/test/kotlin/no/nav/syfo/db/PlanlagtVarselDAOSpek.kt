package no.nav.syfo.db


import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.should
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

const val arbeidstakerFnr1 = "12345678901"
const val arbeidstakerFnr2 = "23456789012"
const val arbeidstakerAktorId1 = "1234567890123"
const val arbeidstakerAktorId2 = "2345678901234"

object PlanlagtVarselDAOSpek : Spek({

    //The default timeout of 10 seconds is not sufficient to initialise the embedded database
    defaultTimeout = 20000L

    describe("PlanlagtVarselDAOSpek") {

        val embeddedDatabase by lazy { EmbeddedDatabase() }

        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }

        it("Store and fetch PlanlagtVarsel") {
            val planlagtVarselToStore1 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("1", "2" ), VarselType.AKTIVITETSKRAV)
            val planlagtVarselToStore2 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("3"), VarselType.MER_VEILEDNING)
            val planlagtVarselToStore3 = PlanlagtVarsel(arbeidstakerFnr2, arbeidstakerAktorId2, setOf("4"), VarselType.AKTIVITETSKRAV)

            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore1)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            val planlagtVarselFetchedList1 = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            val planlagtVarselFetchedList2 = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr2)

            val nrOfRowsFetchedTotal = planlagtVarselFetchedList1.size + planlagtVarselFetchedList2.size
            val fnr1Fetched = planlagtVarselFetchedList1.first().fnr
            val fnr2Fetched = planlagtVarselFetchedList2.first().fnr

            val list1ContainsCorrectVarselTypes = planlagtVarselFetchedList1.map { it.type }.containsAll(listOf(VarselType.AKTIVITETSKRAV.name, VarselType.MER_VEILEDNING.name))
            val list2ContainsCorrectVarselTypes = planlagtVarselFetchedList2.map { it.type }.containsAll(listOf(VarselType.AKTIVITETSKRAV.name))

            nrOfRowsFetchedTotal shouldBeEqualTo 3
            arbeidstakerFnr1 shouldBeEqualTo fnr1Fetched
            arbeidstakerFnr2 shouldBeEqualTo fnr2Fetched
            list1ContainsCorrectVarselTypes shouldBeEqualTo true
            list2ContainsCorrectVarselTypes shouldBeEqualTo true

            val aktivitetskravPlanlagtVarselUuid = planlagtVarselFetchedList1.find { it.type == VarselType.AKTIVITETSKRAV.name }!!.uuid
            val merveiledningPlanlagtVarselUuid = planlagtVarselFetchedList1.find { it.type == VarselType.MER_VEILEDNING.name }!!.uuid

            embeddedDatabase.fetchAllSykmeldingIdsAndCount() shouldBeEqualTo 4
            embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(aktivitetskravPlanlagtVarselUuid).size shouldNotBe 0
            embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(merveiledningPlanlagtVarselUuid).size shouldNotBe 0
        }

        it("Delete PlanlagtVarsel by varsel uuid") {
            val planlagtVarselToStore1 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("1", "2"), VarselType.AKTIVITETSKRAV)
            val planlagtVarselToStore2 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("3"), VarselType.MER_VEILEDNING)
            val planlagtVarselToStore3 = PlanlagtVarsel(arbeidstakerFnr2, arbeidstakerAktorId2, setOf("4"), VarselType.AKTIVITETSKRAV)

            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore1)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            //Før delete
            val planlagtVarselFetchedList1 = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)

            val aktivitetskravPlanlagtVarselUuid = planlagtVarselFetchedList1.find { it.type == VarselType.AKTIVITETSKRAV.name }!!.uuid
            val merveiledningPlanlagtVarselUuid = planlagtVarselFetchedList1.find { it.type == VarselType.MER_VEILEDNING.name }!!.uuid


            planlagtVarselFetchedList1.filter { it.uuid == aktivitetskravPlanlagtVarselUuid }.size shouldBeEqualTo 1
            planlagtVarselFetchedList1.filter { it.uuid == merveiledningPlanlagtVarselUuid }.size shouldBeEqualTo 1

            embeddedDatabase.fetchAllSykmeldingIdsAndCount() shouldBeEqualTo 4
            embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(aktivitetskravPlanlagtVarselUuid).size shouldNotBe 0
            embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(merveiledningPlanlagtVarselUuid).size shouldNotBe 0

            //Delete
            embeddedDatabase.deletePlanlagtVarselByVarselId(aktivitetskravPlanlagtVarselUuid)

            //Etter delete
            val planlagtVarselFetchedList1EtterDelete = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            planlagtVarselFetchedList1EtterDelete.filter { it.uuid == aktivitetskravPlanlagtVarselUuid }.size shouldBeEqualTo 0
            planlagtVarselFetchedList1EtterDelete.filter { it.uuid == merveiledningPlanlagtVarselUuid }.size shouldBeEqualTo 1

            embeddedDatabase.fetchAllSykmeldingIdsAndCount() shouldBeEqualTo 2
            embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(aktivitetskravPlanlagtVarselUuid).size shouldBeEqualTo 0
            embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(merveiledningPlanlagtVarselUuid).size shouldNotBe 0
        }

        it("Delete PlanlagtVarsel by sykmelding ids") {
            val planlagtVarselToStore1 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("1", "2"), VarselType.AKTIVITETSKRAV)
            val planlagtVarselToStore2 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("3"), VarselType.MER_VEILEDNING)
            val planlagtVarselToStore3 = PlanlagtVarsel(arbeidstakerFnr2, arbeidstakerAktorId2, setOf("4"), VarselType.AKTIVITETSKRAV)

            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore1)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            //Før delete
            val planlagtVarselFetchedList1 = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)

            val aktivitetskravPlanlagtVarselUuid = planlagtVarselFetchedList1.find { it.type == VarselType.AKTIVITETSKRAV.name }!!.uuid
            val merveiledningPlanlagtVarselUuid = planlagtVarselFetchedList1.find { it.type == VarselType.MER_VEILEDNING.name }!!.uuid


            planlagtVarselFetchedList1.filter { it.uuid == aktivitetskravPlanlagtVarselUuid }.size shouldBeEqualTo 1
            planlagtVarselFetchedList1.filter { it.uuid == merveiledningPlanlagtVarselUuid }.size shouldBeEqualTo 1

            embeddedDatabase.fetchAllSykmeldingIdsAndCount() shouldBeEqualTo 4
            embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(aktivitetskravPlanlagtVarselUuid).size shouldNotBe 0
            embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(merveiledningPlanlagtVarselUuid).size shouldNotBe 0

            //Delete
            embeddedDatabase.deletePlanlagtVarselBySykmeldingerId(setOf("1", "2"))

            //Etter delete
            val planlagtVarselFetchedList1EtterDelete = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            planlagtVarselFetchedList1EtterDelete.filter { it.uuid == aktivitetskravPlanlagtVarselUuid }.size shouldBeEqualTo 0
            planlagtVarselFetchedList1EtterDelete.filter { it.uuid == merveiledningPlanlagtVarselUuid }.size shouldBeEqualTo 1

            embeddedDatabase.fetchAllSykmeldingIdsAndCount() shouldBeEqualTo 2
            embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(aktivitetskravPlanlagtVarselUuid).size shouldBeEqualTo 0
            embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(merveiledningPlanlagtVarselUuid).size shouldNotBe 0
        }

        it("Finn PlanlagtVarsel som skal sendes") {
            val planlagtVarselToStore1 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("1", "2"), VarselType.AKTIVITETSKRAV, LocalDate.now())
            val planlagtVarselToStore2 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("3"), VarselType.MER_VEILEDNING, LocalDate.now().minusDays(1))
            val planlagtVarselToStore3 = PlanlagtVarsel(arbeidstakerFnr2, arbeidstakerAktorId2, setOf("4"), VarselType.AKTIVITETSKRAV, LocalDate.now().plusDays(1))
            val planlagtVarselToStore4 = PlanlagtVarsel(arbeidstakerFnr2, arbeidstakerAktorId2, setOf("5"), VarselType.MER_VEILEDNING, LocalDate.now().plusWeeks(1))

            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore1)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore4)

            val varslerSomSkalSendes = embeddedDatabase.fetchPlanlagtVarselByUtsendingsdato(LocalDate.now())

            varslerSomSkalSendes.skalBareHaVarslerMedUtsendingsdatoPa(LocalDate.now())
            varslerSomSkalSendes.skalInneholdeVarsel(planlagtVarselToStore1)
            varslerSomSkalSendes.skalIkkeInneholdeVarsel(planlagtVarselToStore2)
            varslerSomSkalSendes.skalIkkeInneholdeVarsel(planlagtVarselToStore3)
            varslerSomSkalSendes.skalIkkeInneholdeVarsel(planlagtVarselToStore4)
        }
    }
})

private fun Collection<PPlanlagtVarsel>.skalBareHaVarslerMedUtsendingsdatoPa(dato: LocalDate) = this.should("Varsel skal ha utsendingsdato nøyaktig på $dato. Varsel $this") {
    all { it.utsendingsdato.isEqual(dato) }
}

private fun Collection<PPlanlagtVarsel>.skalInneholdeVarsel(expectedVarsel: PlanlagtVarsel) = this.should("$this skal inneholde varsel med fnr[${expectedVarsel.fnr}], aktorId[${expectedVarsel.aktorId}], utsendingsdato[${expectedVarsel.utsendingsdato}], type[${expectedVarsel.type}]") {
    any { it.fnr == expectedVarsel.fnr &&
            it.aktorId == expectedVarsel.aktorId &&
            it.utsendingsdato == expectedVarsel.utsendingsdato &&
            it.type == expectedVarsel.type.name }
}

private fun Collection<PPlanlagtVarsel>.skalIkkeInneholdeVarsel(expectedVarsel: PlanlagtVarsel) = this.should("$this skal ikke inneholde varsel med fnr[${expectedVarsel.fnr}], aktorId[${expectedVarsel.aktorId}], utsendingsdato[${expectedVarsel.utsendingsdato}], type[${expectedVarsel.type}]") {
    ! any { it.fnr == expectedVarsel.fnr &&
            it.aktorId == expectedVarsel.aktorId &&
            it.utsendingsdato == expectedVarsel.utsendingsdato &&
            it.type == expectedVarsel.type.name }
}
