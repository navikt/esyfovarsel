package no.nav.syfo.db

import java.time.LocalDateTime
import java.util.*
import no.nav.syfo.db.domain.Kanal
import no.nav.syfo.db.domain.PUtsendtVarselFeilet
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.should
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object UtsendtVarselFeiletDAOSpek : Spek({
    // The default timeout of 10 seconds is not sufficient to initialise the embedded database
    defaultTimeout = 20000L

    describe("UtsendtVarselFeiletDAOSpek") {
        val embeddedDatabase by lazy { EmbeddedDatabase() }

        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
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
    }
})

private fun ikkeUtsendtVarsel(fnr: String) = PUtsendtVarselFeilet(
    uuid = UUID.randomUUID().toString(),
    fnr = fnr,
    narmesteLederFnr = "01010101010",
    orgnummer = null,
    type = HendelseType.NL_DIALOGMOTE_NYTT_TID_STED.name,
    kanal = Kanal.DINE_SYKMELDTE.name,
    utsendtForsokTidspunkt = LocalDateTime.now(),
    eksternReferanse = "00000",
    feilmelding = "Achtung!",
    journalpostId = null
)

private fun DatabaseInterface.skalHaLagretIkkeUtsendtVarsel(
    type: HendelseType,
    kanal: Kanal,
    fnr: String,
) =
    this.should("Skal ha lagret ikke-utsendt varsel av type ${type.name} ") {
        this.fetchUtsendtVarselFeiletByFnr(fnr)
            .filter { it.type == type.name }.any { it.kanal.equals(kanal.name) }
    }