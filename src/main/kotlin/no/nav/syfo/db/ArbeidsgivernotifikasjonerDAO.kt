package no.nav.syfo.db

import com.apollo.graphql.type.SaksStatus
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.SQLIntegrityConstraintViolationException
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID
import no.nav.syfo.db.domain.PKalenderInput
import no.nav.syfo.db.domain.PSakInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.KalenderTilstand
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NySakInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.SakStatus

fun DatabaseInterface.storeArbeidsgivernotifikasjonerSak(sakInput: NySakInput): String {
    val uuid = UUID.randomUUID()
    val insertStatement =
        """
        INSERT INTO ARBEIDSGIVERNOTIFIKASJONER_SAK (
            id,
            narmestelederId,
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
            overstyrStatustekstMed,
            hardDeleteDate,
            opprettet
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

    return try {
        connection.use { connection ->
            connection.prepareStatement(insertStatement).use { preparedStatement ->
                preparedStatement.setObject(1, uuid)
                preparedStatement.setString(2, sakInput.narmestelederId)
                preparedStatement.setString(3, sakInput.grupperingsid)
                preparedStatement.setString(4, sakInput.merkelapp)
                preparedStatement.setString(5, sakInput.virksomhetsnummer)
                preparedStatement.setString(6, sakInput.narmesteLederFnr)
                preparedStatement.setString(7, sakInput.ansattFnr)
                preparedStatement.setString(8, sakInput.tittel)
                preparedStatement.setString(9, sakInput.tilleggsinformasjon)
                preparedStatement.setString(10, sakInput.lenke)
                preparedStatement.setString(11, sakInput.initiellStatus.name)
                preparedStatement.setString(12, sakInput.nesteSteg)
                preparedStatement.setString(13, sakInput.overstyrStatustekstMed)
                preparedStatement.setTimestamp(14, Timestamp.valueOf(sakInput.hardDeleteDate))
                preparedStatement.setTimestamp(15, Timestamp.valueOf(LocalDateTime.now()))

                preparedStatement.executeUpdate()
            }
            connection.commit()
        }
        uuid.toString()
    } catch (e: SQLIntegrityConstraintViolationException) {
        connection.rollback()
        log.error("Integrity constraint violation: ${e.message}")
        throw e
    } catch (e: SQLException) {
        connection.rollback()
        log.error("Database error occurred: ${e.message}")
        throw e
    }
}

fun DatabaseInterface.updateArbeidsgivernotifikasjonerSakStatus(
    sakId: String,
    sakStatus: SakStatus,
) {
    val updateStatement =
        """
        UPDATE ARBEIDSGIVERNOTIFIKASJONER_SAK
        SET initiellStatus = ?
        WHERE id = ?
        """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(updateStatement).use { preparedStatement ->
            preparedStatement.setString(1, sakStatus.name)
            preparedStatement.setObject(2, UUID.fromString(sakId))

            preparedStatement.executeUpdate()
        }

        connection.commit()
    }
}

fun DatabaseInterface.getPaagaaendeArbeidsgivernotifikasjonerSak(
    narmestelederId: String,
    merkelapp: String,
): PSakInput? {
    val queryStatement =
        """
        SELECT *
        FROM ARBEIDSGIVERNOTIFIKASJONER_SAK
        WHERE narmestelederId = ?
        AND merkelapp = ?
        AND hardDeleteDate > CURRENT_TIMESTAMP
        AND initiellStatus not in ('FERDIG', 'AVHOLDT')
        ORDER BY opprettet DESC
        """.trimIndent()

    val listOfSak =
        connection.use { connection ->
            connection.prepareStatement(queryStatement).use {
                it.setString(1, narmestelederId)
                it.setString(2, merkelapp)
                it.executeQuery().toList { toPSakInput() }
            }
        }
    return listOfSak.firstOrNull()
}

fun ResultSet.toPSakInput() =
    PSakInput(
        id = getString("id"),
        narmestelederId = getString("narmestelederId"),
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
        overstyrStatustekstMed = getString("overstyrStatustekstMed"),
        hardDeleteDate = getTimestamp("hardDeleteDate").toLocalDateTime(),
    )

fun DatabaseInterface.storeArbeidsgivernotifikasjonerKalenderavtale(kalenderInput: PKalenderInput): String {
    val uuid = UUID.randomUUID()
    val insertStatement =
        """
        INSERT INTO ARBEIDSGIVERNOTIFIKASJONER_KALENDERAVTALE (
            id,
            eksternId,
            sakId,
            grupperingsid,
            merkelapp,
            kalenderId,
            tekst,
            startTidspunkt,
            sluttTidspunkt,
            kalenderavtaleTilstand,
            hardDeleteDate,
            opprettet
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(insertStatement).use { preparedStatement ->
            preparedStatement.setObject(1, uuid)
            preparedStatement.setString(2, kalenderInput.eksternId)
            preparedStatement.setString(3, kalenderInput.sakId)
            preparedStatement.setString(4, kalenderInput.grupperingsid)
            preparedStatement.setString(5, kalenderInput.merkelapp)
            preparedStatement.setString(6, kalenderInput.kalenderId)
            preparedStatement.setString(7, kalenderInput.tekst)
            preparedStatement.setTimestamp(8, Timestamp.valueOf(kalenderInput.startTidspunkt))
            preparedStatement.setTimestamp(9, kalenderInput.sluttTidspunkt?.let { Timestamp.valueOf(it) })
            preparedStatement.setString(10, kalenderInput.kalenderavtaleTilstand.name)
            preparedStatement.setTimestamp(11, kalenderInput.hardDeleteDate?.let { Timestamp.valueOf(it) })
            preparedStatement.setTimestamp(12, Timestamp.valueOf(LocalDateTime.now()))

            preparedStatement.executeUpdate()
        }

        connection.commit()
        return uuid.toString()
    }
}

fun DatabaseInterface.getArbeidsgivernotifikasjonerKalenderavtale(sakId: String): PKalenderInput? {
    val queryStatement =
        """
        SELECT *
        FROM ARBEIDSGIVERNOTIFIKASJONER_KALENDERAVTALE
        WHERE sakId = ?
        ORDER BY opprettet DESC
        """.trimIndent()

    val listOfSak =
        connection.use { connection ->
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

fun ResultSet.toPKalenderInput() =
    PKalenderInput(
        sakId = getString("sakId"),
        eksternId = getString("eksternId"),
        grupperingsid = getString("grupperingsid"),
        merkelapp = getString("merkelapp"),
        kalenderId = getString("kalenderId"),
        tekst = getString("tekst"),
        startTidspunkt = getTimestamp("startTidspunkt").toLocalDateTime(),
        sluttTidspunkt = getTimestamp("sluttTidspunkt")?.toLocalDateTime(),
        kalenderavtaleTilstand = KalenderTilstand.valueOf(getString("kalenderavtaleTilstand")),
        hardDeleteDate = getTimestamp("hardDeleteDate")?.toLocalDateTime(),
    )
