DROP INDEX IF EXISTS idx_receipts_user_policy_year;

CREATE TABLE user_policy_years (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    policy_year_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_policy_years_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_policy_years_policy_year
        FOREIGN KEY (policy_year_id) REFERENCES policy_year (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_policy_years_user_policy_year UNIQUE (user_id, policy_year_id)
);

CREATE INDEX idx_user_policy_years_user_id ON user_policy_years (user_id);
CREATE INDEX idx_user_policy_years_policy_year_id ON user_policy_years (policy_year_id);

INSERT INTO user_policy_years (user_id, policy_year_id, created_at, updated_at)
SELECT DISTINCT
    user_tax_profile.user_id,
    user_tax_profile.policy_year_id,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM user_tax_profile
ON CONFLICT (user_id, policy_year_id) DO NOTHING;

INSERT INTO user_policy_years (user_id, policy_year_id, created_at, updated_at)
SELECT DISTINCT
    receipts.user_id,
    policy_year.id,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM receipts
JOIN policy_year ON policy_year.year = receipts.policy_year
ON CONFLICT (user_id, policy_year_id) DO NOTHING;

ALTER TABLE user_relief_claims
    ADD COLUMN user_policy_year_id UUID;

UPDATE user_relief_claims
SET user_policy_year_id = user_policy_years.id
FROM user_tax_profile
JOIN user_policy_years
    ON user_policy_years.user_id = user_tax_profile.user_id
    AND user_policy_years.policy_year_id = user_tax_profile.policy_year_id
WHERE user_relief_claims.user_tax_profile_id = user_tax_profile.id;

ALTER TABLE user_relief_claims
    ALTER COLUMN user_policy_year_id SET NOT NULL;

ALTER TABLE user_relief_claims
    ADD CONSTRAINT fk_user_relief_claims_user_policy_year
        FOREIGN KEY (user_policy_year_id) REFERENCES user_policy_years (id) ON DELETE CASCADE;

ALTER TABLE user_relief_claims
    DROP CONSTRAINT uq_user_relief_claims_profile_category,
    DROP CONSTRAINT fk_user_relief_claims_profile;

DROP INDEX IF EXISTS idx_user_relief_claims_profile_id;

ALTER TABLE user_relief_claims
    DROP COLUMN user_tax_profile_id;

ALTER TABLE user_relief_claims
    ADD CONSTRAINT uq_user_relief_claims_user_policy_year_category
        UNIQUE (user_policy_year_id, relief_category_id);

CREATE INDEX idx_user_relief_claims_user_policy_year_id
    ON user_relief_claims (user_policy_year_id);

ALTER TABLE receipts
    ADD COLUMN user_policy_year_id UUID;

UPDATE receipts
SET user_policy_year_id = user_policy_years.id
FROM user_policy_years
JOIN policy_year ON policy_year.id = user_policy_years.policy_year_id
WHERE receipts.user_id = user_policy_years.user_id
  AND receipts.policy_year = policy_year.year;

ALTER TABLE receipts
    ALTER COLUMN user_policy_year_id SET NOT NULL;

ALTER TABLE receipts
    ADD CONSTRAINT fk_receipts_user_policy_year
        FOREIGN KEY (user_policy_year_id) REFERENCES user_policy_years (id) ON DELETE CASCADE;

ALTER TABLE receipts
    DROP CONSTRAINT fk_receipts_user,
    DROP CONSTRAINT fk_receipts_policy_year;

ALTER TABLE receipts
    DROP COLUMN user_id,
    DROP COLUMN policy_year;

CREATE INDEX idx_receipts_user_policy_year_id ON receipts (user_policy_year_id);
