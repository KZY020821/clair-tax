ALTER TABLE receipts
    DROP CONSTRAINT IF EXISTS chk_receipts_status;

UPDATE receipts
SET status = UPPER(status)
WHERE status IS NOT NULL
  AND status <> UPPER(status);

ALTER TABLE receipts
    ADD CONSTRAINT chk_receipts_status
        CHECK (status IN ('UPLOADED', 'PROCESSING', 'PROCESSED', 'VERIFIED', 'REJECTED', 'FAILED'));
