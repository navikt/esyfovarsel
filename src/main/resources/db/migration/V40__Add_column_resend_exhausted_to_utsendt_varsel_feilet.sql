ALTER TABLE utsending_varsel_feilet
    ADD COLUMN if not exists resend_exhausted BOOLEAN DEFAULT null;

