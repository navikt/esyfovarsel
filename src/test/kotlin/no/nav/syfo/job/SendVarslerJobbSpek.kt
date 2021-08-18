package no.nav.syfo.job

import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.varsel.VarselSender
import no.nav.syfo.varsel.arbeidstakerAktorId1
import no.nav.syfo.varsel.arbeidstakerFnr1
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object SendVarslerJobbSpek: Spek( {

    val embeddedDatabase by lazy { EmbeddedDatabase() }
    val varselSender = mockk<VarselSender>(relaxed = true)
    val sendVarselJobb = SendVarslerJobb(embeddedDatabase, varselSender)

    describe("SendVarslerJobbSpek") {
        it("Sender varsler") {
            val planlagtVarselToStore = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("1"), VarselType.MER_VEILEDNING)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore)
            sendVarselJobb.sendVarsler()
            verify {varselSender.send(any())}
        }
    }
})
