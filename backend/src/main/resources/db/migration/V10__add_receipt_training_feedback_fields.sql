ALTER TABLE receipt_extraction_results
    ADD COLUMN error_code VARCHAR(120),
    ADD COLUMN error_message TEXT;

ALTER TABLE receipt_review_actions
    ADD COLUMN invalid_reason_code VARCHAR(120),
    ADD COLUMN invalid_reason_message TEXT;
