ALTER TABLE UTSENDT_VARSEL
    ADD COLUMN narmesteleder_fnr varchar(11),
    ADD COLUMN orgnummer         varchar(9),
    ADD COLUMN kanal             varchar(50);

ALTER TABLE UTSENDT_VARSEL
    ALTER COLUMN aktor_id DROP NOT NULL;
