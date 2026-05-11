package no.nav.syfo.db

import io.kotest.core.spec.style.DescribeSpec
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP
import no.nav.syfo.db.domain.Kanal
import no.nav.syfo.db.domain.PUtsendtVarselFeilet
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.testutil.EmbeddedDatabase
import org.amshove.kluent.should
import java.time.LocalDateTime
import java.util.UUID

class UtsendtVarselFeiletDAOSpek :
    DescribeSpec({
        describe("UtsendtVarselFeiletDAOSpek") {
            val embeddedDatabase = EmbeddedDatabase()

            beforeTest {
                embeddedDatabase.dropData()
            }

            it("Store ikke-utsendt varsel til NL i database") {
                val fnr = "12121212121"
                val hendelseJson = """{"type":"NL_DIALOGMOTE_NYTT_TID_STED","data":{"foo":"bar"}}"""
                embeddedDatabase.storeUtsendtVarselFeilet(ikkeUtsendtVarsel(fnr, hendelseJson))
                embeddedDatabase.skalHaLagretIkkeUtsendtVarsel(
                    HendelseType.NL_DIALOGMOTE_NYTT_TID_STED,
                    Kanal.DINE_SYKMELDTE,
                    fnr,
                    hendelseJson,
                )
            }
        }
    })

private fun ikkeUtsendtVarsel(
    fnr: String,
    hendelseJson: String? = null,
): PUtsendtVarselFeilet =
    PUtsendtVarselFeilet(
        uuid = UUID.randomUUID().toString(),
        uuidEksternReferanse = "00000",
        arbeidstakerFnr = fnr,
        narmesteLederFnr = "01010101010",
        orgnummer = null,
        hendelsetypeNavn = HendelseType.NL_DIALOGMOTE_NYTT_TID_STED.name,
        arbeidsgivernotifikasjonMerkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
        brukernotifikasjonerMeldingType = null,
        journalpostId = null,
        kanal = Kanal.DINE_SYKMELDTE.name,
        feilmelding = "Achtung!",
        utsendtForsokTidspunkt = LocalDateTime.now(),
        isForcedLetter = false,
        hendelseJson = hendelseJson,
    )

private fun DatabaseInterface.skalHaLagretIkkeUtsendtVarsel(
    type: HendelseType,
    kanal: Kanal,
    fnr: String,
    hendelseJson: String?,
) = this.should("Skal ha lagret ikke-utsendt varsel av type ${type.name} ") {
    this
        .fetchUtsendtVarselFeiletByFnr(fnr)
        .filter { it.hendelsetypeNavn == type.name }
        .any { it.kanal.equals(kanal.name) && it.hendelseJson == hendelseJson }
}

fun DatabaseInterface.fetchUtsendtVarselFeiletByFnr(fnr: String): List<PUtsendtVarselFeilet> {
    val queryStatement =
        """
        SELECT *
        FROM UTSENDING_VARSEL_FEILET
        WHERE arbeidstaker_fnr = ?
        """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(queryStatement).use {
            it.setString(1, fnr)
            it.executeQuery().toList { toPUtsendtVarselFeilet() }
        }
    }
}
