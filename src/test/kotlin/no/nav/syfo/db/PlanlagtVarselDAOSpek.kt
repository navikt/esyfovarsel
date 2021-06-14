package no.nav.syfo.db


import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.shouldEqual
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
            val planlagtVarselToStore1 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, listOf("1, 2"), VarselType.AKTIVITETSKRAV)
            val planlagtVarselToStore2 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, listOf("3"), VarselType.MER_VEILEDNING)
            val planlagtVarselToStore3 = PlanlagtVarsel(arbeidstakerFnr2, arbeidstakerAktorId2, listOf("4"), VarselType.AKTIVITETSKRAV)

            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore1)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            val planlagtVarselFetchedList1 = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            val planlagtVarselFetchedList2 = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr2)

            val uuid = planlagtVarselFetchedList1.find { it.type == VarselType.AKTIVITETSKRAV.name }?.uuid

            val sykmeldinger = uuid?.let { embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(it) }

            sykmeldinger?.size shouldEqual 1
            sykmeldinger?.get(0)?.get(uuid)?.size shouldEqual 2

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
        }

        it("Delete PlanlagtVarsel") {
            val planlagtVarselToStore1 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, listOf("1, 2"), VarselType.AKTIVITETSKRAV)
            val planlagtVarselToStore2 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, listOf("3"), VarselType.MER_VEILEDNING)
            val planlagtVarselToStore3 = PlanlagtVarsel(arbeidstakerFnr2, arbeidstakerAktorId2, listOf("4"), VarselType.AKTIVITETSKRAV)

            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore1)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            val planlagtVarselFetchedList1 = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            val planlagtVarselFetchedList2 = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr2)

            val uuid = planlagtVarselFetchedList1.find { it.type == VarselType.AKTIVITETSKRAV.name }?.uuid

            val sykmeldinger = uuid?.let { embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(it) }

            //Før delete
            sykmeldinger?.size shouldEqual 1
            sykmeldinger?.get(0)?.get(uuid)?.size shouldEqual 2

            uuid?.let { embeddedDatabase.deletePlanlagtVarsel(it) }

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

            // Etter delete
            val sykmeldingerEtterDelete = uuid?.let { embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(it) }
            sykmeldingerEtterDelete?.size shouldEqual 0

            val planlagtVarselFetchedList1EtterDelete = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            val planlagtVarselFetchedList2EtterDelete = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr2)

            val nrOfRowsFetchedTotalEtterDelete = planlagtVarselFetchedList1EtterDelete.size + planlagtVarselFetchedList2EtterDelete.size
            val fnr1FetchedEtterDelete = planlagtVarselFetchedList1EtterDelete.first().fnr
            val fnr2FetchedEtterDelete = planlagtVarselFetchedList2EtterDelete.first().fnr

            val list1ContainsCorrectVarselTypesEtterDelete = planlagtVarselFetchedList1EtterDelete.map { it.type }.containsAll(listOf(VarselType.AKTIVITETSKRAV.name, VarselType.MER_VEILEDNING.name))
            val list2ContainsCorrectVarselTypesEtterDelete = planlagtVarselFetchedList2EtterDelete.map { it.type }.containsAll(listOf(VarselType.AKTIVITETSKRAV.name))

            nrOfRowsFetchedTotalEtterDelete shouldEqual 2
            arbeidstakerFnr1 shouldEqual fnr1FetchedEtterDelete
            arbeidstakerFnr2 shouldEqual fnr2FetchedEtterDelete
            list1ContainsCorrectVarselTypesEtterDelete shouldEqual false
            list2ContainsCorrectVarselTypesEtterDelete shouldEqual true
        }
    }
})
