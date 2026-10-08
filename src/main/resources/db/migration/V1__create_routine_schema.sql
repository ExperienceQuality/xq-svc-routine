create extension pgcrypto;

create table routine.routines (
    id uuid primary key default gen_random_uuid(),
    name varchar(120) not null,
    notes varchar(2000),
    version bigint not null default 1,
    archived_at timestamptz(3),
    created_at timestamptz(3) not null default now(),
    updated_at timestamptz(3) not null default now(),
    constraint routines_name_not_blank check (length(btrim(name)) > 0),
    constraint routines_version_positive check (version > 0)
);

create table routine.routine_sessions (
    id uuid primary key default gen_random_uuid(),
    routine_id uuid not null references routine.routines(id) on delete cascade,
    name varchar(120) not null,
    position integer not null,
    created_at timestamptz(3) not null default now(),
    updated_at timestamptz(3) not null default now(),
    constraint routine_sessions_name_not_blank check (length(btrim(name)) > 0),
    constraint routine_sessions_position_nonnegative check (position >= 0),
    constraint routine_sessions_position_unique unique (routine_id, position)
);

create table routine.routine_session_exercises (
    id uuid primary key default gen_random_uuid(),
    session_id uuid not null references routine.routine_sessions(id) on delete cascade,
    position integer not null,
    exercise_name varchar(120) not null,
    constraint routine_session_exercises_name_not_blank check (length(btrim(exercise_name)) > 0),
    constraint routine_session_exercises_position_nonnegative check (position >= 0),
    constraint routine_session_exercises_position_unique unique (session_id, position)
);

create index routines_archive_updated_idx
    on routine.routines (archived_at, updated_at desc, id desc);

create index routine_sessions_order_idx
    on routine.routine_sessions (routine_id, position, id);

create index routine_session_exercises_order_idx
    on routine.routine_session_exercises (session_id, position, id);
