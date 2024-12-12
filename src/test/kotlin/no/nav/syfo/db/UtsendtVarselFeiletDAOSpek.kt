package no.nav.syfo.db

import io.kotest.core.spec.style.DescribeSpec
import java.time.LocalDateTime
import java.util.*
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.testutil.EmbeddedDatabase
import org.amshove.kluent.shouldBeEqualTo

class UtsendtVarselFeiletDAOSpek : DescribeSpec({
    describe("UtsendtVarselFeiletDAOSpek") {
        val embeddedDatabase = EmbeddedDatabase()

        beforeTest {
            embeddedDatabase.dropData()
        }

        it("Returns 3 varsler") {
            val utsendtVarsel1 = // skal sendes
                PUtsendtVarsel(
                    uuid = UUID.randomUUID().toString(),
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
                )
            embeddedDatabase.storeUtsendtVarsel(utsendtVarsel1)
            embeddedDatabase.storeUtsendtVarsel(utsendtVarsel2)
            embeddedDatabase.storeUtsendtVarsel(utsendtVarsel3)
            embeddedDatabase.storeUtsendtVarsel(utsendtVarsel4)
            embeddedDatabase.storeUtsendtVarsel(utsendtVarsel5)

            val result = embeddedDatabase.fetchAlleUferdigstilteAktivitetspliktVarsler().size
            //            val result = job.sendForcedLetterFromJob()

            result shouldBeEqualTo 3
        }
    }
})
