package no.nav.syfo.db


import io.kotest.core.spec.style.DescribeSpec
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.domain.VarselType.MER_VEILEDNING
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.mocks.orgnummer
import org.amshove.kluent.shouldMatchAllWith
import org.amshove.kluent.shouldMatchAtLeastOneOf
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.*

class UtsendtVarselDAOSpek : DescribeSpec({
    describe("UtsendtVarselDAOSpek") {

        val embeddedDatabase by lazy { EmbeddedDatabase() }

        afterTest {
            embeddedDatabase.connection.dropData()
        }

        afterSpec {
            embeddedDatabase.stop()
        }

        it("Fetch mer veiledning varsel sent last 3 months") {
            val merVeiledningVarselSentWithin3Months = utsendtVarsel(MER_VEILEDNING, now().minusMonths(3).plusDays(1))
            val merVeiledningVarselSentOutside3Months = utsendtVarsel(MER_VEILEDNING, now().minusMonths(3).minusDays(1))
            embeddedDatabase.storeUtsendtVarsel(merVeiledningVarselSentWithin3Months)
            embeddedDatabase.storeUtsendtVarsel(merVeiledningVarselSentOutside3Months)

            val utsendteMerVeiledningVarslerSiste3Maneder =
                embeddedDatabase.fetchUtsendteMerVeiledningVarslerSiste3Maneder()
            utsendteMerVeiledningVarslerSiste3Maneder.skalInneholde(merVeiledningVarselSentWithin3Months.uuid)
            utsendteMerVeiledningVarslerSiste3Maneder.skalIkkeInneholde(merVeiledningVarselSentOutside3Months.uuid)
        }
    }
})

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

private fun Collection<PUtsendtVarsel>.skalInneholde(uuid: String) {
    this.shouldMatchAtLeastOneOf { pUtsendtVarsel: PUtsendtVarsel -> pUtsendtVarsel.uuid == uuid }
}

private fun Collection<PUtsendtVarsel>.skalIkkeInneholde(uuid: String) {
    this.shouldMatchAllWith { pUtsendtVarsel: PUtsendtVarsel -> pUtsendtVarsel.uuid != uuid }

}
