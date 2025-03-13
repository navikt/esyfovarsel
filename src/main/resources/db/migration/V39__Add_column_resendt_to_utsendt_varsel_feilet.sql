ALTER TABLE utsending_varsel_feilet
    ADD COLUMN if not exists is_resendt BOOLEAN DEFAULT FALSE;

ALTER TABLE utsending_varsel_feilet
    ADD COLUMN if not exists resendt_tidspunkt TIMESTAMP;