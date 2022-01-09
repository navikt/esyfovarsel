package no.nav.syfo.job

import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.ToggleEnv
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.UTSENDING_FEILET
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.domain.VarselType.AKTIVITETSKRAV
import no.nav.syfo.db.domain.VarselType.MER_VEILEDNING
import no.nav.syfo.db.fetchPlanlagtVarselByFnr
import no.nav.syfo.db.fetchUtsendtVarselByFnr
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.service.SendVarselService
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.varsel.arbeidstakerAktorId1
import no.nav.syfo.varsel.arbeidstakerFnr1
import org.amshove.kluent.should
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object VarselSenderSpek: Spek( {

    defaultTimeout = 20000L

    val embeddedDatabase by lazy { EmbeddedDatabase() }

    val sendVarselService = mockk<SendVarselService>(relaxed = true)

    describe("SendVarslerJobbSpek") {
        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }


        it("Sender varsler") {
            val sendVarselJobb = VarselSender(embeddedDatabase, sendVarselService, ToggleEnv(true,true,true))
            val planlagtVarselToStore = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("1"), MER_VEILEDNING)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore)

            sendVarselJobb.sendVarsler()
            verify {sendVarselService.sendVarsel(any())}

            embeddedDatabase.skalIkkeHaPlanlagtVarsel(arbeidstakerFnr1, MER_VEILEDNING)
            embeddedDatabase.skalHaUtsendtVarsel(arbeidstakerFnr1, MER_VEILEDNING)
        }

        it("Skal ikke sende mer veiledning-varsel hvis toggle er false") {
            val sendVarselJobb = VarselSender(embeddedDatabase, sendVarselService, ToggleEnv(true,false,true))
            val planlagtVarselToStore = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, emptySet(), MER_VEILEDNING)
            val planlagtVarselToStore2 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("1"), AKTIVITETSKRAV)

            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)

            sendVarselJobb.sendVarsler()
            verify {sendVarselService.sendVarsel(any())}

            embeddedDatabase.skalHaPlanlagtVarsel(arbeidstakerFnr1, MER_VEILEDNING)
            embeddedDatabase.skalIkkeHaUtsendtVarsel(arbeidstakerFnr1, MER_VEILEDNING)

            embeddedDatabase.skalIkkeHaPlanlagtVarsel(arbeidstakerFnr1, AKTIVITETSKRAV)
            embeddedDatabase.skalHaUtsendtVarsel(arbeidstakerFnr1, AKTIVITETSKRAV)
        }

        it("Skal ikke sende aktivitetskrav-varsel hvis toggle er false") {
            val sendVarselJobb = VarselSender(embeddedDatabase, sendVarselService, ToggleEnv(true,true,false))
            val planlagtVarselToStore = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("1"), MER_VEILEDNING)
            val planlagtVarselToStore2 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("1"), AKTIVITETSKRAV)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)

            sendVarselJobb.sendVarsler()
            verify {sendVarselService.sendVarsel(any())}

            embeddedDatabase.skalIkkeHaPlanlagtVarsel(arbeidstakerFnr1, MER_VEILEDNING)
            embeddedDatabase.skalHaUtsendtVarsel(arbeidstakerFnr1, MER_VEILEDNING)

            embeddedDatabase.skalHaPlanlagtVarsel(arbeidstakerFnr1, AKTIVITETSKRAV)
            embeddedDatabase.skalIkkeHaUtsendtVarsel(arbeidstakerFnr1, AKTIVITETSKRAV)
            verify {sendVarselService.sendVarsel(any())}
        }

        it ("Skal ikke markere varsel som sendt dersom utsending feiler"){
            val sendVarselJobb = VarselSender(embeddedDatabase, sendVarselService, ToggleEnv(true,true,true))
            val planlagtVarselToStore = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("1"), MER_VEILEDNING)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore)

            coEvery { sendVarselService.sendVarsel(any()) } returns UTSENDING_FEILET

            sendVarselJobb.sendVarsler()
            verify {sendVarselService.sendVarsel(any())}

            embeddedDatabase.skalHaPlanlagtVarsel(arbeidstakerFnr1, MER_VEILEDNING)
            embeddedDatabase.skalIkkeHaUtsendtVarsel(arbeidstakerFnr1, MER_VEILEDNING)
        }
    }
})

private fun DatabaseInterface.skalHaPlanlagtVarsel(fnr: String, type: VarselType) = this.should("Skal ha planlagt varsel av type $type") {
    this.fetchPlanlagtVarselByFnr(fnr).filter { it.type.equals(type.name) }.isNotEmpty()
}

private fun DatabaseInterface.skalIkkeHaPlanlagtVarsel(fnr: String, type: VarselType) = this.should("Skal ikke ha planlagt varsel av type $type") {
    this.fetchPlanlagtVarselByFnr(fnr).filter { it.type.equals(type.name) }.isEmpty()
}

private fun DatabaseInterface.skalHaUtsendtVarsel(fnr: String, type: VarselType) = this.should("Skal ha utsendt varsel av type $type") {
    this.fetchUtsendtVarselByFnr(fnr).filter { it.type.equals(type.name) }.isNotEmpty()
}

private fun DatabaseInterface.skalIkkeHaUtsendtVarsel(fnr: String, type: VarselType) = this.should("Skal ikke ha utsendt varsel av type $type") {
    this.fetchUtsendtVarselByFnr(fnr).filter { it.type.equals(type.name) }.isEmpty()
}