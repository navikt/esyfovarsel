package no.nav.syfo.kafka.varselbus

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.nav.syfo.kafka.varselbus.domain.OppfolgingsplanNLVarselData
import no.nav.syfo.kafka.varselbus.domain.EsyfovarselHendelse
import org.apache.commons.cli.MissingArgumentException
import java.io.IOException

//Placeholdere

const val NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING_TEKST = "SM har sendt plan til godkjenning"
const val NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING_LENKE = "https://dine-sykmeldte.no"
const val NL_OPPFOLGINGSPLAN_OPPRETTET_TEKST = "SM har opprettet plan"
const val NL_OPPFOLGINGSPLAN_OPPRETTET_LENKE  = "https://dine-sykmeldte.no"

private val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
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