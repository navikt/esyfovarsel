package no.nav.syfo.db

import io.kotest.core.spec.style.DescribeSpec
import java.time.LocalDateTime
import java.util.UUID
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.testutil.EmbeddedDatabase
import org.amshove.kluent.shouldBeEqualTo

class UtsendtVarselDAOSpek :
    DescribeSpec({
        describe("UtsendtVarselDAOSpek") {
            val embeddedDatabase = EmbeddedDatabase()

            beforeTest {
                embeddedDatabase.dropData()
            }

            it("Returns 3 varsler") {
                val uuidToTest = UUID.randomUUID().toString()
                val utsendtVarsel1 = // skal sendes
                    PUtsendtVarsel(
                        uuid = uuidToTest,
                        fnr = no.nav.syfo.planner.arbeidstakerFnr1,
                        aktorId = arbeidstakerAktorId1,
                        narmesteLederFnr = null,
                        orgnummer = null,
                        type = HendelseType.SM_AKTIVITETSPLIKT.name,
                        kanal = "BRUKERNOTIFIKASJON",
                        utsendtTidspunkt = LocalDateTime.now().minusDays(2),
                        planlagtVarselId = null,
                        eksternReferanse = "123",
                        ferdigstiltTidspunkt = null,
                        arbeidsgivernotifikasjonMerkelapp = null,
                        isForcedLetter = false,
                        journalpostId = "111",
                    )

                val utsendtVarsel2 = // skal sendes
                    PUtsendtVarsel(
                        uuid = UUID.randomUUID().toString(),
                        fnr = arbeidstakerFnr2,
                        aktorId = arbeidstakerAktorId2,
                        narmesteLederFnr = null,
                        orgnummer = null,
                        type = HendelseType.SM_AKTIVITETSPLIKT.name,
                        kanal = "BRUKERNOTIFIKASJON",
                        utsendtTidspunkt = LocalDateTime.now().minusDays(3),
                        planlagtVarselId = null,
                        eksternReferanse = "456",
                        ferdigstiltTidspunkt = null,
                        arbeidsgivernotifikasjonMerkelapp = null,
                        isForcedLetter = false,
                        journalpostId = "222",
                    )

                val utsendtVarsel3 = // skal ikke sendes: not overdue
                    PUtsendtVarsel(
                        uuid = UUID.randomUUID().toString(),
                        fnr = arbeidstakerFnr2,
                        aktorId = arbeidstakerAktorId2,
                        narmesteLederFnr = null,
                        orgnummer = null,
                        type = HendelseType.SM_AKTIVITETSPLIKT.name,
                        kanal = "BRUKERNOTIFIKASJON",
                        utsendtTidspunkt = LocalDateTime.now().minusDays(1),
                        planlagtVarselId = null,
                        eksternReferanse = "789",
                        ferdigstiltTidspunkt = null,
                        arbeidsgivernotifikasjonMerkelapp = null,
                        isForcedLetter = false,
                        journalpostId = "333",
                    )

                val utsendtVarsel4 = // skal ikke sendes: Forced letter was sent before
                    PUtsendtVarsel(
                        uuid = UUID.randomUUID().toString(),
                        fnr = arbeidstakerFnr2,
                        aktorId = arbeidstakerAktorId2,
                        narmesteLederFnr = null,
                        orgnummer = null,
                        type = HendelseType.SM_AKTIVITETSPLIKT.name,
                        kanal = "BRUKERNOTIFIKASJON",
                        utsendtTidspunkt = LocalDateTime.now().minusDays(1),
                        planlagtVarselId = null,
                        eksternReferanse = "987",
                        ferdigstiltTidspunkt = null,
                        arbeidsgivernotifikasjonMerkelapp = null,
                        isForcedLetter = true,
                        journalpostId = "444",
                    )

                val utsendtVarsel5 = // skal ikke sendes: not aktivitetskrav
                    PUtsendtVarsel(
                        uuid = UUID.randomUUID().toString(),
                        fnr = arbeidstakerFnr2,
                        aktorId = arbeidstakerAktorId2,
                        narmesteLederFnr = null,
                        orgnummer = null,
                        type = HendelseType.SM_MER_VEILEDNING.name,
                        kanal = "BRUKERNOTIFIKASJON",
                        utsendtTidspunkt = LocalDateTime.now().minusDays(3),
                        planlagtVarselId = null,
                        eksternReferanse = "654",
                        ferdigstiltTidspunkt = null,
                        arbeidsgivernotifikasjonMerkelapp = null,
                        isForcedLetter = false,
                        journalpostId = "555",
                    )
                embeddedDatabase.storeUtsendtVarsel(utsendtVarsel1)
                embeddedDatabase.storeUtsendtVarsel(utsendtVarsel2)
                embeddedDatabase.storeUtsendtVarsel(utsendtVarsel3)
                embeddedDatabase.storeUtsendtVarsel(utsendtVarsel4)
                embeddedDatabase.storeUtsendtVarsel(utsendtVarsel5)

                val result = embeddedDatabase.fetchAlleUferdigstilteAktivitetspliktVarsler()
                result.size shouldBeEqualTo 2

                // Should not pick original uferdigstilt utsend varsel to notify user by physical letter for the second time
                embeddedDatabase.setUferdigstiltUtsendtVarselToForcedLetter(eksternRef = "123")
                val result2 = embeddedDatabase.fetchAlleUferdigstilteAktivitetspliktVarsler()
                result2.size shouldBeEqualTo 1

                // Should not pick uferdigstilt utsend varsel to notify user by physical letter varsel was ferdigstilt (lest)
                embeddedDatabase.setUtsendtVarselToFerdigstilt("456")
                val result3 = embeddedDatabase.fetchAlleUferdigstilteAktivitetspliktVarsler()
                result3.size shouldBeEqualTo 0
            }
        }
    })
