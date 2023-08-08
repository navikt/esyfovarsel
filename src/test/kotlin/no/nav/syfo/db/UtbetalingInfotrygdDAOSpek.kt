package no.nav.syfo.db

import io.kotest.core.spec.style.DescribeSpec
import no.nav.syfo.kafka.consumers.infotrygd.domain.InfotrygdSource.AAP_KAFKA_TOPIC
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import java.time.LocalDate

class UtbetalingInfotrygdDAOSpek : DescribeSpec({
    val sykepengerMaxDate = LocalDate.now().plusDays(1)

    describe("InfotrygdMaxDateDAOSpek") {
        val embeddedDatabase by lazy { EmbeddedDatabase() }

        afterTest {
            embeddedDatabase.connection.dropData()
        }

        afterSpec {
            embeddedDatabase.stop()
        }


        it("Store utbetaling") {
            embeddedDatabase.storeInfotrygdUtbetaling(
                arbeidstakerFnr1,
                sykepengerMaxDate,
                sykepengerMaxDate,
                0,
                AAP_KAFKA_TOPIC
            )
        }

        it("Store duplicate utbetaling") {
            embeddedDatabase.storeInfotrygdUtbetaling(
                arbeidstakerFnr1,
                sykepengerMaxDate,
                sykepengerMaxDate,
                0,
                AAP_KAFKA_TOPIC
            )
            embeddedDatabase.storeInfotrygdUtbetaling(
                arbeidstakerFnr1,
                sykepengerMaxDate,
                sykepengerMaxDate,
                30,
                AAP_KAFKA_TOPIC
            )
        }

    }
})
