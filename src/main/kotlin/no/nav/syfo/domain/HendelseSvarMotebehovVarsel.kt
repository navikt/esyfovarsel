package no.nav.syfo.domain

import java.time.LocalDate

class HendelseSvarMotebehovVarsel : Hendelse {
    override var sykmelding: Sykmelding? = null
    override var type: HendelseType = HendelseType.DEFAULT
    override var intruffetdato: LocalDate = LocalDate.now()

    override fun withInntruffetdato(intruffetdato: LocalDate): HendelseSvarMotebehovVarsel {
        this.intruffetdato = intruffetdato
        return this
    }

    override fun withSykmelding(sykmelding: Sykmelding): HendelseSvarMotebehovVarsel {
        this.sykmelding = sykmelding
        return this
    }

    override fun withType(type: HendelseType): HendelseSvarMotebehovVarsel {
        this.type = type
        return this
    }
}
