package no.nav.syfo.testutil.mocks

import no.nav.syfo.access.domain.UserAccessStatus

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

val dkifResponseSuccessKanVarslesResponseJSON = """
    {
        "kanVarsles": true,
        "reservert": false
    }
""".trim()

val dkifResponseSuccessReservertResponseJSON = """
    {
        "kanVarsles": false,
        "reservert": true
    }
""".trim()

val dkifResponseMap = mapOf(
    fnr1 to dkifResponseSuccessKanVarslesResponseJSON,
    fnr2 to dkifResponseSuccessReservertResponseJSON
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
