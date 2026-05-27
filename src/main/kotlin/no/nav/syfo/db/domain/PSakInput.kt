package no.nav.syfo.db.domain

import com.apollo.graphql.type.SaksStatus
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.MottakerType
import java.time.LocalDateTime

data class PSakInput(
    val id: String,
    val narmestelederId: String?,
    val grupperingsid: String,
    val merkelapp: String,
    val virksomhetsnummer: String,
    val narmesteLederFnr: String?,
    val ansattFnr: String,
    val type: String? = null,
    val eksternSakId: String? = null,
    val ressursId: String? = null,
    val tittel: String,
    val tilleggsinformasjon: String? = null,
    val lenke: String?,
    val mottakerType: MottakerType,
    val initiellStatus: SaksStatus,
    val nesteSteg: String? = null,
    val overstyrStatustekstMed: String? = null,
    val hardDeleteDate: LocalDateTime,
)
