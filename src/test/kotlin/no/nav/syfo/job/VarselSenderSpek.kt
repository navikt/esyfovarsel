package no.nav.syfo.job

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.db.*
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.UTSENDING_FEILET
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.domain.VarselType.MER_VEILEDNING
import no.nav.syfo.planner.arbeidstakerFnr1
import no.nav.syfo.service.MerVeiledningVarselFinder
import no.nav.syfo.service.SendVarselService
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.mocks.orgnummer
import org.amshove.kluent.should
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class VarselSenderSpek : DescribeSpec({

    val embeddedDatabase by lazy { EmbeddedDatabase() }

    val sendVarselService = mockk<SendVarselService>(relaxed = true)
    val merVeiledningVarselFinder = mockk<MerVeiledningVarselFinder>(relaxed = true)
    val merVeiledningVarsel = PPlanlagtVarsel(
        UUID.randomUUID().toString(),
        arbeidstakerFnr1,
        orgnummer,
        null,
        MER_VEILEDNING.name,
        LocalDate.now(),
        LocalDateTime.now(),
        LocalDateTime.now(),
    )

    describe("VarselSenderSpek") {
        afterTest {
            embeddedDatabase.connection.dropData()
        }

        afterSpec {
            embeddedDatabase.stop()
        }

        it("Sender varsler") {
            val sendVarselJobb = VarselSender(
                embeddedDatabase,
                sendVarselService,
                merVeiledningVarselFinder,
            )
            val planlagtVarselToStore =
                PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, orgnummer, setOf("1"), MER_VEILEDNING)

            coEvery { merVeiledningVarselFinder.findMerVeiledningVarslerToSendToday() } returns listOf(
                merVeiledningVarsel,
            )
            coEvery { merVeiledningVarselFinder.isBrukerYngreEnn67Ar(any()) } returns true

            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore)

            sendVarselJobb.testSendVarsler()
            sendVarselService.testSendVarsel()

            embeddedDatabase.skalHaPlanlagtVarsel(arbeidstakerFnr1, MER_VEILEDNING)
            embeddedDatabase.skalHaUtsendtVarsel(arbeidstakerFnr1, MER_VEILEDNING)
        }


        it("Skal ikke markere varsel som sendt dersom utsending feiler") {
            val sendVarselJobb = VarselSender(
                embeddedDatabase,
                sendVarselService,
                merVeiledningVarselFinder,
            )
            val planlagtVarselToStore =
                PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, orgnummer, setOf("1"), MER_VEILEDNING)
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore)

            coEvery { merVeiledningVarselFinder.findMerVeiledningVarslerToSendToday() } returns listOf(
                merVeiledningVarsel,
            )
            coEvery { sendVarselService.sendVarsel(any()) } returns UTSENDING_FEILET
            coEvery { merVeiledningVarselFinder.isBrukerYngreEnn67Ar(any()) } returns true

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

private fun DatabaseInterface.skalHaPlanlagtVarsel(fnr: String, type: VarselType) =
    this.should("Skal ha planlagt varsel av type $type") {
        this.fetchPlanlagtVarselByFnr(fnr).filter { it.type.equals(type.name) }.isNotEmpty()
    }

private fun DatabaseInterface.skalHaUtsendtVarsel(fnr: String, type: VarselType) =
    this.should("Skal ha utsendt varsel av type $type") {
        this.fetchUtsendtVarselByFnr(fnr).filter { it.type.equals(type.name) }.isNotEmpty()
    }

private fun DatabaseInterface.skalIkkeHaUtsendtVarsel(fnr: String, type: VarselType) =
    this.should("Skal ikke ha utsendt varsel av type $type") {
        this.fetchUtsendtVarselByFnr(fnr).filter { it.type.equals(type.name) }.isEmpty()
    }
