package no.nav.syfo.db

import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.domain.PersonIdent

fun DatabaseInterface.storeUtsendtVarsel(PUtsendtVarsel: PUtsendtVarsel) {
    val insertStatement = """INSERT INTO UTSENDT_VARSEL (
        uuid,
        narmesteLeder_fnr,
        fnr,   
        orgnummer,
        type,
        kanal,
        utsendt_tidspunkt,
        ekstern_ref,
        arbeidsgivernotifikasjon_merkelapp, 
        is_forced_letter
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()
/*,
        journalpost_id
        , ?
        */
    connection.use { connection ->
        connection.prepareStatement(insertStatement).use {
            it.setObject(1, UUID.fromString(PUtsendtVarsel.uuid))
            it.setString(2, PUtsendtVarsel.narmesteLederFnr)
            it.setString(3, PUtsendtVarsel.fnr)
            it.setString(4, PUtsendtVarsel.orgnummer)
            it.setString(5, PUtsendtVarsel.type)
            it.setString(6, PUtsendtVarsel.kanal)
            it.setTimestamp(7, Timestamp.valueOf(PUtsendtVarsel.utsendtTidspunkt))
            it.setString(8, PUtsendtVarsel.eksternReferanse)
            it.setString(9, PUtsendtVarsel.arbeidsgivernotifikasjonMerkelapp)
            it.setBoolean(10, PUtsendtVarsel.isForcedLetter)
//            it.setString(11, PUtsendtVarsel.journalpostId)
            it.executeUpdate()
        }

        connection.commit()
    }
}

fun DatabaseInterface.fetchUferdigstilteVarsler(
    fnr: PersonIdent,
): List<PUtsendtVarsel> {
    val queryStatement = """SELECT *
                            FROM UTSENDT_VARSEL
                            WHERE fnr = ?
                            AND ferdigstilt_tidspunkt is null
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(queryStatement).use {
            it.setString(1, fnr.value)
            it.executeQuery().toList { toPUtsendtVarsel() }
        }
    }
}

fun DatabaseInterface.fetchAlleUferdigstilteAktivitetspliktVarsler(
): List<PUtsendtVarsel> {
    val queryStatement = """SELECT *
                            FROM UTSENDT_VARSEL
                            WHERE type = 'SM_AKTIVITETSPLIKT'
                            AND kanal = 'BRUKERNOTIFIKASJON'
                            AND ferdigstilt_tidspunkt is null
                            AND is_forced_letter is false OR is_forced_letter is null
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(queryStatement).use {
            it.executeQuery().toList { toPUtsendtVarsel() }
        }
    }
}

fun DatabaseInterface.setUferdigstiltUtsendtVarselToForcedLEtter(eksternRef: String): Int {
    val updateStatement = """UPDATE UTSENDT_VARSEL
                   SET is_forced_letter = ?
                   WHERE EKSTERN_REF = ?
    """.trimMargin()

    return connection.use { connection ->
        val rowsUpdated = connection.prepareStatement(updateStatement).use {
            it.setBoolean(1, true)
            it.setString(2, eksternRef)
            it.executeUpdate()
        }
        connection.commit()
        rowsUpdated
    }
}

fun DatabaseInterface.fetchUtsendtVarselByFnr(fnr: String): List<PUtsendtVarsel> {
    val queryStatement = """SELECT *
                            FROM UTSENDT_VARSEL
                            WHERE fnr = ?
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(queryStatement).use {
            it.setString(1, fnr)
            it.executeQuery().toList { toPUtsendtVarsel() }
        }
    }
}

fun DatabaseInterface.setUtsendtVarselToFerdigstilt(eksternRef: String): Int {
    val now = Timestamp.valueOf(LocalDateTime.now())
    val updateStatement = """UPDATE UTSENDT_VARSEL
                   SET ferdigstilt_tidspunkt = ?
                   WHERE EKSTERN_REF = ?
    """.trimMargin()

    return connection.use { connection ->
        val rowsUpdated = connection.prepareStatement(updateStatement).use {
            it.setTimestamp(1, now)
            it.setString(2, eksternRef)
            it.executeUpdate()
        }
        connection.commit()
        rowsUpdated
    }
}

fun DatabaseInterface.deleteUtsendtVarselByFnr(fnr: PersonIdent) {
    val updateStatement = """DELETE FROM UTSENDT_VARSEL
                   WHERE fnr = ?
    """.trimMargin()

    return connection.use { connection ->
        connection.prepareStatement(updateStatement).use {
            it.setString(1, fnr.value)
            it.executeUpdate()
        }
        connection.commit()
    }
}

