package no.nav.syfo.db

import no.nav.syfo.db.domain.PMaksDato
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
                            AND FORELOPIG_BEREGNET_SLUTT >= current_date + INTERVAL '$maxDateLimit' DAY
                            AND FNR NOT IN 
                                (SELECT FNR 
                                FROM UTSENDT_VARSEL 
                                WHERE TYPE = 'MER_VEILEDNING' 
                                AND UTSENDT_TIDSPUNKT > NOW() - INTERVAL '90' DAY)
                                """
        .trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(queryStatement).use {
            it.executeQuery().toList { toPUtbetaling() }
        }
    }
}

fun DatabaseInterface.fetchMaksDatoByFnr(fnr: String): PMaksDato? {
    val queryStatement = """SELECT *
                            FROM UTBETALINGER AS UTBETALINGER1
                            WHERE ID =
                                (SELECT UTBETALINGER2.ID
                                FROM UTBETALINGER AS UTBETALINGER2
                                WHERE UTBETALINGER1.FNR = UTBETALINGER2.FNR
                                ORDER BY UTBETALT_TOM DESC, OPPRETTET DESC
                                LIMIT 1)
                            AND FNR = ?
                            """
        .trimIndent()

    val list = connection.use { connection ->
        connection.prepareStatement(queryStatement).use {
            it.setString(1, fnr)
            it.executeQuery().toList { toPMaksDato() }
        }
    }

    return if (list.isNotEmpty()) {
        list.first()
    } else null
}
