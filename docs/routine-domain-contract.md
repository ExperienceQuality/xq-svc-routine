# Routine domain contract

Status: shaped from the immutable BDD scenarios in `apps/xq-svc-routine-e2e/features/`.

This document defines the business boundary for `xq-svc-routine`. The feature files are the
acceptance contract; this document records the aggregate, service, and PostgreSQL decisions needed
to implement that contract. It does not change or supersede the scenarios.

## 1. BDD traceability

| Feature/scenario group | Contract | Owning boundary | Persistence/concurrency seam |
| --- | --- | --- | --- |
| `health.feature` | `GET /livez` returns `200`/`UP` without PostgreSQL or exercise-service dependency; `GET /readyz` reports usable readiness. | HTTP health adapter and database readiness probe. | Liveness has no dependency; readiness may execute `SELECT 1`. |
| `routine-management.feature` create/retrieve | Create trims `name` and optional `notes`, returns `201`, `Location`, strong ETag, and no sessions by default. Retrieve is local and does not call exercise service. | `Routine` aggregate and routine HTTP adapter. | `routines.version` starts at `1`; ETag is a strong representation of version. |
| routine validation | Blank or over-limit names, unknown properties, and malformed JSON return `400` with `VALIDATION_FAILED` or `MALFORMED_REQUEST`; invalid name identifies `name`. | Request DTO validation plus boundary error mapper. | Database checks remain defense in depth, not the primary field-error mechanism. |
| active routine list | Default list contains active routines, excludes sessions, and supports `limit` 1–100. | Routine query boundary. | Stable order: `updated_at DESC, id DESC`; cursor carries filter and last sort key. |
| routine update/archive | PATCH and archive require `If-Match`; missing header is `428 PRECONDITION_REQUIRED`; stale version is `412 PRECONDITION_FAILED`; successful mutation increments version and emits newer ETag. | Aggregate mutation service. | Conditional update on `(id, version, archived_at IS NULL)` inside one transaction. |
| archived routine behavior | Archive is a soft state transition (`204`); archived routine remains readable, is absent from active list, and rejects session mutation with `409 ROUTINE_ARCHIVED`. | Routine aggregate lifecycle policy. | Keep children; do not cascade-delete on archive. |
| `session-management.feature` add/replace/delete | Sessions are routine-owned ordered definitions. Add/replace/delete are atomic, reorder positions, compact positions from zero, return updated ETag where applicable, and never call exercise service. | `Routine` aggregate mutation service. | One transaction locks/claims routine version, shifts positions, and writes child rows. |
| session validation | Session names and exercise names are trimmed and nonblank, max 120; max 50 sessions per routine and 50 exercise references per session; duplicate exercise names are rejected case-insensitively. | Aggregate invariant validator. | `CHECK` constraints enforce nonnegative positions and text bounds; aggregate validator enforces cardinality and case-insensitive uniqueness. |
| session not-found cases | Unknown routine returns `ROUTINE_NOT_FOUND`; unknown session returns `SESSION_NOT_FOUND`. | Aggregate lookup boundary. | Session lookup always scopes by `routine_id`; no cross-routine identity leak. |
| `pagination.feature` | Routine cursor is opaque, stable, and bound to `includeArchived`; invalid or filter-mismatched cursor returns `400 INVALID_CURSOR`; pages contain no duplicate IDs. | Cursor codec plus routine query boundary. | Keyset predicate over `(updated_at, id)` with matching composite index. |
| `exercise-records.feature` composition | Read session locally, preserve exercise order, fetch logs by name case-insensitively, preserve complete downstream log data, and return empty collection when history is empty. | Read-only composition adapter. | No exercise-owned data is persisted in routine schema. |
| exercise-service failure mapping | Downstream unavailable/rate-limited maps to `503 EXERCISE_SERVICE_UNAVAILABLE`; preserve `Retry-After`; malformed payload maps to `502 DOWNSTREAM_BAD_RESPONSE`. | Downstream client and error mapper. | Composition transaction is read-only; routine definition remains readable without downstream call. |
| composed-record pagination | Cursor is opaque and filter-bound; limit is 1–100; invalid cursor is `400 INVALID_CURSOR`; no duplicate IDs across pages. | Composition query/pagination boundary. | Cursor must encode enough deterministic traversal state to prevent replay under changed filters. |

