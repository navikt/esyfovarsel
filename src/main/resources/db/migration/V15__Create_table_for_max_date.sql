CREATE TABLE MAX_DATE
(
    uuid        UUID PRIMARY KEY,
    fnr         VARCHAR(11)  NOT NULL,
    max_date    DATE         NOT NULL,
    opprettet   TIMESTAMP    NOT NULL,
    sist_endret TIMESTAMP    NOT NULL,
    source      VARCHAR(100) NOT NULL
);
