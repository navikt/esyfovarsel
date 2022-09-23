package no.nav.syfo.testutil.mocks

import no.nav.syfo.access.domain.UserAccessStatus

const val aktorId = "1234567890123"
const val aktorId2 = "2345678901234"
const val aktorId3 = "3456789012345"
const val aktorId4 = "4567890123456"
const val aktorId5 = "4567890123450"
const val fnr1 = "12345678901"
const val fnr2 = "23456789012"
const val fnr3 = "34567890123"
const val fnr4 = "45678901234"
const val fnr5 = "45678901230"
const val orgnummer = "999888777"

val userAccessStatus1 = UserAccessStatus(fnr1, true, false) // Kan varsles digitalt
val userAccessStatus2 = UserAccessStatus(fnr2, true, false) // Kan varsles digitalt
val userAccessStatus3 = UserAccessStatus(fnr3, false, true) // Kan varsles fysisk
val userAccessStatus4 = UserAccessStatus(fnr4, false, true) // Kan varsles fysisk
val userAccessStatus5 = UserAccessStatus(fnr5, false, false) // Kan ikke varsles

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
