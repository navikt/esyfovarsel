package no.nav.syfo.utils

import java.util.*

const val NAV_CALL_ID_HEADER = "Nav-Call-Id"
const val NAV_PERSONIDENT_HEADER = "nav-personident"

fun createCallId(): String {
    val randomUUID = UUID.randomUUID().toString()
    return "esyfovarsel-$randomUUID"
}
