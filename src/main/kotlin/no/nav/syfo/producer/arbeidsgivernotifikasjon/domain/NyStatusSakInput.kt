package no.nav.syfo.producer.arbeidsgivernotifikasjon.domain

import com.apollo.graphql.NyStatusSakByGrupperingsidMutation
import com.apollo.graphql.type.FutureTemporalInput
import com.apollo.graphql.type.HardDeleteUpdateInput
import com.apollo.graphql.type.NyTidStrategi
import com.apollo.graphql.type.SaksStatus
import com.apollographql.apollo.api.Optional
import no.nav.syfo.producer.arbeidsgivernotifikasjon.formatAsISO8601DateTime
import java.time.LocalDateTime

data class NyStatusSakInput(
    val grupperingsId: String,
    val merkelapp: String,
    val sakStatus: SakStatus,
    val oppdatertTidspunkt: LocalDateTime? = null,
    val oppdatertHardDeleteDateTime: LocalDateTime? = null,
)

fun NyStatusSakInput.toNyStatusSakByGrupperingsidMutation(): NyStatusSakByGrupperingsidMutation {
    return NyStatusSakByGrupperingsidMutation(
        grupperingsid = grupperingsId,
        merkelapp = merkelapp,
        nyStatus = SaksStatus.valueOf(sakStatus.name),
        tidspunkt = Optional.presentIfNotNull(oppdatertTidspunkt?.formatAsISO8601DateTime()),
        hardDelete = oppdatertHardDeleteDateTime?.let {
            Optional.present(
                HardDeleteUpdateInput(
                    nyTid = FutureTemporalInput(
                        den = Optional.present(oppdatertHardDeleteDateTime.formatAsISO8601DateTime()),
                    ),
                    strategi = NyTidStrategi.OVERSKRIV,
                )
            )
        } ?: Optional.absent(),
    )
}
