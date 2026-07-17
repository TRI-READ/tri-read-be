ALTER TABLE passages
    ADD CONSTRAINT ux_passages_quiz_id
        UNIQUE (quiz_set_id, id);

ALTER TABLE quiz_attempts
    ADD COLUMN passage_id BIGINT,
    ADD COLUMN attempt_type VARCHAR(10) DEFAULT 'PRIMARY' NOT NULL;

ALTER TABLE quiz_attempts
    DROP CONSTRAINT ux_quiz_attempts_user_quiz;

UPDATE quiz_attempts qa
SET passage_id = attempted_passage.passage_id
FROM (
    SELECT aa.attempt_id,
           MIN(q.passage_id) AS passage_id
    FROM attempt_answers aa
    JOIN questions q ON q.id = aa.question_id
    GROUP BY aa.attempt_id
    HAVING COUNT(DISTINCT q.passage_id) = 1
) attempted_passage
WHERE attempted_passage.attempt_id = qa.id;

DO $$
DECLARE
    existing_attempt RECORD;
    attempted_passage RECORD;
    primary_passage_id BIGINT;
    split_attempt_id BIGINT;
BEGIN
    FOR existing_attempt IN
        SELECT qa.id, qa.user_id, qa.quiz_set_id, qa.completed_at
        FROM quiz_attempts qa
        JOIN attempt_answers aa ON aa.attempt_id = qa.id
        JOIN questions q ON q.id = aa.question_id
        GROUP BY qa.id, qa.user_id, qa.quiz_set_id, qa.completed_at
        HAVING COUNT(DISTINCT q.passage_id) > 1
    LOOP
        SELECT p.id
        INTO primary_passage_id
        FROM attempt_answers aa
        JOIN questions q ON q.id = aa.question_id
        JOIN passages p ON p.id = q.passage_id
        WHERE aa.attempt_id = existing_attempt.id
        ORDER BY p.position
        LIMIT 1;

        UPDATE quiz_attempts qa
        SET passage_id = primary_passage_id,
            score = (
                SELECT COUNT(*)
                FROM attempt_answers aa
                JOIN questions q ON q.id = aa.question_id
                WHERE aa.attempt_id = existing_attempt.id
                  AND q.passage_id = primary_passage_id
                  AND aa.is_correct
            )
        WHERE qa.id = existing_attempt.id;

        FOR attempted_passage IN
            SELECT DISTINCT p.id, p.position
            FROM attempt_answers aa
            JOIN questions q ON q.id = aa.question_id
            JOIN passages p ON p.id = q.passage_id
            WHERE aa.attempt_id = existing_attempt.id
              AND p.id <> primary_passage_id
            ORDER BY p.position
        LOOP
            INSERT INTO quiz_attempts (
                user_id,
                quiz_set_id,
                passage_id,
                attempt_type,
                score,
                completed_at
            )
            SELECT existing_attempt.user_id,
                   existing_attempt.quiz_set_id,
                   attempted_passage.id,
                   'BONUS',
                   COUNT(*) FILTER (WHERE aa.is_correct),
                   existing_attempt.completed_at
            FROM attempt_answers aa
            JOIN questions q ON q.id = aa.question_id
            WHERE aa.attempt_id = existing_attempt.id
              AND q.passage_id = attempted_passage.id
            RETURNING id INTO split_attempt_id;

            INSERT INTO attempt_answers (
                attempt_id,
                question_id,
                selected_option_id,
                is_correct
            )
            SELECT split_attempt_id,
                   aa.question_id,
                   aa.selected_option_id,
                   aa.is_correct
            FROM attempt_answers aa
            JOIN questions q ON q.id = aa.question_id
            WHERE aa.attempt_id = existing_attempt.id
              AND q.passage_id = attempted_passage.id;

            UPDATE answer_reviews ar
            SET source_attempt_id = split_attempt_id
            WHERE ar.source_attempt_id = existing_attempt.id
              AND EXISTS (
                  SELECT 1
                  FROM questions q
                  WHERE q.id = ar.question_id
                    AND q.passage_id = attempted_passage.id
              );

            DELETE FROM attempt_answers aa
            USING questions q
            WHERE aa.question_id = q.id
              AND aa.attempt_id = existing_attempt.id
              AND q.passage_id = attempted_passage.id;
        END LOOP;
    END LOOP;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM quiz_attempts WHERE passage_id IS NULL) THEN
        RAISE EXCEPTION 'Every existing quiz attempt must belong to exactly one passage';
    END IF;
END $$;

ALTER TABLE quiz_attempts
    ALTER COLUMN passage_id SET NOT NULL,
    ADD CONSTRAINT fk_quiz_attempts_quiz_passage
        FOREIGN KEY (quiz_set_id, passage_id)
        REFERENCES passages (quiz_set_id, id),
    ADD CONSTRAINT ux_quiz_attempts_user_quiz_passage
        UNIQUE (user_id, quiz_set_id, passage_id),
    ADD CONSTRAINT ck_quiz_attempts_type
        CHECK (attempt_type IN ('PRIMARY', 'BONUS'));

CREATE UNIQUE INDEX ux_quiz_attempts_one_primary
    ON quiz_attempts (user_id, quiz_set_id)
    WHERE attempt_type = 'PRIMARY';

CREATE INDEX ix_quiz_attempts_user_quiz_type
    ON quiz_attempts (user_id, quiz_set_id, attempt_type);
