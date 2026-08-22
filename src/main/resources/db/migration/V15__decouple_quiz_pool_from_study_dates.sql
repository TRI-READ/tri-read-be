ALTER TABLE quiz_sets
    ADD COLUMN available_on DATE,
    ADD COLUMN retired_at TIMESTAMPTZ;

UPDATE quiz_sets
SET available_on = COALESCE(challenge_date, created_at::DATE)
WHERE available_on IS NULL;

ALTER TABLE quiz_sets
    ALTER COLUMN available_on SET NOT NULL;

CREATE INDEX ix_quiz_sets_available_pool
    ON quiz_sets (available_on, published_at, id)
    WHERE status = 'PUBLISHED' AND retired_at IS NULL;

ALTER TABLE user_quiz_assignments
    ADD COLUMN study_date DATE;

UPDATE user_quiz_assignments
SET study_date = challenge_date
WHERE study_date IS NULL;

ALTER TABLE user_quiz_assignments
    ALTER COLUMN study_date SET NOT NULL,
    DROP CONSTRAINT fk_user_quiz_assignments_quiz_date,
    ADD CONSTRAINT fk_user_quiz_assignments_quiz_set
        FOREIGN KEY (quiz_set_id) REFERENCES quiz_sets (id),
    ADD CONSTRAINT ux_user_quiz_assignments_user_study_date
        UNIQUE (user_id, study_date);

CREATE INDEX ix_user_quiz_assignments_user_study
    ON user_quiz_assignments (user_id, study_date DESC, quiz_set_id);
