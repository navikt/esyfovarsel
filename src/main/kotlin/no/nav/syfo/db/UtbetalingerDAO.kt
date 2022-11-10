package no.nav.syfo.db

import no.nav.syfo.db.domain.PUtbetaling


fun DatabaseInterface.fetchMerVeiledningVarslerToSend(): List<PUtbetaling> {
    val gjenstaendeSykedagerLimit = 80
    val maxDateLimit = 14
    val queryStatement = """SELECT ID, FNR, UTBETALT_TOM, FORELOPIG_BEREGNET_SLUTT, GJENSTAENDE_SYKEDAGER, OPPRETTET
                            FROM UTBETALINGER AS UTBETALINGER1
                            WHERE ID =
                                (SELECT UTBETALINGER2.ID
                                FROM UTBETALINGER AS UTBETALINGER2
                                WHERE UTBETALINGER1.FNR = UTBETALINGER2.FNR
                                ORDER BY UTBETALT_TOM DESC, OPPRETTET DESC
                                LIMIT 1)
                            AND GJENSTAENDE_SYKEDAGER < $gjenstaendeSykedagerLimit
                            AND FORELOPIG_BEREGNET_SLUTT > NOW() + INTERVAL '$maxDateLimit' DAY;"""
        .trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(queryStatement).use {
            it.executeQuery().toList { toPUtbetaling() }
        }
    }
}
