CREATE TABLE utsendt_mer_veiledning_varsel_backup
(
    uuid                               UUID PRIMARY KEY,
    fnr                                VARCHAR(11)  NOT NULL,
    aktor_id                           VARCHAR(13),
    type                               VARCHAR(100) NOT NULL,
    utsendt_tidspunkt                  TIMESTAMP    NOT NULL,
    planlagt_varsel_id                 UUID,
    narmesteleder_fnr                  VARCHAR(11),
    orgnummer                          varchar(9),
    kanal                              VARCHAR(50),
    ekstern_ref                        VARCHAR(50),
    ferdigstilt_tidspunkt              TIMESTAMP,
    arbeidsgivernotifikasjon_merkelapp VARCHAR(50)
);

CREATE INDEX utsendt_mer_veiledning_varsel_backup_fnr_index ON utsendt_mer_veiledning_varsel_backup (fnr);