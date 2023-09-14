package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.consumer.dkif.DkifConsumer
import no.nav.syfo.testutil.mocks.fnr1
import org.amshove.kluent.shouldBeEqualTo

class AccessControlServiceSpek : DescribeSpec({
    val dkifConsumer = mockk<DkifConsumer>(relaxed = true)

    val accessControlService = AccessControlService(dkifConsumer)

    describe("AccessControlServiceSpek") {
        it("User can be notified digitally if DKIF says kanVarsles == true") {
            every { dkifConsumer.person(fnr1)?.kanVarsles } returns true

            val userAccessStatus = accessControlService.getUserAccessStatus(fnr1)
            userAccessStatus.canUserBeDigitallyNotified shouldBeEqualTo true
        }

        it("User can not be notified digitally if DKIF says kanVarsles == false") {
            every { dkifConsumer.person(fnr1)?.kanVarsles } returns false

            val userAccessStatus = accessControlService.getUserAccessStatus(fnr1)
            userAccessStatus.canUserBeDigitallyNotified shouldBeEqualTo false
        }
    }
})
