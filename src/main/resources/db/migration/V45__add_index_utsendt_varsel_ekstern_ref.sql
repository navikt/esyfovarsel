-- flyway:executeInTransaction=false
CREATE INDEX CONCURRENTLY IF NOT EXISTS utsendt_varsel_ekstern_ref_index ON UTSENDT_VARSEL (ekstern_ref);
