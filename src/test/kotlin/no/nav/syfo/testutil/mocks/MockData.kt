package no.nav.syfo.testutil.mocks

import no.nav.syfo.consumer.domain.*
import no.nav.syfo.consumer.pdl.*
import no.nav.syfo.consumer.syfosmregister.SykmeldtStatusResponse
import no.nav.syfo.kafka.oppfolgingstilfelle.domain.KOppfolgingstilfellePeker
import no.nav.syfo.kafka.oppfolgingstilfelle.domain.OppfolgingstilfellePerson
import no.nav.syfo.syketilfelle.domain.Syketilfellebit
import no.nav.syfo.syketilfelle.domain.Tag
import java.time.LocalDate

// Syfosyketilfelle

const val aktorId = "1234567890123"
const val aktorId2 = "2345678901234"
const val aktorId3 = "3456789012345"
const val aktorId4 = "4567890123456"
const val fnr1 = "12345678901"
const val fnr2 = "23456789012"
const val fnr3 = "34567890123"
const val fnr4 = "45678901234"
const val orgnummer = "999888777"

val fom = LocalDate.of(2021, 5, 5)
val tom = LocalDate.of(2021, 6, 5)
val fomStartOfDay = fom.atStartOfDay()
val tomStartOfDay = tom.atStartOfDay()

val syketilfellebit = Syketilfellebit(
    id = "id",
    fnr = fnr1,
    orgnummer = orgnummer,
    opprettet = fomStartOfDay,
    inntruffet = fomStartOfDay,
    tags = listOf(Tag.SYKMELDING, Tag.SENDT),
    ressursId = "ressursId",
    fom = fom,
    tom = tom
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

//syfosmregister

val sykmeldtStatusResponse = listOf(
    SykmeldtStatusResponse(
        erSykmeldt = true,
        gradert = false,
        fom = LocalDate.now(),
        tom = LocalDate.now()
    )
)

// DKIF

val dkifResponseSuccessKanVarsles = DigitalKontaktinfoBolk(
    feil = null,
    kontaktinfo = mapOf(
        aktorId to DigitalKontaktinfo(
            epostadresse = "test@nav.no",
            kanVarsles = true,
            reservert = false,
            mobiltelefonnummer = "44556677",
            personident = aktorId
        )
    )
)

val dkifResponseSuccessReservert = DigitalKontaktinfoBolk(
    feil = null,
    kontaktinfo = mapOf(
        aktorId2 to DigitalKontaktinfo(
            epostadresse = "test@nav.no",
            kanVarsles = false,
            reservert = true,
            mobiltelefonnummer = "44556677",
            personident = aktorId
        )
    )
)

val dkifResponseFailedGeneral = DigitalKontaktinfoBolk(
    feil = mapOf("" to Feil(melding = "Ukjent feil...")),
    kontaktinfo = null
)

val dkifResponseFailedIngenKontaktinfo = DigitalKontaktinfoBolk(
    feil = mapOf("" to Feil(melding = "Ingen kontaktinformasjon er registrert på personen")),
    kontaktinfo = null
)

val dkifResponseMap = mapOf(
    aktorId to dkifResponseSuccessKanVarsles,
    aktorId2 to dkifResponseSuccessReservert,
    aktorId3 to dkifResponseFailedGeneral,
    aktorId4 to dkifResponseFailedIngenKontaktinfo
)

// PDL - Persondataløsningen

val pdlGetBrukerReservert = mapOf(
    aktorId to PdlPersonResponse(
        null,
        PdlHentPerson(
            PdlPerson(
                emptyList()
            )
        )
    ),
    aktorId2 to PdlPersonResponse(
        null,
        PdlHentPerson(
            PdlPerson(
                emptyList()
            )
        )
    ),
    aktorId3 to PdlPersonResponse(
        null,
        PdlHentPerson(
            PdlPerson(
                listOf(
                    Adressebeskyttelse(Gradering.STRENGT_FORTROLIG)
                )
            )
        )
    ),
    aktorId4 to PdlPersonResponse(
        listOf(
            PdlError(
                message = "Cannot find person in PDL",
                locations = listOf(PdlErrorLocation(line = 0, column = 0)),
                path = null,
                extensions = PdlErrorExtension(code = "code", classification = "classification")
            )
        ),
        null
    )
)

val pdlGetFnrResponseMap = mapOf(
    aktorId to PdlIdentResponse(
        null,
        PdlHentIdenter(
            PdlIdenter(
                listOf(
                    PdlIdent(
                        ident = fnr1
                    )
                )
            )
        )
    ),
    aktorId2 to PdlIdentResponse(
        null,
        PdlHentIdenter(
            PdlIdenter(
                listOf(
                    PdlIdent(
                        ident = fnr2
                    )
                )
            )
        )
    ),
    aktorId3 to PdlIdentResponse(
        null,
        PdlHentIdenter(
            PdlIdenter(
                listOf(
                    PdlIdent(
                        ident = fnr3
                    )
                )
            )
        )
    ),
    aktorId4 to PdlIdentResponse(
        null,
        PdlHentIdenter(
            PdlIdenter(
                listOf(
                    PdlIdent(
                        ident = fnr4
                    )
                )
            )
        )
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
