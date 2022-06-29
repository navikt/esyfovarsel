package no.nav.syfo.service

import no.nav.syfo.*
import no.nav.syfo.consumer.arbeidsgiverNotifikasjonProdusent.ArbeidsgiverNotifikasjonProdusent
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.kafka.dinesykmeldte.domain.ArbeidsgiverNotifikasjon
import java.time.LocalDateTime
import java.util.*

class ArbeidsgiverNotifikasjonService(val arbeidsgiverNotifikasjonProdusent: ArbeidsgiverNotifikasjonProdusent) {

    fun sendNotifikasjon(
        varselType: VarselType,
        varselId: String?,
        orgnummer: String,
        url: String,
        narmesteLederFnr: String,
        ansattFnr: String,
        narmesteLederEpostadresse: String,
        hardDeletDate: LocalDateTime,
    ) {
        val arbeidsgiverNotifikasjon = getNotifikasjonFromType(varselType, varselId, orgnummer, url, narmesteLederFnr, ansattFnr, narmesteLederEpostadresse, hardDeletDate)
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
        hardDeleteDate: LocalDateTime
    ): ArbeidsgiverNotifikasjon? {
        val uuid = varselId ?: UUID.randomUUID().toString()

        return when (varselType) {
            VarselType.AKTIVITETSKRAV -> ArbeidsgiverNotifikasjon(
                uuid,
                orgnummer,
                url,
                narmesteLederFnr,
                ansattFnr,
                ARBEIDSGIVERNOTIFIKASJON_AKTIVITETSKRAV_MESSAGE_TEXT,
                narmesteLederEpostadresse,
                ARBEIDSGIVERNOTIFIKASJON_AKTIVITETSKRAV_EMAIL_TITLE,
                ARBEIDSGIVERNOTIFIKASJON_AKTIVITETSKRAV_EMAIL_BODY_START + url + ARBEIDSGIVERNOTIFIKASJON_AKTIVITETSKRAV_EMAIL_BODY_END,
                hardDeleteDate,
            )
            VarselType.SVAR_MOTEBEHOV -> ArbeidsgiverNotifikasjon(
                uuid,
                orgnummer,
                url,
                narmesteLederFnr,
                ansattFnr,
                ARBEIDSGIVERNOTIFIKASJON_SVAR_MOTEBEHOV_MESSAGE_TEXT,
                narmesteLederEpostadresse,
                ARBEIDSGIVERNOTIFIKASJON_SVAR_MOTEBEHOV_EMAIL_TITLE,
                ARBEIDSGIVERNOTIFIKASJON_SVAR_MOTEBEHOV_EMAIL_BODY,
                hardDeleteDate
            )
            else -> null
        }
    }
}
