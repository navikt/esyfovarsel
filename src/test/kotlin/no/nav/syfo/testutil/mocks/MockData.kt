package no.nav.syfo.testutil.mocks

import no.nav.syfo.access.domain.UserAccessStatus
import no.nav.syfo.consumer.dkif.Kontaktinfo
import no.nav.syfo.consumer.dkif.PostPersonerResponse

const val FNR_1 = "12345678901"
const val FNR_2 = "23456789012"
const val FNR_3 = "34567890123"
const val FNR_4 = "45678901234"
const val FNR_5 = "45678901230"
const val ORGNUMMER = "999888777"

val userAccessStatus1 = UserAccessStatus(FNR_1, true)
val userAccessStatus2 = UserAccessStatus(FNR_2, true)
val userAccessStatus3 = UserAccessStatus(FNR_3, false)
val userAccessStatus4 = UserAccessStatus(FNR_4, false)
val userAccessStatus5 = UserAccessStatus(FNR_5, false)

val dkifPostPersonerResponse =
    PostPersonerResponse(
        personer =
            mapOf(
                FNR_1 to Kontaktinfo(kanVarsles = true, reservert = false),
                FNR_2 to Kontaktinfo(kanVarsles = false, reservert = true),
            ),
    )

val tokenFromAzureServer =
    Token(
        access_token = "AAD access token",
        token_type = "Bearer",
        expires_in = 3600,
    )

data class Token(
    val access_token: String,
    val token_type: String,
    val expires_in: Long,
)
