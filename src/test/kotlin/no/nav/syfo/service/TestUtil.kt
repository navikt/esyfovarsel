package no.nav.syfo.service

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList

fun DatabaseInterface.fetchSpleisUtbetalingByFnr(fnr: String) =
    connection.use { connection ->
        connection.prepareStatement("""SELECT *  FROM UTBETALING_SPLEIS  WHERE FNR = ?""".trimIndent()).use {
            it.setString(1, fnr)
            it.executeQuery().toList { getInt("GJENSTAENDE_SYKEDAGER") }
        }
    }
