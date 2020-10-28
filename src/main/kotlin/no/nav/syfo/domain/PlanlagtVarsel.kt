package no.nav.syfo.domain

import java.time.LocalDate

class PlanlagtVarsel {
    var type: HendelseType = HendelseType.DEFAULT
    var ressursId: String = ""
    var sendingsdato: LocalDate = LocalDate.now()
    var sykmelding: Sykmelding? = null

    fun withType(type: HendelseType): PlanlagtVarsel {
        this.type = type
        return this
    }

    fun withRessursId(ressursId: String): PlanlagtVarsel {
        this.ressursId = ressursId
        return this
    }

    fun withSendingsdato(sendingsdato: LocalDate): PlanlagtVarsel {
        this.sendingsdato = sendingsdato
        return this
    }

    fun withSykmelding(sykmelding: Sykmelding): PlanlagtVarsel {
        this.sykmelding = sykmelding
        return this
    }
}
