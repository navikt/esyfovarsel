package no.nav.syfo.service

import no.nav.syfo.consumer.arbeidsgiverNotifikasjonProdusent.*
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.kafka.dinesykmeldte.domain.ArbeidsgiverNotifikasjon
import java.util.*

class ArbeidsgiverNotifikasjonService(val arbeidsgiverNotifikasjonProdusent: ArbeidsgiverNotifikasjonProdusent) {

    fun sendNotifikasjon(
        varselType: VarselType,
        varselId: String?,
        orgnummer: String,
        url: String,
        narmesteLederFnr: String,
        ansattFnr: String,
        narmesteLederEpostadresse: String
    ) {
        val arbeidsgiverNotifikasjon = getNotifikasjonFromType(varselType, varselId, orgnummer, url, narmesteLederFnr, ansattFnr, narmesteLederEpostadresse)
        arbeidsgiverNotifikasjonProdusent.createNewNotificationForArbeidsgiver(arbeidsgiverNotifikasjon!!)
    }

    private fun getNotifikasjonFromType(
        varselType: VarselType,
        varselId: String?,
        orgnummer: String,
        url: String,
        narmesteLederFnr: String,
        ansattFnr: String,
        narmesteLederEpostadresse: String,
    ): ArbeidsgiverNotifikasjon? {
        val uuid = varselId ?: UUID.randomUUID().toString()

        return when (varselType) {
            VarselType.AKTIVITETSKRAV -> ArbeidsgiverNotifikasjon(
                uuid,
                orgnummer,
                url,
                narmesteLederFnr,
                ansattFnr,
                AKTIVITETSKRAV_MESSAGE_TEXT,
                narmesteLederEpostadresse,
                AKTIVITETSKRAV_EMAIL_TITLE,
                AKTIVITETSKRAV_EMAIL_BODY_START + url + AKTIVITETSKRAV_EMAIL_BODY_END
            )
            VarselType.SVAR_MOTEBEHOV -> ArbeidsgiverNotifikasjon(
                uuid,
                orgnummer,
                url,
                narmesteLederFnr,
                ansattFnr,
                SVAR_MOTEBEHOV_MESSAGE_TEXT,
                narmesteLederEpostadresse,
                SVAR_MOTEBEHOV_EMAIL_TITLE,
                SVAR_MOTEBEHOV_EMAIL_BODY
            )
            else -> null
        }
    }
}
