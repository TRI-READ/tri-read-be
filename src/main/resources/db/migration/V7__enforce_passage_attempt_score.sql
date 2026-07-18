ALTER TABLE quiz_attempts
    DROP CONSTRAINT ck_quiz_attempts_score,
    ADD CONSTRAINT ck_quiz_attempts_score
        CHECK (score BETWEEN 0 AND 3);
