ALTER TABLE generation_logs
    ADD COLUMN manual_retry_count SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE generation_logs
    ADD CONSTRAINT ck_generation_logs_manual_retry_count
        CHECK (manual_retry_count BETWEEN 0 AND 2);
