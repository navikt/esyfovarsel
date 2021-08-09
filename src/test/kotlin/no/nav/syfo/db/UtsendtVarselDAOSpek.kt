package no.nav.syfo.db


import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object UtsendtVarselDAOSpek : Spek({

    //The default timeout of 10 seconds is not sufficient to initialise the embedded database
    defaultTimeout = 20000L

    describe("UtsendtVarselDAOSpek") {

        val embeddedDatabase by lazy { EmbeddedDatabase() }

        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }

        it("Store utsendt varsel") {
            val planlagtVarselToStore1 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("1", "2" ), VarselType.AKTIVITETSKRAV)
            val planlagtVarselToStore2 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, setOf("3"), VarselType.MER_VEILEDNING)
            val planlagtVarselToStore3 = PlanlagtVarsel(arbeidstakerFnr2, arbeidstakerAktorId2, setOf("4"), VarselType.AKTIVITETSKRAV)

            embeddedDatabase.storeUtsendtVarsel(planlagtVarselToStore1)
            embeddedDatabase.storeUtsendtVarsel(planlagtVarselToStore2)
            embeddedDatabase.storeUtsendtVarsel(planlagtVarselToStore3)

        }
    }
})
