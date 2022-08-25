package no.nav.syfo.db

import no.nav.syfo.kafka.consumers.syketilfelle.domain.KSyketilfellebit
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.mocks.fnr1
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.OffsetDateTime

object SyketilfellebitDAOSpek : Spek({

    //The default timeout of 10 seconds is not sufficient to initialise the embedded database
    defaultTimeout = 20000L

    describe("SyketilfellebitDAOSpek") {

        val embeddedDatabase by lazy { EmbeddedDatabase() }

        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }

        it("Store syketilfellebit") {
            val syketilfellebitToStore1 = KSyketilfellebit(
                fnr = fnr1,
                id = "1",
                orgnummer = "123456789",
                opprettet = OffsetDateTime.now(),
                inntruffet = OffsetDateTime.now(),
                tags = setOf("SYKMELDING", "SENDT"),
                ressursId = "ressursId_1",
                fom = LocalDate.now(),
                tom = LocalDate.now().plusDays(14),
                korrigererSendtSoknad = null
            )

            val syketilfellebitToStore2 = KSyketilfellebit(
                fnr = fnr1,
                id = "2",
                orgnummer = "123456789",
                opprettet = OffsetDateTime.now(),
                inntruffet = OffsetDateTime.now(),
                tags = setOf("SYKMELDING", "SENDT"),
                ressursId = "ressursId_2",
                fom = LocalDate.now().plusDays(15),
                tom = LocalDate.now().plusDays(30),
                korrigererSendtSoknad = null
            )

            embeddedDatabase.storeSyketilfellebit(syketilfellebitToStore1.toPSyketilfellebit())
            embeddedDatabase.storeSyketilfellebit(syketilfellebitToStore2.toPSyketilfellebit())
            embeddedDatabase.storeSyketilfellebit(syketilfellebitToStore2.toPSyketilfellebit())

            val syketilfellebitListe = embeddedDatabase.fetchSyketilfellebiterByFnr(fnr1)

            syketilfellebitListe.size shouldBeEqualTo 2
        }
    }
})
