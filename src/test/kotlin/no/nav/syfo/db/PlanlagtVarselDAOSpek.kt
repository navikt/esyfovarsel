package no.nav.syfo.db


import io.kotest.core.spec.style.DescribeSpec
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.mocks.orgnummer
import org.amshove.kluent.should
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import java.time.LocalDate


class PlanlagtVarselDAOSpek : DescribeSpec({
    describe("PlanlagtVarselDAOSpek") {

        val embeddedDatabase by lazy { EmbeddedDatabase() }

        afterTest {
            embeddedDatabase.connection.dropData()
        }

        afterSpec {
            embeddedDatabase.stop()
        }

        it("Store and fetch PlanlagtVarsel") {
            val planlagtVarselToStore1 = PlanlagtVarsel(
                arbeidstakerFnr1,
                arbeidstakerAktorId1,
                orgnummer,
                setOf("1", "2"),
                VarselType.MER_VEILEDNING
            )
            val planlagtVarselToStore2 = PlanlagtVarsel(
                arbeidstakerFnr2,
                arbeidstakerAktorId2,
                orgnummer,
                setOf("3"),
                VarselType.MER_VEILEDNING
            )
            val planlagtVarselToStore3 = PlanlagtVarsel(
                arbeidstakerFnr2,
                arbeidstakerAktorId2,
                orgnummer,
                setOf("4"),
                VarselType.MER_VEILEDNING
            )

            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore1)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            val planlagtVarselFetchedList1 = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            val planlagtVarselFetchedList2 = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr2)

            val nrOfRowsFetchedTotal = planlagtVarselFetchedList1.size + planlagtVarselFetchedList2.size
            val fnr1Fetched = planlagtVarselFetchedList1.first().fnr
            val fnr2Fetched = planlagtVarselFetchedList2.first().fnr

            nrOfRowsFetchedTotal shouldBeEqualTo 3
            arbeidstakerFnr1 shouldBeEqualTo fnr1Fetched
            arbeidstakerFnr2 shouldBeEqualTo fnr2Fetched

            embeddedDatabase.fetchAllSykmeldingIdsAndCount() shouldBeEqualTo 4
        }

        it("Delete PlanlagtVarsel by sykmelding ids") {
            val planlagtVarselToStore1 = PlanlagtVarsel(
                arbeidstakerFnr1,
                arbeidstakerAktorId1,
                orgnummer,
                setOf("1", "2"),
                VarselType.MER_VEILEDNING
            )
            val planlagtVarselToStore2 = PlanlagtVarsel(
                arbeidstakerFnr1,
                arbeidstakerAktorId1,
                orgnummer,
                setOf("3"),
                VarselType.MER_VEILEDNING
            )
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore1)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)

            //Før delete
            val planlagtVarselFetchedList1 = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)

            val merVeiledningPlanlagtVarselUuid =
                planlagtVarselFetchedList1.find { it.type == VarselType.MER_VEILEDNING.name }!!.uuid


            planlagtVarselFetchedList1.filter { it.uuid == merVeiledningPlanlagtVarselUuid }.size shouldBeEqualTo 1

            embeddedDatabase.fetchAllSykmeldingIdsAndCount() shouldBeEqualTo 3
            embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(merVeiledningPlanlagtVarselUuid).size shouldNotBe 0

            //Delete
            embeddedDatabase.deletePlanlagtVarselBySykmeldingerId(setOf("1", "2"), VarselType.MER_VEILEDNING)

            //Etter delete
            val planlagtVarselFetchedList1EtterDelete = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
            planlagtVarselFetchedList1EtterDelete.filter { it.uuid == merVeiledningPlanlagtVarselUuid }.size shouldBeEqualTo 0

            embeddedDatabase.fetchAllSykmeldingIdsAndCount() shouldBeEqualTo 1
            embeddedDatabase.fetchSykmeldingerIdByPlanlagtVarselsUUID(merVeiledningPlanlagtVarselUuid).size shouldBeEqualTo 0
        }

        it("Finn PlanlagtVarsel som skal sendes") {
            val planlagtVarselToStore1 = PlanlagtVarsel(
                arbeidstakerFnr1,
                arbeidstakerAktorId1,
                orgnummer,
                setOf("1", "2"),
                VarselType.MER_VEILEDNING,
                LocalDate.now()
            )
            val planlagtVarselToStore2 = PlanlagtVarsel(
                arbeidstakerFnr2,
                arbeidstakerAktorId2,
                orgnummer,
                setOf("3"),
                VarselType.MER_VEILEDNING,
                LocalDate.now().plusDays(1)
            )
            val planlagtVarselToStore3 = PlanlagtVarsel(
                arbeidstakerFnr2,
                arbeidstakerAktorId2,
                orgnummer,
                setOf("4"),
                VarselType.MER_VEILEDNING,
                LocalDate.now().minusDays(1)
            )

            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore1)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore3)

            val varslerSomSkalSendes = embeddedDatabase.fetchPlanlagtVarselByUtsendingsdato(LocalDate.now())

            varslerSomSkalSendes.skalBareHaVarslerMedUtsendingsdatoPa(LocalDate.now())
            varslerSomSkalSendes.skalInneholdeVarsel(planlagtVarselToStore1)
            varslerSomSkalSendes.skalIkkeInneholdeVarsel(planlagtVarselToStore2)
            varslerSomSkalSendes.skalIkkeInneholdeVarsel(planlagtVarselToStore3)
        }
    }
})

private fun Collection<PPlanlagtVarsel>.skalBareHaVarslerMedUtsendingsdatoPa(dato: LocalDate) =
    this.should("Varsel skal ha utsendingsdato nøyaktig på $dato. Varsel $this") {
        all { it.utsendingsdato.isEqual(dato) }
    }

private fun Collection<PPlanlagtVarsel>.skalInneholdeVarsel(expectedVarsel: PlanlagtVarsel) =
    this.should("$this skal inneholde varsel med fnr[${expectedVarsel.fnr}], aktorId[${expectedVarsel.aktorId}], utsendingsdato[${expectedVarsel.utsendingsdato}], type[${expectedVarsel.type}]") {
        any {
            it.fnr == expectedVarsel.fnr &&
                    it.aktorId == expectedVarsel.aktorId &&
                    it.utsendingsdato == expectedVarsel.utsendingsdato &&
                    it.type == expectedVarsel.type.name
        }
    }

private fun Collection<PPlanlagtVarsel>.skalIkkeInneholdeVarsel(expectedVarsel: PlanlagtVarsel) =
    this.should("$this skal ikke inneholde varsel med fnr[${expectedVarsel.fnr}], aktorId[${expectedVarsel.aktorId}], utsendingsdato[${expectedVarsel.utsendingsdato}], type[${expectedVarsel.type}]") {
        !any {
            it.fnr == expectedVarsel.fnr &&
                    it.aktorId == expectedVarsel.aktorId &&
                    it.utsendingsdato == expectedVarsel.utsendingsdato &&
                    it.type == expectedVarsel.type.name
        }
    }
