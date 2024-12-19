package no.nav.syfo.db

import com.apollo.graphql.type.SaksStatus
import no.nav.syfo.db.domain.PSakInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NySakInput
import java.sql.ResultSet
import java.sql.Timestamp

fun DatabaseInterface.storeArbeidsgivernotifikasjonerSak(sakInput: NySakInput) {
    val insertStatement = """
        INSERT INTO ARBEIDSGIVERNOTIFIKASJONER_SAK (
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
            hardDeleteDate
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(insertStatement).use { preparedStatement ->
            preparedStatement.setString(1, sakInput.grupperingsid)
            preparedStatement.setString(2, sakInput.merkelapp)
            preparedStatement.setString(3, sakInput.virksomhetsnummer)
            preparedStatement.setString(4, sakInput.narmesteLederFnr)
            preparedStatement.setString(5, sakInput.ansattFnr)
            preparedStatement.setString(6, sakInput.tittel)
            preparedStatement.setString(7, sakInput.tilleggsinformasjon)
            preparedStatement.setString(8, sakInput.lenke)
            preparedStatement.setString(9, sakInput.initiellStatus.name)
            preparedStatement.setString(10, sakInput.nesteSteg)
            preparedStatement.setTimestamp(11, Timestamp.valueOf(sakInput.tidspunkt))
            preparedStatement.setString(12, sakInput.overstyrStatustekstMed)
            preparedStatement.setTimestamp(13, Timestamp.valueOf(sakInput.hardDeleteDate))

            preparedStatement.executeUpdate()
        }

        connection.commit()
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
    hardDeleteDate = getTimestamp("hardDeleteDate").toLocalDateTime()
)