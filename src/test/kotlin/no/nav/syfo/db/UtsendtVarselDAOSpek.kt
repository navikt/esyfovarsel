package no.nav.syfo.db


import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.domain.VarselType.AKTIVITETSKRAV
import no.nav.syfo.db.domain.VarselType.MER_VEILEDNING
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.mocks.orgnummer
import org.amshove.kluent.should
import org.amshove.kluent.shouldMatchAllWith
import org.amshove.kluent.shouldMatchAtLeastOneOf
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.*

object UtsendtVarselDAOSpek : Spek({

    // The default timeout of 10 seconds is not sufficient to initialise the embedded database
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
            val aktivitetskravVarsel = planlagtVarsel(AKTIVITETSKRAV)
            embeddedDatabase.storeUtsendtVarsel(aktivitetskravVarsel)
            embeddedDatabase.skalHaUtsendtVarsel(arbeidstakerFnr1, AKTIVITETSKRAV, aktivitetskravVarsel.uuid)
        }

        it("Fetch mer veiledning varsel sent last 3 months") {
            val aktivitetskravVarsel = utsendtVarsel(AKTIVITETSKRAV, now())
            val merVeiledningVarselSentWithin3Months = utsendtVarsel(MER_VEILEDNING, now().minusMonths(3).plusDays(1))
            val merVeiledningVarselSentOutside3Months = utsendtVarsel(MER_VEILEDNING, now().minusMonths(3).minusDays(1))
            embeddedDatabase.storeUtsendtVarsel(aktivitetskravVarsel)
            embeddedDatabase.storeUtsendtVarsel(merVeiledningVarselSentWithin3Months)
            embeddedDatabase.storeUtsendtVarsel(merVeiledningVarselSentOutside3Months)

            val utsendteMerVeiledningVarslerSiste3Maneder = embeddedDatabase.fetchUtsendteMerVeiledningVarslerSiste3Maneder()
            utsendteMerVeiledningVarslerSiste3Maneder.skalInneholde(merVeiledningVarselSentWithin3Months.uuid)
            utsendteMerVeiledningVarslerSiste3Maneder.skalIkkeInneholde(merVeiledningVarselSentOutside3Months.uuid)
            utsendteMerVeiledningVarslerSiste3Maneder.skalIkkeInneholde(aktivitetskravVarsel.uuid)
        }
    }
})

private fun planlagtVarsel(type: VarselType) = PPlanlagtVarsel(
    UUID.randomUUID().toString(),
    arbeidstakerFnr1,
    arbeidstakerAktorId1,
    orgnummer,
    type.name,
    LocalDate.now(),
    now(),
    now()
)

private fun utsendtVarsel(type: VarselType, utsendtTidspunkt: LocalDateTime) = PUtsendtVarsel(
    UUID.randomUUID().toString(),
    arbeidstakerFnr1,
    arbeidstakerAktorId1,
    "11111111111",
    orgnummer,
    type.name,
    "kanal",
    utsendtTidspunkt,
    "000",
    "000",
    null,
    null
)

private fun DatabaseInterface.skalHaUtsendtVarsel(fnr: String, type: VarselType, planlagtVarselId: String) =
    this.should("Skal ha utsendt varsel av type $type og planlagtVarselId $planlagtVarselId") {
        this.fetchUtsendtVarselByFnr(fnr)
            .filter { it.type.equals(type.name) }
            .filter { it.planlagtVarselId.equals(planlagtVarselId) }
            .isNotEmpty()
    }

private fun Collection<PUtsendtVarsel>.skalInneholde(uuid: String) {
    this.shouldMatchAtLeastOneOf { pUtsendtVarsel: PUtsendtVarsel -> pUtsendtVarsel.uuid == uuid }
}

private fun Collection<PUtsendtVarsel>.skalIkkeInneholde(uuid: String) {
    this.shouldMatchAllWith { pUtsendtVarsel: PUtsendtVarsel -> pUtsendtVarsel.uuid != uuid }

}
