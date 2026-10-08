# xq-svc-routine

Java 21 and Spring Boot service for managing workout routines, ordered sessions, and
exercise-history composition.

## Requirements

- Java 21
- Docker with the Compose plugin
- Gradle is provided by the Gradle Wrapper (`./gradlew`)

The service uses PostgreSQL through Flyway migrations in
`src/main/resources/db/migration`. Do not edit an applied migration; add a new versioned
migration instead.

## Source of truth

The BDD scenarios under `apps/xq-svc-routine-e2e/features/` are the immutable acceptance
contract for the service. `docs/routine-domain-contract.md` records the domain boundary,
invariants, persistence decisions, and exercise-service composition rules derived from those
scenarios.

## Local development

Start PostgreSQL with the repository's local Compose definition:

```shell
docker compose -f docker-compose.xq-svc-routine.yml up -d --wait
./gradlew --no-daemon bootRun
```

The default local database is `xq_platform` on `localhost:55432`, with username
`xq_platform` and password `local-only-xq-platform`. Override the `SPRING_DATASOURCE_*`
variables when using another database. Flyway applies migrations during application startup.

Stop the local database and remove its disposable volume after development:

```shell
docker compose -f docker-compose.xq-svc-routine.yml down --volumes --remove-orphans
```

## Commands

```shell
./gradlew --no-daemon clean test
./gradlew --no-daemon bootJar
./gradlew --no-daemon e2e
```

Run `e2e` after PostgreSQL is running and the packaged or boot-run service is listening on
`http://localhost:8080`. Set `XQORB_BASE_URI` when the service uses another URL. The E2E
source set uses the JVM Test Kit dependency from GitHub Packages and therefore needs
`GITHUB_ACTOR` and `GITHUB_TOKEN` when dependency resolution requires authentication.

## HTTP and dependencies

Health endpoints are `GET /livez` and `GET /readyz`. Routine CRUD and session operations are
served under `/api/v1/routines`; the OpenAPI document is available at
`src/main/resources/static/openapi/routine-service.yaml`.

Routine definitions and session mutations are local and must not call the exercise service.
The exercise service is a read-only downstream dependency used only when composing exercise
history for `GET /api/v1/routines/{id}/exercise-records`. Configure its base URL with
`EXERCISE_SERVICE_BASE_URL` (default `http://127.0.0.1:8090`). Downstream failures map to the
documented unavailable or bad-response contracts; routine definitions remain readable without
that dependency.

## CI and delivery

GitHub Actions keeps Build, Test, Publish, Deploy, and Deployment Smoke Test responsibilities
separate. Build and Test use the Gradle Wrapper and Java 21. Publish and Deploy are gated
delivery workflows; they must use immutable tested artifacts and repository/environment secrets,
never values committed to this repository.
