# xq-svc-exercice
[![Build](https://github.com/ExperienceQuality/xq-svc-exercice/actions/workflows/build.yml/badge.svg)](https://github.com/ExperienceQuality/xq-svc-exercice/actions/workflows/build.yml)
[![Test](https://github.com/ExperienceQuality/xq-svc-exercice/actions/workflows/test.yml/badge.svg)](https://github.com/ExperienceQuality/xq-svc-exercice/actions/workflows/test.yml)
[![Publish](https://github.com/ExperienceQuality/xq-svc-exercice/actions/workflows/publish.yml/badge.svg)](https://github.com/ExperienceQuality/xq-svc-exercice/actions/workflows/publish.yml)
[![Deploy](https://github.com/ExperienceQuality/xq-svc-exercice/actions/workflows/deploy.yml/badge.svg)](https://github.com/ExperienceQuality/xq-svc-exercice/actions/workflows/deploy.yml)
[![Deployment Smoke Test](https://github.com/ExperienceQuality/xq-svc-exercice/actions/workflows/deploy-smoke.yml/badge.svg)](https://github.com/ExperienceQuality/xq-svc-exercice/actions/workflows/deploy-smoke.yml)

Spring Boot microservice and PostgreSQL database for a single-user exercise log book.



## Local development

Prerequisites: Java 21 and Docker with the Compose plugin.

```shell
./gradlew bootRun
```

Flyway applies the schema from `src/main/resources/db/migration`. The service uses PostgreSQL
through `compose.yaml`; set `POSTGRES_PORT=0` to let Docker select a free host port.

## API

```text
POST /api/v1/exercise-logs
GET  /api/v1/exercise-logs/{exerciseLogId}
GET  /api/v1/exercise-logs?exerciseName=Bench%20Press&sort=highestSetVolume&limit=2
GET  /api/v1/exercise-logs/exercises
```

The service calculates each set's volume as `weightKg * reps` and ranks logs by their highest
single-set volume. The distinct-exercises endpoint is a read projection from `exercise_logs`;
there is no exercise catalog table yet.

## Verification

```shell
./gradlew test
```

The CI workflow runs unit and integration checks, packages the service JAR, starts PostgreSQL,
checks the packaged service health endpoint, and runs the JVM Test Kit E2E suite. Releases are
triggered by semantic-version tags such as `v1.0.0` or manually from GitHub Actions; the release
workflow publishes the GHCR image `ghcr.io/experiencequality/xq-svc-exercice` with an immutable
commit tag and registry-backed provenance and SBOM attestations.

To run the CI-equivalent gates locally:

```shell
./gradlew --no-daemon clean ci
docker compose up -d --wait
./gradlew --no-daemon e2e
docker compose down --volumes --remove-orphans
```

## Render and Neon deployment

GitHub Actions runs separate Build, Test, Publish, and Deploy workflows. Publish builds a Linux
 container image with Dockerfile and publishes it to GHCR after successful main-branch Test, or
 for an explicit release tag. Deploy then triggers an image-backed Render service with the immutable
 `sha-<commit>` image tag. The image targets `linux/amd64` and uses bounded JVM memory for Render's
 512 MB plan. Configure the
service health check as `/actuator/health/readiness`; the service binds to Render's `PORT` value
and uses production profile settings when `SPRING_PROFILES_ACTIVE=production`.

Create a Render workspace registry credential named `ghcr` with a GitHub token that has
`read:packages` permission. Attach it to the image-backed service. Render does not automatically
redeploy when a registry tag changes; the deploy hook in GitHub Actions triggers each image deploy.

Use one Neon project and its protected `main` branch only. Configure these Render environment
variables with the Neon `main` connection and runtime role; keep values in Render, never Git:

```text
SPRING_PROFILES_ACTIVE=production
SPRING_DATASOURCE_URL=<Neon main pooled connection URL>
SPRING_DATASOURCE_USERNAME=<runtime role>
SPRING_DATASOURCE_PASSWORD=<runtime role password>
DB_SCHEMA=fitness
```

Production uses a 30-second database connection timeout by default to tolerate Neon compute
cold start. Override `DB_CONNECTION_TIMEOUT_MS` in Render only when measured startup behavior
requires a different value.

Flyway applies migrations from `src/main/resources/db/migration`. Validate migrations against
local PostgreSQL in CI, then review production migration impact before deploying to Neon `main`.
Never enable Flyway clean in production or edit an applied migration.

Create GitHub environment `production`, restrict it to `main`, and set repository variable
`RENDER_SERVICE_URL` to the Render service URL. The deployment smoke workflow checks liveness,
readiness, and the read-only distinct-exercises endpoint. Run it only after Render deploys.

Create repository secret `RENDER_DEPLOY_HOOK_URL` from the Render service Settings → Deploy Hook.
The deploy workflow appends the immutable GHCR image tag to this hook. Keep the hook secret; it
can trigger production deploys. [Render deploy hooks](https://render.com/docs/deploy-hooks)

Build the deployment image locally with:

```shell
docker build -t xq-svc-exercise:test .
```
