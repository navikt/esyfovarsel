ALTER TABLE UTSENDT_VARSEL
    ADD COLUMN is_forced_letter boolean;

ALTER TABLE UTSENDING_VARSEL_FEILET
    ADD COLUMN is_forced_letter boolean;