package no.nav.syfo.db

import com.apollo.graphql.type.SaksStatus
import no.nav.syfo.db.domain.PKalenderInput
import no.nav.syfo.db.domain.PSakInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.KalenderTilstand
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NySakInput
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*

fun DatabaseInterface.storeArbeidsgivernotifikasjonerSak(sakInput: NySakInput): String {
    val uuid = UUID.randomUUID()
    val insertStatement = """
        INSERT INTO ARBEIDSGIVERNOTIFIKASJONER_SAK (
            id,
            grupperingsid,
            merkelapp,
            virksomhetsnummer,
            narmesteLederFnr,
            ansattFnr,
            tittel,
            tilleggsinformasjon,
            lenke,
            initiellStatus,
            nesteSteg,
            tidspunkt,
            overstyrStatustekstMed,
            hardDeleteDate,
            opprettet
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(insertStatement).use { preparedStatement ->
            preparedStatement.setObject(1, uuid)
            preparedStatement.setString(2, sakInput.grupperingsid)
            preparedStatement.setString(3, sakInput.merkelapp)
            preparedStatement.setString(4, sakInput.virksomhetsnummer)
            preparedStatement.setString(5, sakInput.narmesteLederFnr)
            preparedStatement.setString(6, sakInput.ansattFnr)
            preparedStatement.setString(7, sakInput.tittel)
            preparedStatement.setString(8, sakInput.tilleggsinformasjon)
            preparedStatement.setString(9, sakInput.lenke)
            preparedStatement.setString(10, sakInput.initiellStatus.name)
            preparedStatement.setString(11, sakInput.nesteSteg)
            preparedStatement.setTimestamp(12, Timestamp.valueOf(sakInput.tidspunkt))
            preparedStatement.setString(13, sakInput.overstyrStatustekstMed)
            preparedStatement.setTimestamp(14, Timestamp.valueOf(sakInput.hardDeleteDate))
            preparedStatement.setTimestamp(15, Timestamp.valueOf(LocalDateTime.now()))

            preparedStatement.executeUpdate()
        }

        connection.commit()

        return uuid.toString()
    }
}

fun DatabaseInterface.getArbeidsgivernotifikasjonerSak(
    grupperingsid: String,
    merkelapp: String
): PSakInput? {
    val queryStatement = """SELECT *
                            FROM ARBEIDSGIVERNOTIFIKASJONER_SAK
                            WHERE grupperingsid = ?
                            AND merkelapp = ?
                            AND hardDeleteDate > CURRENT_TIMESTAMP
    """.trimIndent()

    val listOfSak = connection.use { connection ->
        connection.prepareStatement(queryStatement).use {
            it.setString(1, grupperingsid)
            it.setString(2, merkelapp)
            it.executeQuery().toList { toPSakInput() }
        }
    }
    return if (listOfSak.isNotEmpty()) {
        listOfSak.first()
    } else {
        null
    }
}

fun ResultSet.toPSakInput() = PSakInput(
    sakId = getString("id"),
    grupperingsid = getString("grupperingsid"),
    merkelapp = getString("merkelapp"),
    virksomhetsnummer = getString("virksomhetsnummer"),
    narmesteLederFnr = getString("narmesteLederFnr"),
    ansattFnr = getString("ansattFnr"),
    tittel = getString("tittel"),
    tilleggsinformasjon = getString("tilleggsinformasjon"),
    lenke = getString("lenke"),
    initiellStatus = SaksStatus.valueOf(getString("initiellStatus")),
    nesteSteg = getString("nesteSteg"),
    tidspunkt = getTimestamp("tidspunkt").toLocalDateTime(),
    overstyrStatustekstMed = getString("overstyrStatustekstMed"),
    hardDeleteDate = getTimestamp("hardDeleteDate").toLocalDateTime(),
)

fun DatabaseInterface.storeArbeidsgivernotifikasjonerKalenderavtale(
    kalenderInput: PKalenderInput
): String {
    val uuid = UUID.randomUUID()
    val insertStatement = """
        INSERT INTO ARBEIDSGIVERNOTIFIKASJONER_KALENDERAVTALE (
            id,
            eksternId,
            sakId,
            kalenderId,
            tekst,
            startTidspunkt,
            sluttTidspunkt,
            kalenderavtaleTilstand,
            hardDeleteDate,
            opprettet
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(insertStatement).use { preparedStatement ->
            preparedStatement.setObject(1, uuid)
            preparedStatement.setString(2, kalenderInput.eksternId)
            preparedStatement.setString(3, kalenderInput.sakId)
            preparedStatement.setString(4, kalenderInput.kalenderId)
            preparedStatement.setString(5, kalenderInput.tekst)
            preparedStatement.setTimestamp(6, Timestamp.valueOf(kalenderInput.startTidspunkt))
            preparedStatement.setTimestamp(7, kalenderInput.sluttTidspunkt?.let { Timestamp.valueOf(it) })
            preparedStatement.setString(8, kalenderInput.kalenderavtaleTilstand.name)
            preparedStatement.setTimestamp(9, Timestamp.valueOf(kalenderInput.hardDeleteDate))
            preparedStatement.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()))

            preparedStatement.executeUpdate()
        }

        connection.commit()
        return uuid.toString()
    }
}

fun DatabaseInterface.getArbeidsgivernotifikasjonerKalenderavtale(
    sakId: String,
): PKalenderInput? {
    val queryStatement = """SELECT *
                            FROM ARBEIDSGIVERNOTIFIKASJONER_KALENDERAVTALE
                            WHERE sakId = ?
                            AND hardDeleteDate > CURRENT_TIMESTAMP
                            ORDER BY opprettet DESC
    """.trimIndent()

    val listOfSak = connection.use { connection ->
        connection.prepareStatement(queryStatement).use {
            it.setString(1, sakId)
            it.executeQuery().toList { toPKalenderInput() }
        }
    }
    return if (listOfSak.isNotEmpty()) {
        listOfSak.first()
    } else {
        null
    }
}

fun ResultSet.toPKalenderInput() = PKalenderInput(
    eksternId = getString("eksternId"),
    sakId = getString("sakId"),
    kalenderId = getString("kalenderId"),
    tekst = getString("tekst"),
    startTidspunkt = getTimestamp("startTidspunkt").toLocalDateTime(),
    sluttTidspunkt = getTimestamp("sluttTidspunkt")?.toLocalDateTime(),
    kalenderavtaleTilstand = KalenderTilstand.valueOf(getString("kalenderavtaleTilstand")),
    hardDeleteDate = getTimestamp("hardDeleteDate").toLocalDateTime()
)