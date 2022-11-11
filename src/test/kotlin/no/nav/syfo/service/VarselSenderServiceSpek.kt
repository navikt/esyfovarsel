package no.nav.syfo.service

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.consumer.syfosmregister.SykmeldingerConsumer
import no.nav.syfo.db.arbeidstakerAktorId1
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.storeInfotrygdUtbetaling
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.db.storeUtsendtVarsel
import no.nav.syfo.planner.arbeidstakerFnr1
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.mocks.orgnummer
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

object VarselSenderServiceSpek : Spek({

    val embeddedDatabase by lazy { EmbeddedDatabase() }
    val sykmeldingerConsumerMock: SykmeldingerConsumer = mockk(relaxed = true)
    val varselSenderServiceMockk: VarselSenderService = mockk(relaxed = true)
    val sykmeldingServiceMockk = SykmeldingService(sykmeldingerConsumerMock)

    //The default timeout of 10 seconds is not sufficient to initialise the embedded database
    defaultTimeout = 20000L
    
    describe("VarselSenderServiceSpek") {
        afterEachTest {
            clearAllMocks()
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }

        it("Should send 2 varsler: 1 MER_VEILEDNING and 1 AKTIVITETSKRAV") {
            coEvery { sykmeldingServiceMockk.isPersonSykmeldtPaDato(LocalDate.now(), arbeidstakerFnr1) } returns true

            val planlagtMerVeiledningVarselToStore =
                PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, orgnummer, setOf("1"), VarselType.MER_VEILEDNING, utsendingsdato = LocalDate.now())
            embeddedDatabase.storePlanlagtVarsel(planlagtMerVeiledningVarselToStore)

            val planlagtAktivitetskravVarselToStore =
                PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, orgnummer, setOf("1"), VarselType.AKTIVITETSKRAV, utsendingsdato = LocalDate.now())
            embeddedDatabase.storePlanlagtVarsel(planlagtAktivitetskravVarselToStore)

            val utsendtVarselToStore = PUtsendtVarsel(
                UUID.randomUUID().toString(),
                arbeidstakerFnr1,
                null,
                "",
                orgnummer,
                VarselType.MER_VEILEDNING.name,
                null,
                LocalDateTime.now().minusDays(1),
                null,
                null
            )
            embeddedDatabase.storeUtsendtVarsel(utsendtVarselToStore)
            embeddedDatabase.storeInfotrygdUtbetaling(arbeidstakerFnr1, LocalDate.now().plusDays(2), LocalDate.now(), 50)

            val varslerToSendToday = runBlocking {
                varselSenderServiceMockk.getVarslerToSendToday()
            }
            varslerToSendToday.size shouldBeEqualTo 2
        }
    }
})

