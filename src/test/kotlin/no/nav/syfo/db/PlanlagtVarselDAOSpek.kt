package no.nav.syfo.db


import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

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
            val planlagtVarselToStore1 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, listOf("1", "2" ), VarselType.AKTIVITETSKRAV)
            val planlagtVarselToStore2 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, listOf("3"), VarselType.MER_VEILEDNING)
            val planlagtVarselToStore3 = PlanlagtVarsel(arbeidstakerFnr2, arbeidstakerAktorId2, listOf("4"), VarselType.AKTIVITETSKRAV)

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

            nrOfRowsFetchedTotal shouldEqual 3
            arbeidstakerFnr1 shouldEqual fnr1Fetched
            arbeidstakerFnr2 shouldEqual fnr2Fetched
            list1ContainsCorrectVarselTypes shouldEqual true
            list2ContainsCorrectVarselTypes shouldEqual true

            val aktivitetskravPlanlagtVarselUuid = planlagtVarselFetchedList1.find { it.type == VarselType.AKTIVITETSKRAV.name }!!.uuid
            val merveiledningPlanlagtVarselUuid = planlagtVarselFetchedList1.find { it.type == VarselType.MER_VEILEDNING.name }!!.uuid

            embeddedDatabase.fetchAllSykmeldingIdsAndCount().size shouldEqual 4
            embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(aktivitetskravPlanlagtVarselUuid).size shouldNotBe 0
            embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(merveiledningPlanlagtVarselUuid).size shouldNotBe 0
        }

        it("Delete PlanlagtVarsel by varsel uuid") {
            val planlagtVarselToStore1 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, listOf("1", " 2"), VarselType.AKTIVITETSKRAV)
            val planlagtVarselToStore2 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, listOf("3"), VarselType.MER_VEILEDNING)
            val planlagtVarselToStore3 = PlanlagtVarsel(arbeidstakerFnr2, arbeidstakerAktorId2, listOf("4"), VarselType.AKTIVITETSKRAV)

            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore1)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            //Før delete
            val planlagtVarselFetchedList1 = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)

            val aktivitetskravPlanlagtVarselUuid = planlagtVarselFetchedList1.find { it.type == VarselType.AKTIVITETSKRAV.name }!!.uuid
            val merveiledningPlanlagtVarselUuid = planlagtVarselFetchedList1.find { it.type == VarselType.MER_VEILEDNING.name }!!.uuid


            planlagtVarselFetchedList1.filter { it.uuid == aktivitetskravPlanlagtVarselUuid }.size shouldEqual 1
            planlagtVarselFetchedList1.filter { it.uuid == merveiledningPlanlagtVarselUuid }.size shouldEqual 1

            embeddedDatabase.fetchAllSykmeldingIdsAndCount().size shouldEqual 4
            embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(aktivitetskravPlanlagtVarselUuid).size shouldNotBe 0
            embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(merveiledningPlanlagtVarselUuid).size shouldNotBe 0

            //Delete
            embeddedDatabase.deletePlanlagtVarselByVarselId(aktivitetskravPlanlagtVarselUuid)

            //Etter delete
            val planlagtVarselFetchedList1EtterDelete = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            planlagtVarselFetchedList1EtterDelete.filter { it.uuid == aktivitetskravPlanlagtVarselUuid }.size shouldEqual 0
            planlagtVarselFetchedList1EtterDelete.filter { it.uuid == merveiledningPlanlagtVarselUuid }.size shouldEqual 1

            embeddedDatabase.fetchAllSykmeldingIdsAndCount().size shouldEqual 2
            embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(aktivitetskravPlanlagtVarselUuid).size shouldEqual 0
            embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(merveiledningPlanlagtVarselUuid).size shouldNotBe 0
        }

        it("Delete PlanlagtVarsel by sykmelding ids") {
            val planlagtVarselToStore1 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, listOf("1", " 2"), VarselType.AKTIVITETSKRAV)
            val planlagtVarselToStore2 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, listOf("3"), VarselType.MER_VEILEDNING)
            val planlagtVarselToStore3 = PlanlagtVarsel(arbeidstakerFnr2, arbeidstakerAktorId2, listOf("4"), VarselType.AKTIVITETSKRAV)

            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore1)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            //Før delete
            val planlagtVarselFetchedList1 = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)

            val aktivitetskravPlanlagtVarselUuid = planlagtVarselFetchedList1.find { it.type == VarselType.AKTIVITETSKRAV.name }!!.uuid
            val merveiledningPlanlagtVarselUuid = planlagtVarselFetchedList1.find { it.type == VarselType.MER_VEILEDNING.name }!!.uuid


            planlagtVarselFetchedList1.filter { it.uuid == aktivitetskravPlanlagtVarselUuid }.size shouldEqual 1
            planlagtVarselFetchedList1.filter { it.uuid == merveiledningPlanlagtVarselUuid }.size shouldEqual 1

            embeddedDatabase.fetchAllSykmeldingIdsAndCount().size shouldEqual 4
            embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(aktivitetskravPlanlagtVarselUuid).size shouldNotBe 0
            embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(merveiledningPlanlagtVarselUuid).size shouldNotBe 0

            //Delete
            embeddedDatabase.deletePlanlagtVarselBySykmeldingerId(listOf("1", " 2"))

            //Etter delete
            val planlagtVarselFetchedList1EtterDelete = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            planlagtVarselFetchedList1EtterDelete.filter { it.uuid == aktivitetskravPlanlagtVarselUuid }.size shouldEqual 0
            planlagtVarselFetchedList1EtterDelete.filter { it.uuid == merveiledningPlanlagtVarselUuid }.size shouldEqual 1

            embeddedDatabase.fetchAllSykmeldingIdsAndCount().size shouldEqual 2
            embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(aktivitetskravPlanlagtVarselUuid).size shouldEqual 0
            embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(merveiledningPlanlagtVarselUuid).size shouldNotBe 0
        }
    }
})
