ALTER TABLE quiz_validation_results
    DROP CONSTRAINT ck_quiz_validation_type;

ALTER TABLE quiz_validation_results
    ADD CONSTRAINT ck_quiz_validation_type
        CHECK (validation_type IN ('RULE', 'AI', 'DIVERSITY'));
