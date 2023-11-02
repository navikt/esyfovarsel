package no.nav.syfo.job

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.access.domain.UserAccessStatus
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType.MER_VEILEDNING
import no.nav.syfo.getTestEnv
import no.nav.syfo.planner.arbeidstakerFnr1
import no.nav.syfo.service.AccessControlService
import no.nav.syfo.service.MerVeiledningVarselFinder
import no.nav.syfo.service.MerVeiledningVarselService
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.mocks.orgnummer
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class SendMerVeiledningVarslerJobbSpec : DescribeSpec({

    val testEnv = getTestEnv()
    val embeddedDatabase by lazy { EmbeddedDatabase() }

    val accessControlService = mockk<AccessControlService>(relaxed = true)
    val merVeiledningVarselFinder = mockk<MerVeiledningVarselFinder>(relaxed = true)
    val merVeiledningVarselService = mockk<MerVeiledningVarselService>(relaxed = true)
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
    val userAccessStatus = UserAccessStatus(
        arbeidstakerFnr1,
        canUserBeDigitallyNotified = true,
    )

    describe("SendMerVeiledningVarslerJobbSpec") {
        afterTest {
            clearAllMocks()
            embeddedDatabase.connection.dropData()
        }

        afterSpec {
            embeddedDatabase.stop()
        }

        it("Sender varsler") {
            val sendVarselJobb = SendMerVeiledningVarslerJobb(
                merVeiledningVarselFinder,
                accessControlService,
                testEnv.urlEnv,
                merVeiledningVarselService
            )

            every { accessControlService.getUserAccessStatus(any()) } returns userAccessStatus
            coEvery { merVeiledningVarselFinder.findMerVeiledningVarslerToSendToday() } returns listOf(
                merVeiledningVarsel,
            )
            coEvery { merVeiledningVarselFinder.isBrukerYngreEnn67Ar(any()) } returns true


            runBlocking {
                sendVarselJobb.sendVarsler()
            }

            coVerify { merVeiledningVarselService.sendVarselTilArbeidstaker(any(), merVeiledningVarsel.uuid, userAccessStatus) }
        }
    }
})
