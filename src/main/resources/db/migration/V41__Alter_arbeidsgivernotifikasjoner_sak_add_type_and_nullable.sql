ALTER TABLE ARBEIDSGIVERNOTIFIKASJONER_SAK
    ALTER COLUMN narmestelederId DROP NOT NULL,
    ALTER COLUMN narmesteLederFnr DROP NOT NULL,
    ADD COLUMN type TEXT,
    ADD COLUMN eksternSakId TEXT,
    ADD COLUMN ressursId TEXT;

UPDATE ARBEIDSGIVERNOTIFIKASJONER_SAK
SET type =
        CASE
            WHEN merkelapp = 'Dialogmøte' THEN 'dialogmote-med-leder'
            WHEN merkelapp = 'Oppfølging' THEN 'oppfolging-med-leder'
            ELSE type
            END
WHERE merkelapp IN ('Dialogmøte', 'Oppfølging');

CREATE INDEX IF NOT EXISTS idx_arbeidsgivernotifikasjoner_sak_ansatt_virksomhet_type
    ON ARBEIDSGIVERNOTIFIKASJONER_SAK (ansattFnr, virksomhetsnummer, type);
