package no.nav.syfo.testutil.mocks

import no.nav.syfo.consumer.domain.OppfolgingstilfellePerson
import no.nav.syfo.consumer.domain.Syketilfellebit
import no.nav.syfo.consumer.domain.Syketilfelledag
import no.nav.syfo.consumer.syfosmregister.Gradert
import no.nav.syfo.consumer.syfosmregister.SyfosmregisterResponse
import no.nav.syfo.consumer.syfosmregister.SykmeldingPeriode
import no.nav.syfo.consumer.syfosmregister.SykmeldingStatus
import no.nav.syfo.kafka.oppfolgingstilfelle.domain.KOppfolgingstilfellePeker
import java.time.LocalDate

// Syfosyketilfelle

const val aktorId = "12345678901"
const val orgnummer = "999888777"
const val tagSykmelding = "SYKMELDING"
const val tagSendt = "SENDT"

val fom = LocalDate.of(2021, 5, 5)
val tom = LocalDate.of(2021, 6, 5)
val fomStartOfDay = fom.atStartOfDay()
val tomStartOfDay = tom.atStartOfDay()

val syketilfellebit = Syketilfellebit(
    id = "id",
    aktorId = aktorId,
    orgnummer = orgnummer,
    opprettet = fomStartOfDay,
    inntruffet = fomStartOfDay,
    tags = listOf(tagSykmelding, tagSendt),
    ressursId = "ressursId",
    fom = fomStartOfDay,
    tom = tomStartOfDay
)

val syketilfelledag = Syketilfelledag(
    dag = LocalDate.of(2021, 5, 5),
    prioritertSyketilfellebit = syketilfellebit
)

val oppfolgingstilfelleResponse = OppfolgingstilfellePerson(
    aktorId = aktorId,
    tidslinje = listOf(syketilfelledag),
    sisteDagIArbeidsgiverperiode = syketilfelledag,
    antallBrukteDager = 2,
    oppbruktArbeidsgvierperiode = false,
    utsendelsestidspunkt = fomStartOfDay
)

val syfosmregisterResponse = listOf(
    SyfosmregisterResponse(
        id = "1",
        sykmeldingsperioder = listOf(SykmeldingPeriode("", "", Gradert(100))),
        sykmeldingStatus = SykmeldingStatus()
    )
)

// Kafka - Oppfolgingstilfelle

val kafkaOppfolgingstilfellePeker = KOppfolgingstilfellePeker(
    aktorId = aktorId,
    orgnummer = orgnummer
)

// STS

val tokenFromStsServer = STSToken(
    access_token = "default access token",
    token_type = "Bearer",
    expires_in = 3600
)

data class STSToken(
    val access_token: String,
    val token_type: String,
    val expires_in: Long
)
