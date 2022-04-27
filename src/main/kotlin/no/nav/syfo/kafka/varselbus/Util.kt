package no.nav.syfo.kafka.varselbus

import no.nav.syfo.kafka.varselbus.domain.OppfolgingsplanNLVarselData
import no.nav.syfo.kafka.varselbus.domain.EsyfovarselHendelse

//Placeholdere

const val NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING_TEKST = "SM har sendt plan til godkjenning"
const val NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING_LENKE = "https://dine-sykmeldte.no"
const val NL_OPPFOLGINGSPLAN_OPPRETTET_TEKST = "SM har opprettet plan"
const val NL_OPPFOLGINGSPLAN_OPPRETTET_LENKE  = "https://dine-sykmeldte.no"


fun EsyfovarselHendelse.dataToOppfolgingsplanNLVarselData(): OppfolgingsplanNLVarselData {
    if (data is OppfolgingsplanNLVarselData?) {
        val varseldata: OppfolgingsplanNLVarselData = this.data ?: throw NullPointerException("EsyfovarselHendelse mangler innhold i 'data'-felt")
        if (isOrgFnrNrValidFormat(varseldata.ansattFnr, varseldata.orgnummer)) {
            return varseldata
        }
        throw IllegalArgumentException("EsyfovarselHendelse har feil format i 'data'-felt")
    }
    throw IllegalArgumentException("EsyfovarselHendelse har feil datatype i 'data'-felt: ${data.toString()}")
}


private fun isOrgFnrNrValidFormat(fnr: String?, orgnr: String?): Boolean {
    return orgnr?.length == 9
        && orgnr.all { char -> char.isDigit()}
        && fnr?.length == 11
        && fnr.all { char -> char.isDigit()}
}