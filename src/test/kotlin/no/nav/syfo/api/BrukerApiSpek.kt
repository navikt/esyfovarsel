package no.nav.syfo.api

import com.auth0.jwt.interfaces.Payload
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.api.bruker.registerBrukerApi
import no.nav.syfo.api.bruker.urlPath39UkersVarsel
import no.nav.syfo.consumer.PdlConsumer
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.storeUtsendtVarselTest
import no.nav.syfo.kafka.consumers.oppfolgingstilfelle.domain.Oppfolgingstilfelle39Uker
import no.nav.syfo.planner.FULL_AG_PERIODE
import no.nav.syfo.service.VarselSendtService
import no.nav.syfo.syketilfelle.SyketilfellebitService
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.mocks.*
import no.nav.syfo.util.contentNegotationFeature
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*

class BrukerApiSpek : Spek({
    describe("Bruker kaller sjekk på om varsel er sendt") {
        val embeddedDatabase by lazy { EmbeddedDatabase() }
        val syketilfellebitService = mockk<SyketilfellebitService>()
        val pdlConsumer = mockk<PdlConsumer>()
        val varselSendtService = VarselSendtService(pdlConsumer, syketilfellebitService, embeddedDatabase)
        val requestUrl39UkersVarsel = urlPath39UkersVarsel.replace("{aktorid}", aktorId)
        val requestUrl39UkersVarselUautorisert = urlPath39UkersVarsel.replace("{aktorid}", aktorId2)
        val requestUrl39UkersVarselManglerAktorId = urlPath39UkersVarsel.replace("{aktorid}", aktorId3)
        val requestUrl39UkersVarselUgyldigAktorId = urlPath39UkersVarsel.replace("{aktorid}", "ugyldig")

        val mockPayload = mockk<Payload>()

        coEvery { mockPayload.getClaim("pid").asString() } returns fnr1

        coEvery { pdlConsumer.getFnr(aktorId) } returns fnr1
        coEvery { pdlConsumer.getFnr(aktorId2) } returns fnr2
        coEvery { pdlConsumer.getFnr(aktorId3) } returns null
        coEvery { pdlConsumer.getFnr("ugyldig") } returns "ugyldig"

        with(TestApplicationEngine()) {
            start()
            application.install(ContentNegotiation, contentNegotationFeature())
            application.routing {
                registerBrukerApi(varselSendtService)
            }

            it("Bruker skal få 'false' dersom varsel ikke er sendt") {
                val fom = LocalDate.now()
                val tom = fom.plusWeeks(40)

                val oppfolgingstilfelle39Uker = Oppfolgingstilfelle39Uker(
                    aktorId,
                    FULL_AG_PERIODE,
                    ChronoUnit.DAYS.between(fom, tom).toInt(),
                    fom,
                    tom
                )

                coEvery { syketilfellebitService.beregnKOppfolgingstilfelle39UkersVarsel(any()) } returns oppfolgingstilfelle39Uker

                with(
                    handleRequest(HttpMethod.Get, requestUrl39UkersVarsel) {
                        call.authentication.principal = JWTPrincipal(mockPayload)
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                    }
                ) {
                    response.status()?.isSuccess() shouldBeEqualTo true
                    response.content shouldBeEqualTo "false"
                }
            }
            it("Bruker skal få 'true' dersom varsel har blitt sendt i inneværende sykeforløp") {
                val fom = LocalDate.now().minusWeeks(40)
                val tom = LocalDate.now().plusWeeks(1)
                val varselUtsendtDato = fom.plusWeeks(39)

                val oppfolgingstilfelle39Uker = Oppfolgingstilfelle39Uker(
                    aktorId,
                    FULL_AG_PERIODE,
                    ChronoUnit.DAYS.between(fom, tom).toInt(),
                    fom,
                    tom
                )

                val tidligereUtsendtVarsel = PPlanlagtVarsel(
                    UUID.randomUUID().toString(),
                    fnr1,
                    aktorId,
                    orgnummer,
                    VarselType.MER_VEILEDNING.name,
                    varselUtsendtDato,
                    varselUtsendtDato.atStartOfDay(),
                    varselUtsendtDato.atStartOfDay()
                )

                embeddedDatabase.storeUtsendtVarselTest(tidligereUtsendtVarsel)

                coEvery { syketilfellebitService.beregnKOppfolgingstilfelle39UkersVarsel(any()) } returns oppfolgingstilfelle39Uker

                with(
                    handleRequest(HttpMethod.Get, requestUrl39UkersVarsel) {
                        call.authentication.principal = JWTPrincipal(mockPayload)
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                    }
                ) {
                    response.status()?.isSuccess() shouldBeEqualTo true
                    response.content shouldBeEqualTo "true"
                }
            }
            it("Bruker skal få 'false' dersom varsel har blitt sendt i et annet sykeforløp") {
                val fom = LocalDate.now()
                val tom = fom.plusWeeks(40)
                val varselUtsendtDato = fom.minusDays(1)

                val oppfolgingstilfelle39Uker = Oppfolgingstilfelle39Uker(
                    aktorId,
                    FULL_AG_PERIODE,
                    ChronoUnit.DAYS.between(fom, tom).toInt(),
                    fom,
                    tom
                )

                val tidligereUtsendtVarsel = PPlanlagtVarsel(
                    UUID.randomUUID().toString(),
                    fnr1,
                    aktorId,
                    orgnummer,
                    VarselType.MER_VEILEDNING.name,
                    varselUtsendtDato,
                    varselUtsendtDato.atStartOfDay(),
                    varselUtsendtDato.atStartOfDay()
                )

                embeddedDatabase.storeUtsendtVarselTest(tidligereUtsendtVarsel)

                coEvery { syketilfellebitService.beregnKOppfolgingstilfelle39UkersVarsel(any()) } returns oppfolgingstilfelle39Uker

                with(
                    handleRequest(HttpMethod.Get, requestUrl39UkersVarsel) {
                        call.authentication.principal = JWTPrincipal(mockPayload)
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                    }
                ) {
                    response.status()?.isSuccess() shouldBeEqualTo true
                    response.content shouldBeEqualTo "false"
                }
            }
            it("Bruker skal ikke kunne slå opp andre aktørId'er enn sin egen") {
                val fom = LocalDate.now()
                val tom = fom.plusWeeks(40)
                val varselUtsendtDato = fom.minusDays(1)

                val oppfolgingstilfelle39Uker = Oppfolgingstilfelle39Uker(
                    aktorId,
                    FULL_AG_PERIODE,
                    ChronoUnit.DAYS.between(fom, tom).toInt(),
                    fom,
                    tom
                )

                val tidligereUtsendtVarsel = PPlanlagtVarsel(
                    UUID.randomUUID().toString(),
                    fnr1,
                    aktorId,
                    orgnummer,
                    VarselType.MER_VEILEDNING.name,
                    varselUtsendtDato,
                    varselUtsendtDato.atStartOfDay(),
                    varselUtsendtDato.atStartOfDay()
                )

                embeddedDatabase.storeUtsendtVarselTest(tidligereUtsendtVarsel)

                coEvery { syketilfellebitService.beregnKOppfolgingstilfelle39UkersVarsel(any()) } returns oppfolgingstilfelle39Uker

                with(
                    handleRequest(HttpMethod.Get, requestUrl39UkersVarselUautorisert) {
                        call.authentication.principal = JWTPrincipal(mockPayload)
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                    }
                ) {
                    response.status()?.value shouldBeEqualTo HttpStatusCode.Forbidden.value
                }
            }

            it("Manglende aktorId skal returnere 500") {
                val fom = LocalDate.now()
                val tom = fom.plusWeeks(40)
                val varselUtsendtDato = fom.minusDays(1)

                val oppfolgingstilfelle39Uker = Oppfolgingstilfelle39Uker(
                    aktorId3,
                    FULL_AG_PERIODE,
                    ChronoUnit.DAYS.between(fom, tom).toInt(),
                    fom,
                    tom
                )

                val tidligereUtsendtVarsel = PPlanlagtVarsel(
                    UUID.randomUUID().toString(),
                    fnr3,
                    aktorId3,
                    orgnummer,
                    VarselType.MER_VEILEDNING.name,
                    varselUtsendtDato,
                    varselUtsendtDato.atStartOfDay(),
                    varselUtsendtDato.atStartOfDay()
                )

                embeddedDatabase.storeUtsendtVarselTest(tidligereUtsendtVarsel)

                coEvery { syketilfellebitService.beregnKOppfolgingstilfelle39UkersVarsel(any()) } returns oppfolgingstilfelle39Uker

                coEvery { mockPayload.subject } returns fnr3

                with(
                    handleRequest(HttpMethod.Get, requestUrl39UkersVarselManglerAktorId) {
                        call.authentication.principal = JWTPrincipal(mockPayload)
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                    }
                ) {
                    response.status()?.value shouldBeEqualTo HttpStatusCode.InternalServerError.value
                }
            }

            it("Ugyldig aktorId skal returnere 400") {
                val fom = LocalDate.now()
                val tom = fom.plusWeeks(40)

                val oppfolgingstilfelle39Uker = Oppfolgingstilfelle39Uker(
                    aktorId,
                    FULL_AG_PERIODE,
                    ChronoUnit.DAYS.between(fom, tom).toInt(),
                    fom,
                    tom
                )

                coEvery { syketilfellebitService.beregnKOppfolgingstilfelle39UkersVarsel(any()) } returns oppfolgingstilfelle39Uker

                with(
                    handleRequest(HttpMethod.Get, requestUrl39UkersVarselUgyldigAktorId) {
                        call.authentication.principal = JWTPrincipal(mockPayload)
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                    }
                ) {
                    response.status()?.value shouldBeEqualTo HttpStatusCode.BadRequest.value
                }
            }
        }
    }
})
