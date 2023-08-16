package no.nav.syfo.db

import io.kotest.core.spec.style.DescribeSpec
import no.nav.syfo.kafka.consumers.syketilfelle.domain.KSyketilfellebit
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.mocks.fnr1
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate
import java.time.OffsetDateTime

class SyketilfellebitDAOSpek : DescribeSpec({
    describe("SyketilfellebitDAOSpek") {

        val embeddedDatabase by lazy { EmbeddedDatabase() }

        afterTest {
            embeddedDatabase.connection.dropData()
        }

        afterSpec {
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

            val syketilfellebitListe = embeddedDatabase.fetchSyketilfellebiterByFnr(fnr1)

            syketilfellebitListe.size shouldBeEqualTo 2
        }

        it("Store syketilfellebit should ignore duplicates") {
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

            val duplicateOfSyketilfellebit2 = syketilfellebitToStore2.copy()

            embeddedDatabase.storeSyketilfellebit(syketilfellebitToStore1.toPSyketilfellebit())
            embeddedDatabase.storeSyketilfellebit(syketilfellebitToStore2.toPSyketilfellebit())
            embeddedDatabase.storeSyketilfellebit(duplicateOfSyketilfellebit2.toPSyketilfellebit())

            val syketilfellebitListe = embeddedDatabase.fetchSyketilfellebiterByFnr(fnr1)

            syketilfellebitListe.size shouldBeEqualTo 2
        }

        it("Delete syketilfellebit") {
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

            embeddedDatabase.deleteSyketilfellebitById(syketilfellebitToStore1.id)
            val syketilfellebitListe = embeddedDatabase.fetchSyketilfellebiterByFnr(fnr1)

            syketilfellebitListe.size shouldBeEqualTo 1
        }

    }
})
