package no.nav.syfo.job

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType.MER_VEILEDNING
import no.nav.syfo.planner.arbeidstakerFnr1
import no.nav.syfo.service.MerVeiledningVarselFinder
import no.nav.syfo.service.MerVeiledningVarselService
import no.nav.syfo.service.microfrontend.MikrofrontendService
import no.nav.syfo.testutil.mocks.orgnummer
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class SendMerVeiledningVarslerJobbSpek : DescribeSpec({

    val merVeiledningVarselFinder = mockk<MerVeiledningVarselFinder>(relaxed = true)
    val merVeiledningVarselService = mockk<MerVeiledningVarselService>(relaxed = true)
    val mikrofrontendService = mockk<MikrofrontendService>(relaxed = true)
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

    describe("SendMerVeiledningVarslerJobbSpek") {
        beforeTest {
            clearAllMocks()
        }

        it("Sender varsler") {
            val sendVarselJobb = SendMerVeiledningVarslerJobb(
                merVeiledningVarselFinder,
                merVeiledningVarselService,
                mikrofrontendService
            )
            coEvery { merVeiledningVarselFinder.findMerVeiledningVarslerToSendToday() } returns listOf(
                merVeiledningVarsel,
            )

            sendVarselJobb.sendVarsler()

            coVerify { merVeiledningVarselService.sendVarselTilArbeidstakerFromJob(any(), merVeiledningVarsel.uuid) }
            coVerify { mikrofrontendService.updateMikrofrontendForUserByHendelse(any()) }
        }
    }
})
