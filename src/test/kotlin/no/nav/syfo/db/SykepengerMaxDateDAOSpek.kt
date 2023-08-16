package no.nav.syfo.db


import io.kotest.core.spec.style.DescribeSpec
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.utils.REMAINING_DAYS_UNTIL_39_UKERS_VARSEL
import org.amshove.kluent.should
import java.time.LocalDate

class SykepengerMaxDateDAOSpek : DescribeSpec({
    val sykepengerMaxDate = LocalDate.now().plusDays(1)

    describe("SykepengerMaxDateDAOSpek") {
        val embeddedDatabase by lazy { EmbeddedDatabase() }

        afterTest {
            embeddedDatabase.connection.dropData()
        }

        afterSpec {
            embeddedDatabase.stop()
        }


        it("Store max date") {
            embeddedDatabase.storeSykepengerMaxDate(sykepengerMaxDate, arbeidstakerFnr1, "Infotrygd")
            embeddedDatabase.shouldContainMaxDate(arbeidstakerFnr1, sykepengerMaxDate)
        }

        it("Update max date") {
            embeddedDatabase.storeSykepengerMaxDate(sykepengerMaxDate, arbeidstakerFnr1, "Infotrygd")
            embeddedDatabase.shouldContainMaxDate(arbeidstakerFnr1, sykepengerMaxDate)
            embeddedDatabase.updateSykepengerMaxDateByFnr(sykepengerMaxDate.plusDays(1), arbeidstakerFnr1, "Spleis")
            embeddedDatabase.shouldContainMaxDate(arbeidstakerFnr1, sykepengerMaxDate.plusDays(1))
        }

        it("Should return null for non-existing fnr") {
            val nonExistingMaxDate = embeddedDatabase.fetchSykepengerMaxDateByFnr(arbeidstakerFnr2)
            nonExistingMaxDate.should { this == null }
        }

        it("Should retutn row with today's date for sending") {
            val maxDate = LocalDate.now().plusDays(REMAINING_DAYS_UNTIL_39_UKERS_VARSEL)
            embeddedDatabase.storeSykepengerMaxDate(maxDate, arbeidstakerFnr1, "Infotrygd")
            embeddedDatabase.shouldReturnEntryWithSendingDateToday(LocalDate.now())
        }

    }
})

private fun DatabaseInterface.shouldContainMaxDate(fnr: String, maxDate: LocalDate) =
    this.should("Should contain row with requested fnr and maxDate") {
        this.fetchSykepengerMaxDateByFnr(fnr)!! == maxDate
    }

private fun DatabaseInterface.shouldReturnEntryWithSendingDateToday(sendingDate: LocalDate) =
    this.should("Should contain row with requested fnr") {
        this.fetchPlanlagtMerVeiledningVarselByUtsendingsdato(sendingDate)[0].utsendingsdato.isEqual(LocalDate.now())
    }
