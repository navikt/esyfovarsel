package no.nav.syfo.job

import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchPlanlagtVarselByFnr
import no.nav.syfo.db.fetchUtsendtVarselByFnr
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.varsel.VarselSender
import no.nav.syfo.varsel.arbeidstakerAktorId1
import no.nav.syfo.varsel.arbeidstakerFnr1
import org.amshove.kluent.should
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object SendVarslerJobbSpek: Spek( {

    val embeddedDatabase by lazy { EmbeddedDatabase() }
    val varselSender = mockk<VarselSender>(relaxed = true)

    describe("SendVarslerJobbSpek") {
        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }


        it("Sender varsler") {
            val sendVarselJobb = SendVarslerJobb(embeddedDatabase, varselSender, true)
            val planlagtVarselToStore = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("1"), VarselType.MER_VEILEDNING)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore)
            sendVarselJobb.sendVarsler()
            verify {varselSender.send(any())}
            embeddedDatabase.skalIkkeHaPlanlagtVarsel(arbeidstakerFnr1)
            embeddedDatabase.skalHaUtsendtVarsel(arbeidstakerFnr1)
        }

        it("Skal ikke markere som sendt hvis toggle er false") {
            val sendVarselJobb = SendVarslerJobb(embeddedDatabase, varselSender, false)
            val planlagtVarselToStore = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("1"), VarselType.AKTIVITETSKRAV)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore)
            sendVarselJobb.sendVarsler()
            verify {varselSender.send(any())}
            embeddedDatabase.skalHaPlanlagtVarsel(arbeidstakerFnr1)
            embeddedDatabase.skalIkkeHaUtsendtVarsel(arbeidstakerFnr1)
        }
    }
})

private fun DatabaseInterface.skalHaPlanlagtVarsel(fnr: String) = this.should("Skal ha planlagt varsel") {
    this.fetchPlanlagtVarselByFnr(fnr).isNotEmpty()
}


private fun DatabaseInterface.skalIkkeHaPlanlagtVarsel(fnr: String) = this.should("Skal ikke ha planlagt varsel") {
    this.fetchPlanlagtVarselByFnr(fnr).isEmpty()
}

private fun DatabaseInterface.skalHaUtsendtVarsel(fnr: String) = this.should("Skal ha utsendt varsel") {
    this.fetchUtsendtVarselByFnr(fnr).isNotEmpty()
}

private fun DatabaseInterface.skalIkkeHaUtsendtVarsel(fnr: String) = this.should("Skal ikke ha utsendt varsel") {
    this.fetchUtsendtVarselByFnr(fnr).isEmpty()
}