HTTP problem responses use `application/problem+json` with stable `code`; validation responses may
include a `violations` array containing the BDD field path.

## 2. Aggregate ownership and invariants

### Routine aggregate

`Routine` is the consistency boundary. It owns metadata, lifecycle state, version, ordered sessions,
and ordered exercise-name references. It does not own exercise logs, sets, history, or exercise
service writes.

Aggregate identity and state:

- `id`: UUID, immutable.
- `name`: trimmed, nonblank, maximum 120 characters.
- `notes`: nullable, trimmed when present, maximum 2,000 characters; merge patch may clear it.
- `archivedAt`: nullable soft-archive timestamp.
- `version`: monotonically increasing positive integer, used for strong ETags.
- `createdAt` and `updatedAt`: server-managed timestamps.

Aggregate invariants:

1. A mutation is allowed only when routine exists, is not archived, and caller's expected version
   equals persisted version.
2. Every successful mutation increments version exactly once, including session add, replace, and
   delete. Failed mutations leave the aggregate unchanged.
3. Session positions are contiguous integers beginning at zero, unique within a routine, and never
   exceed the last valid position after insertion or replacement.
4. A routine has at most 50 sessions.
5. Each session has at most 50 exercise references. References are trimmed, nonblank, maximum 120,
   and unique under case-insensitive comparison within that session.
6. Exercise references are names only. Routine writes never validate, create, update, or delete
   exercise-service data.
7. Session exercise positions are contiguous integers beginning at zero and unique within a session.
8. Archiving changes lifecycle state but does not remove sessions or references. Reads remain valid;
   session mutations fail with `ROUTINE_ARCHIVED`.

The database should enforce row-local invariants. The service must enforce aggregate-wide invariants
and position-rewrite rules because cardinality, case-insensitive duplicate detection, and ordered
rebalancing span multiple rows.

## 3. Java service boundary

The target implementation should follow the conventions observed in `xq-svc-exercise`:

- Java 21 Spring Boot service.
- Package by domain (`routine`) plus shared HTTP/error concerns.
- API records for request and response shapes. Use Jakarta validation for syntactic constraints;
  normalize text and enforce aggregate rules in the service.
- Thin `@RestController` mapping HTTP routes, headers, status, `Location`, ETag, and
  `Retry-After`; no persistence or orchestration in the controller.
- `@Service` owns aggregate commands and read models. Keep command methods transactional; mark
  pure reads `@Transactional(readOnly = true)`.
- Use `JdbcClient` with named parameters and explicit SQL. Keep Flyway SQL as schema authority.
- Map domain failures to a shared `ApiException`/problem handler with stable status and problem code,
  matching the BDD vocabulary. Map malformed JSON separately from validation failures.
- Use a dedicated exercise-service client for composition. It is read-only from routine's point of
  view and must not be invoked by routine CRUD or session commands.

Suggested boundary:

```text
RoutineController
  -> RoutineService (commands, routine reads, session reads)
       -> RoutineRepository / JdbcClient
  -> ExerciseRecordController
       -> RoutineService.readSession
       -> ExerciseLogClient (GET only)
  -> ApiExceptionHandler / ProblemDetail mapper
```

Command transaction shape:

1. Parse `If-Match`; missing is `PRECONDITION_REQUIRED`, malformed/stale is
   `PRECONDITION_FAILED`.
2. Begin transaction and lock the routine row (`SELECT ... FOR UPDATE`) or perform a conditional
   version claim.
3. Verify existence, active lifecycle, and expected version.
4. Validate aggregate command and calculate position shifts.
5. Apply all parent/child changes atomically.
6. Increment version once and commit.
7. Reload the representation and return its new strong ETag.

Conditional version updates must still be retained as the final race-safe guard. A stale caller
must never overwrite a newer routine, even if two requests pass an initial read concurrently.

## 4. Flyway/PostgreSQL schema decisions

Use versioned Flyway SQL migrations under `src/main/resources/db/migration`. Names and constraints
should be explicit and stable; application code should not rely on Prisma-generated names.

### Tables

`routine.routines`

