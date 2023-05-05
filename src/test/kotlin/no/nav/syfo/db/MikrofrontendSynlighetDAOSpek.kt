package no.nav.syfo.db

import no.nav.syfo.kafka.producers.mineside_microfrontend.MikrofrontendSynlighet
import no.nav.syfo.kafka.producers.mineside_microfrontend.Tjeneste
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.shouldContainMikrofrontendEntry
import no.nav.syfo.testutil.shouldNotContainMikrofrontendEntryForUser
import org.amshove.kluent.should
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

object MikrofrontendSynlighetDAOSpek : Spek({
    defaultTimeout = 20000L

    describe("MikrofrontendSynlighetDAOSpek") {
        val embeddedDatabase by lazy { EmbeddedDatabase() }

        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }

        val mikrofrontendSynlighet1 = MikrofrontendSynlighet(
            arbeidstakerFnr1,
            Tjeneste.DIALOGMOTE,
            null
        )

        val mikrofrontendSynlighet2 = MikrofrontendSynlighet(
            arbeidstakerFnr2,
            Tjeneste.DIALOGMOTE,
            LocalDate.now().plusDays(1L)
        )

        it("Store mikrofrontend entry") {
            embeddedDatabase.storeMikrofrontendSynlighetEntry(mikrofrontendSynlighet1)
            embeddedDatabase.shouldContainMikrofrontendEntry(arbeidstakerFnr1, Tjeneste.DIALOGMOTE)
        }

        it("Delete mikrofrontend entry") {
            embeddedDatabase.storeMikrofrontendSynlighetEntry(mikrofrontendSynlighet1)
            embeddedDatabase.storeMikrofrontendSynlighetEntry(mikrofrontendSynlighet2)
            embeddedDatabase.shouldContainMikrofrontendEntry(arbeidstakerFnr2, Tjeneste.DIALOGMOTE)
            embeddedDatabase.deleteMikrofrontendSynlighetEntryByFnrAndTjeneste(arbeidstakerFnr2, Tjeneste.DIALOGMOTE)
            embeddedDatabase.shouldNotContainMikrofrontendEntryForUser(arbeidstakerFnr2)
        }

        it("Update miktrofrontend entry") {
            embeddedDatabase.storeMikrofrontendSynlighetEntry(mikrofrontendSynlighet2)
            val newSynligTom = mikrofrontendSynlighet2.synligTom!!.plusDays(1L)
            embeddedDatabase.updateMikrofrontendEntrySynligTom(mikrofrontendSynlighet2, newSynligTom)
            embeddedDatabase.fetchMikrofrontendSynlighetEntriesByFnr(mikrofrontendSynlighet2.synligFor)
                .size shouldBeEqualTo 1
            embeddedDatabase.entryShouldHaveCorrectSynligTom(mikrofrontendSynlighet2, newSynligTom)
        }
    }
})

private fun DatabaseInterface.entryShouldHaveCorrectSynligTom(
    entry: MikrofrontendSynlighet,
    mostRecentSynligTom: LocalDate
) {
    this.should("Entry should have correct synligTom field") {
        this.fetchMikrofrontendSynlighetEntriesByFnr(entry.synligFor)
            .first { it.tjeneste == entry.tjeneste.name }.synligTom == mostRecentSynligTom
    }
}
