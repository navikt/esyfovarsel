package no.nav.syfo.domain

import java.time.LocalDate

class HendelseMerVeiledning: Hendelse() {
    override val type: Hendelsestype = Hendelsestype.MER_VEILEDNING
    override val inntruffetdato: LocalDate = LocalDate.now()
}