- `id UUID PRIMARY KEY` (server-generated UUID).
- `name VARCHAR(120) NOT NULL`.
- `notes VARCHAR(2000) NULL`.
- `version BIGINT NOT NULL DEFAULT 1` with `CHECK (version > 0)`.
- `archived_at TIMESTAMPTZ NULL`.
- `created_at` and `updated_at` `TIMESTAMPTZ NOT NULL` with server defaults.
- Checks require `length(btrim(name)) > 0`; nullable notes pass when null and otherwise require
  `length(btrim(notes)) > 0` if the API does not support blank notes.

`routine.routine_sessions`

- `id UUID PRIMARY KEY`.
- `routine_id UUID NOT NULL` referencing `routine.routines(id)`.
- `name VARCHAR(120) NOT NULL`.
- `position INTEGER NOT NULL`.
- Server timestamps for auditability.
- `CHECK (position >= 0)` and `CHECK (length(btrim(name)) > 0)`.
- `UNIQUE (routine_id, position)` protects contiguous ordering from duplicate positions during a
  committed state.

`routine.routine_session_exercises`

- `id UUID PRIMARY KEY`.
- `session_id UUID NOT NULL` referencing `routine.routine_sessions(id)`.
- `position INTEGER NOT NULL`.
- `exercise_name VARCHAR(120) NOT NULL`.
- `CHECK (position >= 0)` and `CHECK (length(btrim(exercise_name)) > 0)`.
- `UNIQUE (session_id, position)` protects exercise ordering.
- `UNIQUE (session_id, lower(btrim(exercise_name)))` is recommended if migration/runtime support
  permits an expression unique index; otherwise the service must enforce it inside the locked
  aggregate transaction.

### Foreign keys and cascade rules

Use `routine_sessions.routine_id REFERENCES routine.routines(id) ON DELETE CASCADE` and
`routine_session_exercises.session_id REFERENCES routine.routine_sessions(id) ON DELETE CASCADE`.
The cascade is cleanup for a physically deleted aggregate and child rows, not the archive behavior.
The public routine API archives instead of deleting, so normal archive operations preserve history
and definitions. Do not create a foreign key to the exercise service: exercise names are a
cross-service reference, not a local identity.

### Indexes

- Active/list traversal: `(archived_at, updated_at DESC, id DESC)`; the service applies
  `archived_at IS NULL` for active lists and uses `(updated_at, id)` keyset predicates.
- Session read/order: `(routine_id, position, id)`.
- Session lookup and ordered exercise read: `(session_id, position, id)`.
- Add a functional index on `lower(btrim(exercise_name))` only if query paths search local
  references by normalized name. Composition calls the exercise service, so routine does not need
  an exercise-log lookup index.

The `id` tie-breaker is mandatory in every ordered page. Cursors must include `includeArchived`,
the last `updatedAt`, and the last `id`; composed-record cursors must include the session identity,
limit-independent traversal state, and a contract/version marker. Sign or encrypt cursors if they
would otherwise expose implementation details; in all cases validate shape and filter binding.

### Version concurrency

ETag format is a strong quoted decimal version, for example `"7"`. Create and every successful
mutation return the current version as ETag. Reads may return ETag as well.

For routine metadata and archive:

```sql
UPDATE routine.routines
SET ..., version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE id = :id AND version = :expected_version AND archived_at IS NULL;
```

Require exactly one affected row. Zero rows maps to `ROUTINE_NOT_FOUND` when the row is absent,
`ROUTINE_ARCHIVED` when archived, or `PRECONDITION_FAILED` when version changed. Session changes
use the same claim inside one transaction, then shift/rewrite child positions before commit.

Do not use child-row timestamps or a last-write-wins update as the concurrency token. The routine
version covers the complete aggregate, so a metadata update invalidates stale session commands and
vice versa, as required by the stale-ETag scenarios.

## 5. Acceptance evidence

The contract is accepted when implementation evidence can show:

- Every scenario in the five feature files maps to an endpoint/service rule above.
- Routine/session writes are atomic, ETag-gated, version-incrementing, and exercise-service-free.
- Active/archive filtering and both cursor families reject invalid or mismatched cursors without
  duplicate IDs across pages.
- PostgreSQL migrations contain explicit foreign keys, cascade rules, row checks, ordering indexes,
  and version-concurrency support.
- Java boundary follows the sibling service's records/controller/service/JdbcClient/Flyway/
  ProblemDetail conventions.

No feature file or source file is changed by this design assignment.
