package no.nav.syfo.kafka.varselbus

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.nav.syfo.kafka.varselbus.domain.DineSykmeldteHendelse
import no.nav.syfo.kafka.varselbus.domain.OppfolgingsplanNLVarselData
import no.nav.syfo.kafka.varselbus.domain.EsyfovarselHendelse
import no.nav.syfo.kafka.varselbus.domain.HendelseType
import no.nav.syfo.kafka.varselbus.domain.HendelseType.*
import no.nav.syfo.kafka.varselbus.domain.DineSykmeldteHendelse.*
import org.apache.commons.cli.MissingArgumentException
import java.io.IOException

const val NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING_TEKST = "En oppfølgingsplan venter på godkjenning fra deg"
const val NL_OPPFOLGINGSPLAN_OPPRETTET_TEKST = "En ny oppfølgingsplan er blitt opprettet"

private val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

fun HendelseType.toDineSykmeldteHendelse(): DineSykmeldteHendelse {
    return when (this) {
        NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING -> OPPFOLGINGSPLAN_TIL_GODKJENNING
        NL_OPPFOLGINGSPLAN_OPPRETTET -> OPPFOLGINGSPLAN_OPPRETTET
    }
}

fun EsyfovarselHendelse.dataToOppfolgingsplanNLVarselData(): OppfolgingsplanNLVarselData {
    return data?.let {
        try {
            val varseldata: OppfolgingsplanNLVarselData = objectMapper.readValue(data.toString())
            if (isOrgFnrNrValidFormat(varseldata.ansattFnr, varseldata.orgnummer)) {
                return@let varseldata
            }
            throw IllegalArgumentException("OppfolgingsplanNLVarselData har ugyldig fnr eller orgnummer")
        } catch (e: IOException) {
            throw IOException("EsyfovarselHendelse har feil format i 'data'-felt")
        }
    } ?: throw MissingArgumentException("EsyfovarselHendelse mangler 'data'-felt")
}


private fun isOrgFnrNrValidFormat(fnr: String?, orgnr: String?): Boolean {
    return orgnr?.length == 9
        && orgnr.all { char -> char.isDigit()}
        && fnr?.length == 11
        && fnr.all { char -> char.isDigit()}
}