package no.nav.syfo.domain

import java.time.LocalDate

class HendelseMerVeiledning() : Hendelse {

    override var sykmelding: Sykmelding? = null
    override var type: HendelseType = HendelseType.MER_VEILEDNING
    override var intruffetdato: LocalDate = LocalDate.now()

    override fun withInntruffetdato(intruffetdato: LocalDate): HendelseMerVeiledning {
        this.intruffetdato = intruffetdato
        return this
    }

    override fun withSykmelding(sykmelding: Sykmelding): HendelseMerVeiledning {
        this.sykmelding = sykmelding
        return this
    }

    override fun withType(type: HendelseType): HendelseMerVeiledning {
        this.type = type
        return this
    }
}