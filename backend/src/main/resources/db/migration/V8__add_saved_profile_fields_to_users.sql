ALTER TABLE users
    ADD COLUMN is_disabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN marital_status VARCHAR(32) NOT NULL DEFAULT 'single',
    ADD COLUMN spouse_disabled BOOLEAN,
    ADD COLUMN spouse_working BOOLEAN,
    ADD COLUMN has_children BOOLEAN,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE users
    ADD CONSTRAINT chk_users_marital_status
        CHECK (marital_status IN ('single', 'married', 'previously_married'));
