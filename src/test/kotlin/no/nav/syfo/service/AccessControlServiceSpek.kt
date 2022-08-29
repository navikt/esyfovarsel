import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.consumer.PdlConsumer
import no.nav.syfo.consumer.dkif.DkifConsumer
import no.nav.syfo.service.AccessControlService
import no.nav.syfo.testutil.mocks.aktorId
import no.nav.syfo.testutil.mocks.fnr1
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object AccessControlServiceSpek : Spek({
    val pdlConsumer = mockk<PdlConsumer>()
    val dkifConsumer = mockk<DkifConsumer>()

    val accessControlService = AccessControlService(pdlConsumer, dkifConsumer)
    coEvery { pdlConsumer.getFnr(aktorId) } returns fnr1

    describe("AccessControlServiceSpek") {
        it("User should be only digitally notified if there is no address protection AND non-reserved in DKIF") {
            coEvery { pdlConsumer.isBrukerGradertForInformasjon(aktorId) } returns false
            coEvery { dkifConsumer.kontaktinfo(aktorId)?.kanVarsles } returns true

            val userAccessStatus = accessControlService.getUserAccessStatusByAktorId(aktorId)
            userAccessStatus.canUserBeDigitallyNotified shouldBeEqualTo true
            userAccessStatus.canUserBePhysicallyNotified shouldBeEqualTo false
        }

        it("User should be only physically notified if there is no address protection AND reserved in DKIF") {
            coEvery { pdlConsumer.isBrukerGradertForInformasjon(aktorId) } returns false
            coEvery { dkifConsumer.kontaktinfo(aktorId)?.kanVarsles } returns false

            val userAccessStatus = accessControlService.getUserAccessStatusByAktorId(aktorId)
            userAccessStatus.canUserBeDigitallyNotified shouldBeEqualTo false
            userAccessStatus.canUserBePhysicallyNotified shouldBeEqualTo true
        }

        it("User should not be notified if there is address protection AND reserved in DKIF") {
            coEvery { pdlConsumer.isBrukerGradertForInformasjon(aktorId) } returns true
            coEvery { dkifConsumer.kontaktinfo(aktorId)?.kanVarsles } returns false

            val userAccessStatus = accessControlService.getUserAccessStatusByAktorId(aktorId)
            userAccessStatus.canUserBeDigitallyNotified shouldBeEqualTo false
            userAccessStatus.canUserBePhysicallyNotified shouldBeEqualTo false
        }

        it("User should not be notified if there is address protection AND non-reserved in DKIF") {
            coEvery { pdlConsumer.isBrukerGradertForInformasjon(aktorId) } returns true
            coEvery { dkifConsumer.kontaktinfo(aktorId)?.kanVarsles } returns true

            val userAccessStatus = accessControlService.getUserAccessStatusByAktorId(aktorId)
            userAccessStatus.canUserBeDigitallyNotified shouldBeEqualTo false
            userAccessStatus.canUserBePhysicallyNotified shouldBeEqualTo false
        }
    }
})
