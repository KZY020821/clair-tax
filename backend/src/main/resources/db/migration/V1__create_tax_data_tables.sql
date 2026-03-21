CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE policy_year (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    year INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_policy_year_year UNIQUE (year),
    CONSTRAINT chk_policy_year_status CHECK (status IN ('draft', 'published'))
);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(320) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMPTZ,
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE tax_brackets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_year_id UUID NOT NULL,
    min_income NUMERIC(14, 2) NOT NULL,
    max_income NUMERIC(14, 2),
    tax_rate NUMERIC(7, 4) NOT NULL,
    CONSTRAINT fk_tax_brackets_policy_year
        FOREIGN KEY (policy_year_id) REFERENCES policy_year (id) ON DELETE CASCADE,
    CONSTRAINT chk_tax_brackets_min_income CHECK (min_income >= 0),
    CONSTRAINT chk_tax_brackets_max_income CHECK (max_income IS NULL OR max_income >= min_income),
    CONSTRAINT chk_tax_brackets_tax_rate CHECK (tax_rate >= 0 AND tax_rate <= 100)
);

CREATE TABLE relief_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_year_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    description TEXT NOT NULL,
    max_amount NUMERIC(14, 2) NOT NULL,
    type VARCHAR(16) NOT NULL,
    requires_receipt BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_relief_categories_policy_year
        FOREIGN KEY (policy_year_id) REFERENCES policy_year (id) ON DELETE CASCADE,
    CONSTRAINT uq_relief_categories_policy_year_name UNIQUE (policy_year_id, name),
    CONSTRAINT chk_relief_categories_max_amount CHECK (max_amount >= 0),
    CONSTRAINT chk_relief_categories_type CHECK (type IN ('individual', 'family', 'lifestyle'))
);

CREATE TABLE user_tax_profile (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    policy_year_id UUID NOT NULL,
    gross_income NUMERIC(14, 2) NOT NULL,
    total_relief NUMERIC(14, 2) NOT NULL DEFAULT 0,
    taxable_income NUMERIC(14, 2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    CONSTRAINT fk_user_tax_profile_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_tax_profile_policy_year
        FOREIGN KEY (policy_year_id) REFERENCES policy_year (id) ON DELETE RESTRICT,
    CONSTRAINT uq_user_tax_profile_user_policy_year UNIQUE (user_id, policy_year_id),
    CONSTRAINT chk_user_tax_profile_gross_income CHECK (gross_income >= 0),
    CONSTRAINT chk_user_tax_profile_total_relief CHECK (total_relief >= 0),
    CONSTRAINT chk_user_tax_profile_taxable_income CHECK (taxable_income >= 0),
    CONSTRAINT chk_user_tax_profile_tax_amount CHECK (tax_amount >= 0)
);

CREATE TABLE user_relief_claims (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_tax_profile_id UUID NOT NULL,
    relief_category_id UUID NOT NULL,
    claimed_amount NUMERIC(14, 2) NOT NULL,
    CONSTRAINT fk_user_relief_claims_profile
        FOREIGN KEY (user_tax_profile_id) REFERENCES user_tax_profile (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_relief_claims_relief_category
        FOREIGN KEY (relief_category_id) REFERENCES relief_categories (id) ON DELETE RESTRICT,
    CONSTRAINT uq_user_relief_claims_profile_category UNIQUE (user_tax_profile_id, relief_category_id),
    CONSTRAINT chk_user_relief_claims_claimed_amount CHECK (claimed_amount >= 0)
);

CREATE TABLE receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    policy_year_id UUID NOT NULL,
    relief_category_id UUID NOT NULL,
    file_url TEXT NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    extracted_amount NUMERIC(14, 2),
    extracted_date DATE,
    status VARCHAR(16) NOT NULL,
    CONSTRAINT fk_receipts_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_receipts_policy_year
        FOREIGN KEY (policy_year_id) REFERENCES policy_year (id) ON DELETE RESTRICT,
    CONSTRAINT fk_receipts_relief_category
        FOREIGN KEY (relief_category_id) REFERENCES relief_categories (id) ON DELETE RESTRICT,
    CONSTRAINT chk_receipts_extracted_amount CHECK (extracted_amount IS NULL OR extracted_amount >= 0),
    CONSTRAINT chk_receipts_status CHECK (status IN ('pending', 'processed', 'verified'))
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    action VARCHAR(120) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_logs_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE ai_suggestions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    policy_year_id UUID NOT NULL,
    suggestion_text TEXT NOT NULL,
    potential_tax_saving NUMERIC(14, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_suggestions_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_suggestions_policy_year
        FOREIGN KEY (policy_year_id) REFERENCES policy_year (id) ON DELETE RESTRICT,
    CONSTRAINT chk_ai_suggestions_potential_tax_saving CHECK (potential_tax_saving >= 0)
);

CREATE INDEX idx_policy_year_status ON policy_year (status);
CREATE INDEX idx_tax_brackets_policy_year_id ON tax_brackets (policy_year_id);
CREATE UNIQUE INDEX uq_tax_brackets_open_ended_policy_year
    ON tax_brackets (policy_year_id)
    WHERE max_income IS NULL;
CREATE INDEX idx_relief_categories_policy_year_id ON relief_categories (policy_year_id);
CREATE INDEX idx_user_tax_profile_policy_year_id ON user_tax_profile (policy_year_id);
CREATE INDEX idx_user_relief_claims_profile_id ON user_relief_claims (user_tax_profile_id);
CREATE INDEX idx_user_relief_claims_relief_category_id ON user_relief_claims (relief_category_id);
CREATE INDEX idx_receipts_user_policy_year ON receipts (user_id, policy_year_id);
CREATE INDEX idx_receipts_relief_category_id ON receipts (relief_category_id);
CREATE INDEX idx_receipts_status ON receipts (status);
CREATE INDEX idx_audit_logs_user_created_at ON audit_logs (user_id, created_at DESC);
CREATE INDEX idx_ai_suggestions_user_policy_year ON ai_suggestions (user_id, policy_year_id);
