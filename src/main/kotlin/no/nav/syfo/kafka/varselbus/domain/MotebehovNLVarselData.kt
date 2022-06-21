package no.nav.syfo.kafka.varselbus.domain

import java.io.Serializable

data class MotebehovNLVarselData(
    val ansattFnr: String,
    val orgnummer: String
) : Serializable
