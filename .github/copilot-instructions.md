# esyfovarsel

- `./gradlew build` runs build, tests and lint; `./gradlew test` runs tests.
  Docker is needed for the PostgreSQL Testcontainers tests.
- Local app: `mise docker-up`, then `mise start`. `mise start-job` executes
  the job mode once; see `docs/local-development.md` for its local setup.
- Digital notification eligibility comes from KRR/DKIF `kanVarsles` through
  `AccessControlService`. Keep the channel-specific eligibility checks before
  dispatch; an event alone does not imply that SMS/email can be sent.
- Delivery and completion are separate operations. Preserve failed-delivery
  persistence and retry handling in `SenderFacade` and `ResendFailedVarslerJob`
  when changing a channel or event type.
- Varselbus consumes one record per poll with manual offset commits. Changes
  to offset handling must account for retries and duplicate notifications.
- Database tests share `EmbeddedDatabase`. Its Flyway connection uses
  auto-commit and a session advisory lock so `CREATE INDEX CONCURRENTLY` can
  run; using the transactional application pool can block migrations.
- Notification payloads, letter content and person identifiers must stay out
  of ordinary logs and metric labels.
