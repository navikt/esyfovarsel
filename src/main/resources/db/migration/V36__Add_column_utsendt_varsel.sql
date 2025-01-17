ALTER TABLE UTSENDT_VARSEL
    ADD COLUMN if not exists is_forced_letter boolean;

ALTER TABLE UTSENDING_VARSEL_FEILET
    ADD COLUMN if not exists is_forced_letter boolean;