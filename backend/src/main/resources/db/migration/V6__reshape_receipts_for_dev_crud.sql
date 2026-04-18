DROP INDEX IF EXISTS idx_receipts_user_policy_year;
DROP INDEX IF EXISTS idx_receipts_relief_category_id;
DROP INDEX IF EXISTS idx_receipts_status;

ALTER TABLE receipts
    ADD COLUMN policy_year INTEGER,
    ADD COLUMN merchant_name VARCHAR(160),
    ADD COLUMN receipt_date DATE,
    ADD COLUMN amount NUMERIC(14, 2),
    ADD COLUMN notes TEXT,
    ADD COLUMN file_name VARCHAR(255),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE receipts
    RENAME COLUMN uploaded_at TO created_at;

UPDATE receipts receipt
SET policy_year = policy_year_ref.year
FROM policy_year policy_year_ref
WHERE receipt.policy_year_id = policy_year_ref.id;

UPDATE receipts
SET merchant_name = 'Imported receipt'
WHERE merchant_name IS NULL;

UPDATE receipts
SET receipt_date = COALESCE(extracted_date, created_at::date)
WHERE receipt_date IS NULL;

UPDATE receipts
SET amount = COALESCE(extracted_amount, 0)
WHERE amount IS NULL;

ALTER TABLE receipts
    ALTER COLUMN relief_category_id DROP NOT NULL,
    ALTER COLUMN file_url DROP NOT NULL,
    ALTER COLUMN policy_year SET NOT NULL,
    ALTER COLUMN merchant_name SET NOT NULL,
    ALTER COLUMN receipt_date SET NOT NULL,
    ALTER COLUMN amount SET NOT NULL;

ALTER TABLE receipts
    DROP CONSTRAINT fk_receipts_policy_year,
    DROP CONSTRAINT chk_receipts_extracted_amount,
    DROP CONSTRAINT chk_receipts_status;

ALTER TABLE receipts
    DROP COLUMN policy_year_id,
    DROP COLUMN extracted_amount,
    DROP COLUMN extracted_date,
    DROP COLUMN status;

ALTER TABLE receipts
    ADD CONSTRAINT fk_receipts_policy_year
        FOREIGN KEY (policy_year) REFERENCES policy_year (year) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_receipts_amount CHECK (amount >= 0);

CREATE INDEX idx_receipts_user_policy_year ON receipts (user_id, policy_year);
CREATE INDEX idx_receipts_relief_category_id ON receipts (relief_category_id);
CREATE INDEX idx_receipts_policy_year ON receipts (policy_year);
