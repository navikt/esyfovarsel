package no.nav.syfo.kafka.varselbus.domain

import java.io.Serializable

data class AktivitetskravNLVarselData (
    val ansattFnr: String,
    val orgnummer: String
) : Serializable
