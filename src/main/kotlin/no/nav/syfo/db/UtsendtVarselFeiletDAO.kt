package no.nav.syfo.db

import no.nav.syfo.db.domain.PUtsendtVarselFeilet
import no.nav.syfo.domain.PersonIdent
import java.sql.Timestamp
import java.util.*

fun DatabaseInterface.fetchUtsendtBrukernotifikasjonVarselFeilet(): List<PUtsendtVarselFeilet> {
    val queryStatement = """SELECT *
                            FROM UTSENDING_VARSEL_FEILET feilet
                            WHERE feilet.KANAL = 'BRUKERNOTIFIKASJON'
                            AND feilet.UTSENDT_FORSOK_TIDSPUNKT  >= '2025-02-27'
                            AND feilet.is_resendt = FALSE
                            AND feilet.hendelsetype_navn in 
                            ('SM_DIALOGMOTE_SVAR_MOTEBEHOV', 'SM_DIALOGMOTE_INNKALT', 'SM_DIALOGMOTE_AVLYST', 'SM_DIALOGMOTE_NYTT_TID_STED', 'SM_MER_VEILEDNING')
                            AND feilet.UUID_EKSTERN_REFERANSE NOT IN (
                                SELECT EKSTERN_REF
                                FROM UTSENDT_VARSEL UV
                                WHERE KANAL = 'BRUKERNOTIFIKASJON'
                                AND UTSENDT_TIDSPUNKT  >= '2025-02-27'
                                )
                            ORDER BY feilet.utsendt_forsok_tidspunkt ASC
                            LIMIT 1000
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(queryStatement).use {
            it.executeQuery().toList { toPUtsendtVarselFeilet() }
        }
    }
}

fun DatabaseInterface.fetchUtsendtArbeidsgivernotifikasjonVarselFeilet(): List<PUtsendtVarselFeilet> {
    val queryStatement = """SELECT *
                            FROM UTSENDING_VARSEL_FEILET feilet
                            WHERE feilet.KANAL = 'ARBEIDSGIVERNOTIFIKASJON'
                            AND feilet.UTSENDT_FORSOK_TIDSPUNKT  >= '2025-05-19'
                            AND feilet.is_resendt = FALSE
                            AND feilet.hendelsetype_navn in 
                            ('NL_DIALOGMOTE_SVAR_MOTEBEHOV')
                            ORDER BY feilet.utsendt_forsok_tidspunkt ASC
                            LIMIT 1000
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(queryStatement).use {
            it.executeQuery().toList { toPUtsendtVarselFeilet() }
        }
    }
}

fun DatabaseInterface.fetchUtsendtDokDistVarselFeilet(): List<PUtsendtVarselFeilet> {
    val queryStatement = """SELECT *
                            FROM UTSENDING_VARSEL_FEILET feilet
                            WHERE feilet.KANAL = 'BREV'
                            AND feilet.UTSENDT_FORSOK_TIDSPUNKT  >= '2025-03-17'
                            AND feilet.is_resendt = FALSE
                            and feilet.journalpost_id != '0'
                            AND feilet.UUID_EKSTERN_REFERANSE NOT IN (
                                SELECT EKSTERN_REF
                                FROM UTSENDT_VARSEL UV
                                WHERE KANAL = 'BREV'
                                AND UTSENDT_TIDSPUNKT  >= '2025-03-17'
                                )
                            ORDER BY feilet.utsendt_forsok_tidspunkt ASC
                            LIMIT 500
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(queryStatement).use {
            it.executeQuery().toList { toPUtsendtVarselFeilet() }
        }
    }
}

fun DatabaseInterface.storeUtsendtVarselFeilet(varsel: PUtsendtVarselFeilet) {
    val insertStatement = """INSERT INTO UTSENDING_VARSEL_FEILET (
        uuid,
        uuid_ekstern_referanse,
        arbeidstaker_fnr,   
        narmesteleder_fnr,
        orgnummer,
        hendelsetype_navn,
        arbeidsgivernotifikasjon_merkelapp,
        brukernotifikasjoner_melding_type,
        journalpost_id,
        kanal,
        feilmelding,
        utsendt_forsok_tidspunkt,
        is_forced_letter,
        is_resendt,
        resendt_tidspunkt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(insertStatement).use {
            it.setObject(1, UUID.fromString(varsel.uuid))
            it.setString(2, varsel.uuidEksternReferanse)
            it.setString(3, varsel.arbeidstakerFnr)
            it.setString(4, varsel.narmesteLederFnr)
            it.setString(5, varsel.orgnummer)
            it.setString(6, varsel.hendelsetypeNavn)
            it.setString(7, varsel.arbeidsgivernotifikasjonMerkelapp)
            it.setString(8, varsel.brukernotifikasjonerMeldingType)
            it.setString(9, varsel.journalpostId)
            it.setString(10, varsel.kanal)
            it.setString(11, varsel.feilmelding)
            it.setTimestamp(12, Timestamp.valueOf(varsel.utsendtForsokTidspunkt))
            it.setBoolean(13, varsel.isForcedLetter ?: false)
            it.setBoolean(14, varsel.isResendt ?: false)
            it.setTimestamp(15, null)
            it.executeUpdate()
        }

        connection.commit()
    }
}

fun DatabaseInterface.updateUtsendtVarselFeiletToResendt(uuid: String) {
    val updateStatement = """UPDATE UTSENDING_VARSEL_FEILET
                   SET is_resendt = TRUE,
                       resendt_tidspunkt = CURRENT_TIMESTAMP
                   WHERE uuid = ?
    """.trimMargin()

    return connection.use { connection ->
        connection.prepareStatement(updateStatement).use {
            it.setObject(1, UUID.fromString(uuid))
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterface.deleteUtsendtVarselFeiletByFnr(fnr: PersonIdent) {
    val updateStatement = """DELETE FROM UTSENDING_VARSEL_FEILET
                   WHERE arbeidstaker_fnr = ?
    """.trimMargin()

    return connection.use { connection ->
        connection.prepareStatement(updateStatement).use {
            it.setString(1, fnr.value)
            it.executeUpdate()
        }
        connection.commit()
    }
}
