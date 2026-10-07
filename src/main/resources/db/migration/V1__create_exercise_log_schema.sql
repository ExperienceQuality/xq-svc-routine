CREATE TABLE exercise_logs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    exercise_name TEXT NOT NULL,
    logged_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT exercise_logs_name_not_blank
        CHECK (length(btrim(exercise_name)) > 0)
);

CREATE TABLE exercise_sets (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    exercise_log_id BIGINT NOT NULL
        REFERENCES exercise_logs(id)
        ON DELETE CASCADE,
    set_number SMALLINT NOT NULL,
    weight_kg NUMERIC(7, 2) NOT NULL DEFAULT 0,
    reps SMALLINT NOT NULL,

    CONSTRAINT exercise_sets_number_positive
        CHECK (set_number > 0),
    CONSTRAINT exercise_sets_weight_non_negative
        CHECK (weight_kg >= 0),
    CONSTRAINT exercise_sets_reps_positive
        CHECK (reps > 0),
    CONSTRAINT exercise_sets_number_unique
        UNIQUE (exercise_log_id, set_number)
);

CREATE INDEX exercise_logs_name_date_idx
    ON exercise_logs (exercise_name, logged_at DESC, id DESC);
