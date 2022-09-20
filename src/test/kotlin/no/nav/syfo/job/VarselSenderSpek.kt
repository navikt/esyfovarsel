package no.nav.syfo.job

import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ToggleEnv
import no.nav.syfo.db.*
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.UTSENDING_FEILET
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.domain.VarselType.AKTIVITETSKRAV
import no.nav.syfo.db.domain.VarselType.MER_VEILEDNING
import no.nav.syfo.planner.arbeidstakerFnr1
import no.nav.syfo.planner.orgnummer
import no.nav.syfo.service.SendVarselService
import no.nav.syfo.service.SykepengerMaxDateSource
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.utils.REMAINING_DAYS_UNTIL_39_UKERS_VARSEL
import org.amshove.kluent.should
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

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
            val sendVarselJobb = VarselSender(embeddedDatabase, sendVarselService, ToggleEnv(true, true, true, false, false))

            val planlagtVarselToStore = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, orgnummer, setOf("1"), MER_VEILEDNING)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore)

            sendVarselJobb.testSendVarsler()
            sendVarselService.testSendVarsel()

            embeddedDatabase.skalIkkeHaPlanlagtVarsel(arbeidstakerFnr1, MER_VEILEDNING)
            embeddedDatabase.skalHaUtsendtVarsel(arbeidstakerFnr1, MER_VEILEDNING)
        }

        it("Sender mer veiledning varsler basert på maksdato") {
            val sendVarselJobb = VarselSender(embeddedDatabase, sendVarselService, ToggleEnv(false, false, false, true, true))
            val maxDate = LocalDate.now().plusDays(REMAINING_DAYS_UNTIL_39_UKERS_VARSEL)

            embeddedDatabase.storeSykepengerMaxDate(maxDate, arbeidstakerFnr1, SykepengerMaxDateSource.INFOTRYGD.name)

            sendVarselJobb.testSendVarsler()

            embeddedDatabase.skalHaMaxDate(maxDate)
        }


        it("Skal ikke sende mer veiledning-varsel hvis toggle er false") {
            val sendVarselJobb = VarselSender(embeddedDatabase, sendVarselService, ToggleEnv(false, true, true, false, false))
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
            val sendVarselJobb = VarselSender(embeddedDatabase, sendVarselService, ToggleEnv(true, false, true, false, false))
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
            val sendVarselJobb = VarselSender(embeddedDatabase, sendVarselService, ToggleEnv(true, true, true, false, false))
            val planlagtVarselToStore = PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, orgnummer, setOf("1"), MER_VEILEDNING)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore)

            coEvery { sendVarselService.sendVarsel(any()) } returns UTSENDING_FEILET

            sendVarselJobb.testSendVarsler()
            sendVarselService.testSendVarsel()

            embeddedDatabase.skalHaPlanlagtVarsel(arbeidstakerFnr1, MER_VEILEDNING)
            embeddedDatabase.skalIkkeHaUtsendtVarsel(arbeidstakerFnr1, MER_VEILEDNING)
        }
    }
})

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

private fun DatabaseInterface.skalHaMaxDate(maxDate: LocalDate) = this.should("Skal hente maks dato") {
    this.fetchBySendingDate(LocalDate.now())[0].maxDate.isEqual(maxDate)
}

private fun DatabaseInterface.skalHaUtsendtVarsel(fnr: String, type: VarselType) = this.should("Skal ha utsendt varsel av type $type") {
    this.fetchUtsendtVarselByFnr(fnr).filter { it.type.equals(type.name) }.isNotEmpty()
}

private fun DatabaseInterface.skalIkkeHaUtsendtVarsel(fnr: String, type: VarselType) = this.should("Skal ikke ha utsendt varsel av type $type") {
    this.fetchUtsendtVarselByFnr(fnr).filter { it.type.equals(type.name) }.isEmpty()
}
