package no.nav.syfo.utils

import no.nav.syfo.kafka.consumers.varselbus.domain.VarselData
import no.nav.syfo.kafka.consumers.varselbus.domain.toVarselData
import java.io.IOException

fun dataToVarselData(data: Any?): VarselData {
    return data?.let {
        try {
            return data.toVarselData()
        } catch (e: IOException) {
            throw IOException("ArbeidstakerHendelse har feil format")
        }
    } ?: throw IllegalArgumentException("EsyfovarselHendelse mangler 'data'-felt")
}
