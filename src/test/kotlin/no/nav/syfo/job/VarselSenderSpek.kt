package no.nav.syfo.job

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ToggleEnv
import no.nav.syfo.db.*
import no.nav.syfo.db.domain.*
import no.nav.syfo.db.domain.VarselType.MER_VEILEDNING
import no.nav.syfo.db.domain.VarselType.AKTIVITETSKRAV
import no.nav.syfo.planner.arbeidstakerFnr1
import no.nav.syfo.service.SendVarselService
import no.nav.syfo.service.SykepengerMaxDateSource
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.mocks.orgnummer
import no.nav.syfo.utils.REMAINING_DAYS_UNTIL_39_UKERS_VARSEL
import org.amshove.kluent.should
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

object VarselSenderSpek : Spek({

    defaultTimeout = 20000L

    val embeddedDatabase by lazy { EmbeddedDatabase() }

    val sendVarselService = mockk<SendVarselService>(relaxed = true)

    describe("VarselSenderSpek") {
        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }

        it("Sender varsler") {
            val sendVarselJobb = VarselSender(embeddedDatabase, sendVarselService, ToggleEnv(true, false, true, false, false))

            val planlagtVarselToStore = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, orgnummer, setOf("1"), MER_VEILEDNING)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore)

            sendVarselJobb.testSendVarsler()
            sendVarselService.testSendVarsel()

            embeddedDatabase.skalIkkeHaPlanlagtVarsel(arbeidstakerFnr1, MER_VEILEDNING)
            embeddedDatabase.skalHaUtsendtVarsel(arbeidstakerFnr1, MER_VEILEDNING)
        }

        it("Sender mer veiledning varsler basert på maksdato") {
            val oldPlanlagtVarselToStore = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, "999888999", setOf("1"), MER_VEILEDNING)
            embeddedDatabase.storePlanlagtVarsel(oldPlanlagtVarselToStore)
            val oldPPlanlagtVarsel = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)[0]

            val sendVarselJobb = VarselSender(embeddedDatabase, sendVarselService, ToggleEnv(false, true, false, true, true))

            val maxDate = LocalDate.now().plusDays(REMAINING_DAYS_UNTIL_39_UKERS_VARSEL)
            embeddedDatabase.storeSykepengerMaxDate(maxDate, arbeidstakerFnr1, SykepengerMaxDateSource.INFOTRYGD.name)
            val newPPlanlagtVarsel = embeddedDatabase.fetchPlanlagtMerVeiledningVarselByUtsendingsdato(LocalDate.now())[0]

            sendVarselJobb.testSendVarsler()
            verify(exactly = 0) {
                runBlocking {
                    sendVarselService.sendVarsel(oldPPlanlagtVarsel)
                }
            }

            verify(exactly = 1) {
                runBlocking {
                    sendVarselService.sendVarsel(newPPlanlagtVarsel)
                }
            }

            embeddedDatabase.skalHaUtsendtVarsel(arbeidstakerFnr1, MER_VEILEDNING)
            embeddedDatabase.skalIkkeHaPlanlagtVarsel(arbeidstakerFnr1, MER_VEILEDNING)
        }


        it("Skal ikke sende mer veiledning-varsel hvis toggle er false") {
            val sendVarselJobb = VarselSender(embeddedDatabase, sendVarselService, ToggleEnv(false, false, true, false, false))
            val planlagtVarselToStore = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, orgnummer, emptySet(), MER_VEILEDNING)
            val planlagtVarselToStore2 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, orgnummer, setOf("1"), AKTIVITETSKRAV)

            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)

            sendVarselJobb.testSendVarsler()
            sendVarselService.testSendVarsel()

            embeddedDatabase.skalHaPlanlagtVarsel(arbeidstakerFnr1, MER_VEILEDNING)
            embeddedDatabase.skalIkkeHaUtsendtVarsel(arbeidstakerFnr1, MER_VEILEDNING)

            embeddedDatabase.skalIkkeHaPlanlagtVarsel(arbeidstakerFnr1, AKTIVITETSKRAV)
            embeddedDatabase.skalHaUtsendtVarsel(arbeidstakerFnr1, AKTIVITETSKRAV)
        }

        it("Skal ikke sende aktivitetskrav-varsel hvis toggle er false") {
            val sendVarselJobb = VarselSender(embeddedDatabase, sendVarselService, ToggleEnv(true, false, false, false, false))
            val planlagtVarselToStore = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, orgnummer, setOf("1"), MER_VEILEDNING)
            val planlagtVarselToStore2 = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, orgnummer, setOf("1"), AKTIVITETSKRAV)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore2)

            sendVarselJobb.testSendVarsler()
            sendVarselService.testSendVarsel()

            embeddedDatabase.skalIkkeHaPlanlagtVarsel(arbeidstakerFnr1, MER_VEILEDNING)
            embeddedDatabase.skalHaUtsendtVarsel(arbeidstakerFnr1, MER_VEILEDNING)

            embeddedDatabase.skalHaPlanlagtVarsel(arbeidstakerFnr1, AKTIVITETSKRAV)
            embeddedDatabase.skalIkkeHaUtsendtVarsel(arbeidstakerFnr1, AKTIVITETSKRAV)

            sendVarselService.testSendVarsel()
        }

        it("Skal ikke markere varsel som sendt dersom utsending feiler") {
            val sendVarselJobb = VarselSender(embeddedDatabase, sendVarselService, ToggleEnv(true, false, true, false, false))
            val planlagtVarselToStore = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, orgnummer, setOf("1"), MER_VEILEDNING)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore)

            coEvery { sendVarselService.sendVarsel(any()) } returns UTSENDING_FEILET

            sendVarselJobb.testSendVarsler()
            sendVarselService.testSendVarsel()

            embeddedDatabase.skalHaPlanlagtVarsel(arbeidstakerFnr1, MER_VEILEDNING)
            embeddedDatabase.skalIkkeHaUtsendtVarsel(arbeidstakerFnr1, MER_VEILEDNING)
        }

        it("Sender ikke mer veiledning varsler basert på maksdato i førtid innen 31 dager hvis den var sendt siste måned") {
            val sendVarselJobb = VarselSender(embeddedDatabase, sendVarselService, ToggleEnv(false, true, false, true, true))
            val oldPlanlagtVarselToStore = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, orgnummer, setOf("1"), MER_VEILEDNING, LocalDate.now().minusDays(14))
            val utsendtVarselInnen31DagerToStore = PUtsendtVarsel(
                UUID.randomUUID().toString(),
                arbeidstakerFnr1,
                arbeidstakerAktorId1,
                "11111111111",
                orgnummer,
                MER_VEILEDNING.name,
                "kanal",
                LocalDateTime.now().minusDays(10),
                "000",
                "000"
            )
            val maxDate = LocalDate.now().minusDays(14).plusDays(REMAINING_DAYS_UNTIL_39_UKERS_VARSEL)

            embeddedDatabase.storePlanlagtVarsel(oldPlanlagtVarselToStore)
            embeddedDatabase.storeUtsendtVarsel(utsendtVarselInnen31DagerToStore)
            embeddedDatabase.storeSykepengerMaxDate(maxDate, arbeidstakerFnr1, SykepengerMaxDateSource.INFOTRYGD.name)

            val allUnsendMerveiledning = sendVarselJobb.testGetAllUnsendMerveiledning()
            allUnsendMerveiledning.size shouldBeEqualTo 0

            val merVeiledningVarselBasedOnMaxDate = embeddedDatabase.fetchPlanlagtMerVeiledningVarselBySendingDateSisteManed()[0]
            val merVeiledningVarselNotBasedOnMaxDate =
                embeddedDatabase.fetchPlanlagtVarselByTypeAndUtsendingsdato(MER_VEILEDNING, LocalDate.now().minusDays(14), LocalDate.now().minusDays(14))[0]

            sendVarselJobb.testSendVarsler()
            coVerify(exactly = 0) { sendVarselService.sendVarsel(merVeiledningVarselBasedOnMaxDate) }
            coVerify(exactly = 0) { sendVarselService.sendVarsel(merVeiledningVarselNotBasedOnMaxDate) }
        }

        it("Sender mer veiledning varsler basert på maksdato i førtid innen 31 dager hvis den ikke var sendt siste måned") {
            val sendVarselJobb = VarselSender(embeddedDatabase, sendVarselService, ToggleEnv(false, true, false, true, true))
            val oldPlanlagtVarselToStore = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, orgnummer, setOf("1"), MER_VEILEDNING, LocalDate.now().minusDays(14))
            val utsendtVarselInnen31DagerToStore = PUtsendtVarsel(
                UUID.randomUUID().toString(),
                arbeidstakerFnr1,
                arbeidstakerAktorId1,
                "11111111111",
                orgnummer,
                MER_VEILEDNING.name,
                "kanal",
                LocalDateTime.now().minusDays(100),
                "000",
                "000"
            )
            val maxDate = LocalDate.now().minusDays(14).plusDays(REMAINING_DAYS_UNTIL_39_UKERS_VARSEL)

            embeddedDatabase.storePlanlagtVarsel(oldPlanlagtVarselToStore)
            embeddedDatabase.storeUtsendtVarsel(utsendtVarselInnen31DagerToStore)
            embeddedDatabase.storeSykepengerMaxDate(maxDate, arbeidstakerFnr1, SykepengerMaxDateSource.INFOTRYGD.name)
            val merVeiledningVarselBasedOnMaxDate = embeddedDatabase.fetchPlanlagtMerVeiledningVarselBySendingDateSisteManed()[0]
            val merVeiledningVarselNotBasedOnMaxDate =
                embeddedDatabase.fetchPlanlagtVarselByTypeAndUtsendingsdato(MER_VEILEDNING, LocalDate.now().minusDays(14), LocalDate.now().minusDays(14))[0]

            val unsentVarsler = sendVarselJobb.testGetAllUnsendMerveiledning()
            unsentVarsler.size shouldBeEqualTo 1

            sendVarselJobb.testSendVarsler()
            coVerify(exactly = 1) { sendVarselService.sendVarsel(merVeiledningVarselBasedOnMaxDate) }
            coVerify(exactly = 0) { sendVarselService.sendVarsel(merVeiledningVarselNotBasedOnMaxDate) }
        }

        it("Sender mer veiledning varsler basert på maksdato i framtid og ikke planlagt på gammel måte") {
            val utsendingDate = LocalDate.now()
            val maxDate = utsendingDate.plusDays(REMAINING_DAYS_UNTIL_39_UKERS_VARSEL)
            val sendVarselJobb = VarselSender(embeddedDatabase, sendVarselService, ToggleEnv(false, true, false, true, true))
            val oldPlanlagtVarselToStore = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, orgnummer, setOf("1"), MER_VEILEDNING, utsendingDate)

            embeddedDatabase.storePlanlagtVarsel(oldPlanlagtVarselToStore)
            embeddedDatabase.storeSykepengerMaxDate(maxDate, arbeidstakerFnr1, SykepengerMaxDateSource.INFOTRYGD.name)

            val merVeiledningVarselBasedOnMaxDate = embeddedDatabase.fetchPlanlagtMerVeiledningVarselByUtsendingsdato(utsendingDate)[0]
            val merVeiledningVarselNotBasedOnMaxDate =
                embeddedDatabase.fetchPlanlagtVarselByTypeAndUtsendingsdato(MER_VEILEDNING, utsendingDate, utsendingDate)[0]

            sendVarselJobb.testSendVarsler()

            merVeiledningVarselBasedOnMaxDate.utsendingsdato shouldBeEqualTo merVeiledningVarselNotBasedOnMaxDate.utsendingsdato
            coVerify(exactly = 1) { sendVarselService.sendVarsel(merVeiledningVarselBasedOnMaxDate) }
            coVerify(exactly = 0) { sendVarselService.sendVarsel(merVeiledningVarselNotBasedOnMaxDate) }
        }
    }
})

private fun VarselSender.testGetAllUnsendMerveiledning(): List<PPlanlagtVarsel> {
    return runBlocking { getAllUnsendMerVeiledningVarslerLastMonth() }
}

private fun VarselSender.testSendVarsler() {
    runBlocking {
        sendVarsler()
    }
}

private fun SendVarselService.testSendVarsel() {
    verify {
        runBlocking {
            sendVarsel(any())
        }
    }
}

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
