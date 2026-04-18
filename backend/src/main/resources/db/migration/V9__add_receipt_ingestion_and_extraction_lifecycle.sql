ALTER TABLE receipts
    ADD COLUMN uploaded_at TIMESTAMPTZ,
    ADD COLUMN status VARCHAR(16),
    ADD COLUMN currency_code VARCHAR(3),
    ADD COLUMN s3_bucket VARCHAR(255),
    ADD COLUMN s3_key VARCHAR(255),
    ADD COLUMN mime_type VARCHAR(120),
    ADD COLUMN file_size_bytes BIGINT,
    ADD COLUMN sha256_hash VARCHAR(64),
    ADD COLUMN processing_error_code VARCHAR(120),
    ADD COLUMN processing_error_message TEXT;

UPDATE receipts
SET uploaded_at = COALESCE(created_at, CURRENT_TIMESTAMP),
    status = CASE
        WHEN merchant_name IS NOT NULL AND receipt_date IS NOT NULL AND amount IS NOT NULL THEN 'verified'
        ELSE 'uploaded'
    END,
    s3_bucket = 'clair-tax-receipts',
    s3_key = 'legacy/' || id::text,
    mime_type = 'application/octet-stream',
    file_size_bytes = 0,
    sha256_hash = encode(digest(id::text, 'sha256'), 'hex');

ALTER TABLE receipts
    ALTER COLUMN merchant_name DROP NOT NULL,
    ALTER COLUMN receipt_date DROP NOT NULL,
    ALTER COLUMN amount DROP NOT NULL,
    ALTER COLUMN uploaded_at SET NOT NULL,
    ALTER COLUMN s3_bucket SET NOT NULL,
    ALTER COLUMN s3_key SET NOT NULL,
    ALTER COLUMN mime_type SET NOT NULL,
    ALTER COLUMN file_size_bytes SET NOT NULL,
    ALTER COLUMN sha256_hash SET NOT NULL,
    ALTER COLUMN status SET NOT NULL;

ALTER TABLE receipts
    DROP CONSTRAINT IF EXISTS chk_receipts_status;

ALTER TABLE receipts
    ADD CONSTRAINT chk_receipts_status
        CHECK (status IN ('uploaded', 'processing', 'processed', 'verified', 'rejected', 'failed'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_receipts_s3_key ON receipts (s3_key);
CREATE INDEX IF NOT EXISTS idx_receipts_sha256_hash ON receipts (sha256_hash);

CREATE TABLE receipt_upload_intents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_policy_year_id UUID NOT NULL,
    relief_category_id UUID,
    object_key VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(120) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    uploaded_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_receipt_upload_intents_user_policy_year
        FOREIGN KEY (user_policy_year_id) REFERENCES user_policy_years (id) ON DELETE CASCADE,
    CONSTRAINT fk_receipt_upload_intents_relief_category
        FOREIGN KEY (relief_category_id) REFERENCES relief_categories (id) ON DELETE RESTRICT,
    CONSTRAINT uq_receipt_upload_intents_object_key UNIQUE (object_key)
);

CREATE TABLE receipt_processing_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id UUID NOT NULL,
    job_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    error_code VARCHAR(120),
    error_message TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_receipt_processing_attempts_receipt
        FOREIGN KEY (receipt_id) REFERENCES receipts (id) ON DELETE CASCADE,
    CONSTRAINT uq_receipt_processing_attempts_job_id UNIQUE (job_id),
    CONSTRAINT chk_receipt_processing_attempts_status
        CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE receipt_extraction_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id UUID NOT NULL,
    job_id UUID NOT NULL,
    total_amount NUMERIC(14, 2),
    receipt_date DATE,
    merchant_name VARCHAR(160),
    currency_code VARCHAR(3),
    confidence_score NUMERIC(5, 4) NOT NULL,
    warning_messages TEXT NOT NULL DEFAULT '',
    raw_payload_json TEXT NOT NULL,
    provider_name VARCHAR(120) NOT NULL,
    provider_version VARCHAR(120) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_receipt_extraction_results_receipt
        FOREIGN KEY (receipt_id) REFERENCES receipts (id) ON DELETE CASCADE,
    CONSTRAINT uq_receipt_extraction_results_job_id UNIQUE (job_id),
    CONSTRAINT chk_receipt_extraction_results_confidence
        CHECK (confidence_score >= 0 AND confidence_score <= 1)
);

CREATE TABLE receipt_review_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id UUID NOT NULL,
    action_type VARCHAR(16) NOT NULL,
    merchant_name VARCHAR(160),
    receipt_date DATE,
    amount NUMERIC(14, 2),
    currency_code VARCHAR(3),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_receipt_review_actions_receipt
        FOREIGN KEY (receipt_id) REFERENCES receipts (id) ON DELETE CASCADE,
    CONSTRAINT chk_receipt_review_actions_type
        CHECK (action_type IN ('CONFIRMED', 'REJECTED'))
);
