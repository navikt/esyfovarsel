package no.nav.syfo.db


import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

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
            val planlagtVarselToStore1 = PPlanlagtVarsel(UUID.randomUUID().toString(), arbeidstakerFnr1, arbeidstakerAktorId1, VarselType.AKTIVITETSKRAV.name, LocalDate.now(), LocalDateTime.now(), LocalDateTime.now())

            embeddedDatabase.storeUtsendtVarsel(planlagtVarselToStore1)

        }
    }
})
