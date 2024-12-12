package no.nav.syfo.db.domain

import com.apollo.graphql.type.SaksStatus
import java.time.LocalDateTime

data class PSakInput(
    val id: String,
    val narmestelederId: String,
    val grupperingsid: String,
    val merkelapp: String,
    val virksomhetsnummer: String,
    val narmesteLederFnr: String,
    val ansattFnr: String,
    val tittel: String,
    val tilleggsinformasjon: String? = null,
    val lenke: String,
    val initiellStatus: SaksStatus,
    val nesteSteg: String? = null,
    val overstyrStatustekstMed: String? = null,
    val hardDeleteDate: LocalDateTime,
)
