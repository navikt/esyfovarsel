package no.nav.syfo.kafka.varselbus

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.kafka.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.kafka.varselbus.domain.EsyfovarselHendelse
import no.nav.syfo.kafka.varselbus.domain.OppfolgingsplanNLVarselData
import no.nav.syfo.kafka.varselbus.domain.toDineSykmeldteHendelse
import org.apache.commons.cli.MissingArgumentException
import java.io.IOException
import java.time.OffsetDateTime

class OppfolgingsplanVarsler {

    fun varselSendtOppfolgingsplanNL(varselHendelse: EsyfovarselHendelse): DineSykmeldteVarsel {
        val varseldata = varselHendelse.dataToOppfolgingsplanNLVarselData()
        return DineSykmeldteVarsel(
            varseldata.ansattFnr,
            varseldata.orgnummer,
            varselHendelse.type.toDineSykmeldteHendelse().toString(),
            null,
            NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING_TEKST,
            OffsetDateTime.now().plusWeeks(4L)
        )
    }

    fun varselOpprettetOppfolgingsplanNL(varselHendelse: EsyfovarselHendelse): DineSykmeldteVarsel {
        val varseldata = varselHendelse.dataToOppfolgingsplanNLVarselData()
        return DineSykmeldteVarsel(
            varseldata.ansattFnr,
            varseldata.orgnummer,
            varselHendelse.type.toDineSykmeldteHendelse().toString(),
            null,
            NL_OPPFOLGINGSPLAN_OPPRETTET_TEKST,
            OffsetDateTime.now().plusWeeks(4L)
        )
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
