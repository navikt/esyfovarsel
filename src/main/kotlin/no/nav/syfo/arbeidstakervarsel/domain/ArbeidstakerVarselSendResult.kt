package no.nav.syfo.arbeidstakervarsel.domain

import no.nav.syfo.arbeidstakervarsel.dao.ArbeidstakerKanal

data class ArbeidstakerVarselSendResult(
    val success: Boolean,
    val uuid: String,
    val kanal: ArbeidstakerKanal,
    val exception: Exception? = null
)