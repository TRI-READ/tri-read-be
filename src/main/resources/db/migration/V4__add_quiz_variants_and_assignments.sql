ALTER TABLE quiz_sets
    ADD COLUMN variant_code VARCHAR(1) DEFAULT 'A' NOT NULL,
    ADD CONSTRAINT ck_quiz_sets_variant_code
        CHECK (variant_code BETWEEN 'A' AND 'Z'),
    ADD CONSTRAINT ux_quiz_sets_id_challenge_date
        UNIQUE (id, challenge_date);

DROP INDEX ux_quiz_sets_published_date;

CREATE UNIQUE INDEX ux_quiz_sets_published_date_variant
    ON quiz_sets (challenge_date, variant_code)
    WHERE status = 'PUBLISHED';

CREATE INDEX ix_quiz_sets_active_date
    ON quiz_sets (challenge_date, variant_code)
    WHERE status IN ('DRAFT', 'REVIEWED', 'PUBLISHED');

CREATE TABLE user_quiz_assignments (
    user_id BIGINT NOT NULL,
    challenge_date DATE NOT NULL,
    quiz_set_id BIGINT NOT NULL,
    assigned_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,

    PRIMARY KEY (user_id, challenge_date),

    CONSTRAINT ux_user_quiz_assignments_user_set
        UNIQUE (user_id, quiz_set_id),

    CONSTRAINT fk_user_quiz_assignments_user
        FOREIGN KEY (user_id) REFERENCES app_users (id),

    CONSTRAINT fk_user_quiz_assignments_quiz_date
        FOREIGN KEY (quiz_set_id, challenge_date)
        REFERENCES quiz_sets (id, challenge_date)
);

CREATE INDEX ix_user_quiz_assignments_quiz_set
    ON user_quiz_assignments (quiz_set_id);

INSERT INTO user_quiz_assignments (user_id, challenge_date, quiz_set_id, assigned_at)
SELECT qa.user_id, qs.challenge_date, qa.quiz_set_id, qa.completed_at
FROM quiz_attempts qa
JOIN quiz_sets qs ON qs.id = qa.quiz_set_id
WHERE qs.challenge_date IS NOT NULL
ON CONFLICT DO NOTHING;
