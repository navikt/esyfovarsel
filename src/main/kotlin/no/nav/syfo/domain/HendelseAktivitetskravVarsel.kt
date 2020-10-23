package no.nav.syfo.domain

import java.time.LocalDate

class HendelseAktivitetskravVarsel : Hendelse {
    override var sykmelding: Sykmelding? = null
    override var type: HendelseType = HendelseType.DEFAULT
    override var intruffetdato: LocalDate = LocalDate.now()

    override fun withInntruffetdato(intruffetdato: LocalDate): HendelseAktivitetskravVarsel {
        this.intruffetdato = intruffetdato
        return this
    }

    override fun withSykmelding(sykmelding: Sykmelding): HendelseAktivitetskravVarsel {
        this.sykmelding = sykmelding
        return this
    }

    override fun withType(type: HendelseType): HendelseAktivitetskravVarsel {
        this.type = type
        return this
    }
}
