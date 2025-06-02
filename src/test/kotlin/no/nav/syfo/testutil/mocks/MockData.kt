package no.nav.syfo.testutil.mocks

import no.nav.syfo.access.domain.UserAccessStatus
import no.nav.syfo.consumer.dkif.Kontaktinfo
import no.nav.syfo.consumer.dkif.PostPersonerResponse

const val fnr1 = "12345678901"
const val fnr2 = "23456789012"
const val fnr3 = "34567890123"
const val fnr4 = "45678901234"
const val fnr5 = "45678901230"
const val orgnummer = "999888777"

val userAccessStatus1 = UserAccessStatus(fnr1, true)
val userAccessStatus2 = UserAccessStatus(fnr2, true)
val userAccessStatus3 = UserAccessStatus(fnr3, false)
val userAccessStatus4 = UserAccessStatus(fnr4, false)
val userAccessStatus5 = UserAccessStatus(fnr5, false)

val dkifPostPersonerResponse = PostPersonerResponse(
    personer = mapOf(
        fnr1 to Kontaktinfo(kanVarsles = true, reservert = false),
        fnr2 to Kontaktinfo(kanVarsles = false, reservert = true)
    )
)

val tokenFromAzureServer = Token(
    access_token = "AAD access token",
    token_type = "Bearer",
    expires_in = 3600
)

data class Token(
    val access_token: String,
    val token_type: String,
    val expires_in: Long
)
