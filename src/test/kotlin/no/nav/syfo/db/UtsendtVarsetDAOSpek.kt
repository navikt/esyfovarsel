package no.nav.syfo.db

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.util.*
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP
import no.nav.syfo.db.domain.Kanal
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.domain.PUtsendtVarselFeilet
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.testutil.EmbeddedDatabase
import org.amshove.kluent.should

class UtsendtVarselDAOSpek : DescribeSpec({
    describe("UtsendtVarselDAOSpek") {
        val embeddedDatabase = EmbeddedDatabase()

        beforeTest {
            embeddedDatabase.dropData()
        }

        it("Store ikke-utsendt varsel til NL i database") {
            val fnr = "12121212121"
            embeddedDatabase.storeUtsendtVarselFeilet(ikkeUtsendtVarsel(fnr))
            embeddedDatabase.skalHaLagretIkkeUtsendtVarsel(
                HendelseType.NL_DIALOGMOTE_NYTT_TID_STED,
                Kanal.DINE_SYKMELDTE,
                fnr
            )
        }

        it("Should not fetch ferdigstilt aktivitetsplikt varsel") {
            val uuid = UUID.randomUUID().toString()
            val utsendtVarsel6 = PUtsendtVarsel(
                uuid = uuid,
                fnr = "22121212121",
                aktorId = null,
                narmesteLederFnr = null,
                orgnummer = null,
                type = "SM_AKTIVITETSPLIKT",
                kanal = "BRUKERNOTIFIKASJON",
                utsendtTidspunkt = LocalDateTime.now().minusDays(3),
                planlagtVarselId = null,
                eksternReferanse = "123",
                ferdigstiltTidspunkt = null,
                arbeidsgivernotifikasjonMerkelapp = null,
                isForcedLetter = false,
                journalpostId = "666"
            )

            embeddedDatabase.storeUtsendtVarsel(utsendtVarsel6)
            embeddedDatabase.setUtsendtVarselToFerdigstilt("123")

            val res = embeddedDatabase.fetchAlleUferdigstilteAktivitetspliktVarsler()

            res.size shouldBe 0
        }
    }
})

private fun ikkeUtsendtVarsel(fnr: String) = PUtsendtVarselFeilet(
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
    isForcedLetter = false
)

private fun DatabaseInterface.skalHaLagretIkkeUtsendtVarsel(
    type: HendelseType,
    kanal: Kanal,
    fnr: String,
) =
    this.should("Skal ha lagret ikke-utsendt varsel av type ${type.name} ") {
        this.fetchUtsendtVarselFeiletByFnr(fnr)
            .filter { it.hendelsetypeNavn == type.name }.any { it.kanal.equals(kanal.name) }
    }

fun DatabaseInterface.fetchUtsendtVarselFeiletByFnr(fnr: String): List<PUtsendtVarselFeilet> {
    val queryStatement = """SELECT *
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
