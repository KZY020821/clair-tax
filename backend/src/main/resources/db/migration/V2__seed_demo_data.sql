INSERT INTO policy_year (id, year, status, created_at)
VALUES
    ('11111111-1111-4111-8111-111111111111', 2025, 'published', '2025-01-01T00:00:00Z'),
    ('11111111-1111-4111-8111-111111111112', 2026, 'draft', '2026-01-01T00:00:00Z');

INSERT INTO users (id, email, created_at, last_login_at)
VALUES
    (
        '22222222-2222-4222-8222-222222222221',
        'demo.user@clairtax.my',
        '2025-01-05T09:30:00Z',
        '2026-03-20T08:15:00Z'
    );

INSERT INTO tax_brackets (id, policy_year_id, min_income, max_income, tax_rate)
VALUES
    (
        '33333333-3333-4333-8333-333333333331',
        '11111111-1111-4111-8111-111111111111',
        0.00,
        50000.00,
        1.00
    ),
    (
        '33333333-3333-4333-8333-333333333332',
        '11111111-1111-4111-8111-111111111111',
        50000.01,
        100000.00,
        3.00
    ),
    (
        '33333333-3333-4333-8333-333333333333',
        '11111111-1111-4111-8111-111111111111',
        100000.01,
        NULL,
        5.00
    ),
    (
        '33333333-3333-4333-8333-333333333334',
        '11111111-1111-4111-8111-111111111112',
        0.00,
        60000.00,
        1.00
    ),
    (
        '33333333-3333-4333-8333-333333333335',
        '11111111-1111-4111-8111-111111111112',
        60000.01,
        NULL,
        4.00
    );

INSERT INTO relief_categories (
    id,
    policy_year_id,
    name,
    description,
    max_amount,
    type,
    requires_receipt
)
VALUES
    (
        '44444444-4444-4444-8444-444444444441',
        '11111111-1111-4111-8111-111111111111',
        'Self and Dependent',
        'Baseline relief for the taxpayer and dependents.',
        9000.00,
        'individual',
        FALSE
    ),
    (
        '44444444-4444-4444-8444-444444444442',
        '11111111-1111-4111-8111-111111111111',
        'Lifestyle',
        'Books, devices, sports equipment, and internet subscriptions.',
        2500.00,
        'lifestyle',
        TRUE
    ),
    (
        '44444444-4444-4444-8444-444444444443',
        '11111111-1111-4111-8111-111111111111',
        'Medical Expenses for Parents',
        'Eligible medical support and treatment for parents.',
        8000.00,
        'family',
        TRUE
    ),
    (
        '44444444-4444-4444-8444-444444444444',
        '11111111-1111-4111-8111-111111111112',
        'Digital Learning',
        'Draft relief for work and study-related devices.',
        3000.00,
        'lifestyle',
        TRUE
    );

INSERT INTO user_tax_profile (
    id,
    user_id,
    policy_year_id,
    gross_income,
    total_relief,
    taxable_income,
    tax_amount
)
VALUES
    (
        '55555555-5555-4555-8555-555555555551',
        '22222222-2222-4222-8222-222222222221',
        '11111111-1111-4111-8111-111111111111',
        85000.00,
        7000.00,
        78000.00,
        2340.00
    );

INSERT INTO user_relief_claims (id, user_tax_profile_id, relief_category_id, claimed_amount)
VALUES
    (
        '66666666-6666-4666-8666-666666666661',
        '55555555-5555-4555-8555-555555555551',
        '44444444-4444-4444-8444-444444444441',
        4000.00
    ),
    (
        '66666666-6666-4666-8666-666666666662',
        '55555555-5555-4555-8555-555555555551',
        '44444444-4444-4444-8444-444444444442',
        1800.00
    ),
    (
        '66666666-6666-4666-8666-666666666663',
        '55555555-5555-4555-8555-555555555551',
        '44444444-4444-4444-8444-444444444443',
        1200.00
    );

INSERT INTO receipts (
    id,
    user_id,
    policy_year_id,
    relief_category_id,
    file_url,
    uploaded_at,
    extracted_amount,
    extracted_date,
    status
)
VALUES
    (
        '77777777-7777-4777-8777-777777777771',
        '22222222-2222-4222-8222-222222222221',
        '11111111-1111-4111-8111-111111111111',
        '44444444-4444-4444-8444-444444444442',
        's3://clair-tax-demo/receipts/lifestyle-2025-01.pdf',
        '2025-07-10T10:00:00Z',
        900.00,
        '2025-07-09',
        'verified'
    ),
    (
        '77777777-7777-4777-8777-777777777772',
        '22222222-2222-4222-8222-222222222221',
        '11111111-1111-4111-8111-111111111111',
        '44444444-4444-4444-8444-444444444443',
        's3://clair-tax-demo/receipts/medical-2025-02.pdf',
        '2025-09-18T12:00:00Z',
        1200.00,
        '2025-09-17',
        'processed'
    );

INSERT INTO audit_logs (id, user_id, action, metadata, created_at)
VALUES
    (
        '88888888-8888-4888-8888-888888888881',
        '22222222-2222-4222-8222-222222222221',
        'profile.created',
        '{"policyYear": 2025}'::jsonb,
        '2025-01-05T09:35:00Z'
    ),
    (
        '88888888-8888-4888-8888-888888888882',
        '22222222-2222-4222-8222-222222222221',
        'receipt.uploaded',
        '{"receiptId": "77777777-7777-4777-8777-777777777771"}'::jsonb,
        '2025-07-10T10:01:00Z'
    );

INSERT INTO ai_suggestions (id, user_id, policy_year_id, suggestion_text, potential_tax_saving, created_at)
VALUES
    (
        '99999999-9999-4999-8999-999999999991',
        '22222222-2222-4222-8222-222222222221',
        '11111111-1111-4111-8111-111111111111',
        'You still have room in the lifestyle category based on your uploaded receipts.',
        250.00,
        '2025-10-15T09:00:00Z'
    ),
    (
        '99999999-9999-4999-8999-999999999992',
        '22222222-2222-4222-8222-222222222221',
        '11111111-1111-4111-8111-111111111111',
        'Consider reviewing parent medical expenses before final submission.',
        180.00,
        '2025-10-16T09:00:00Z'
    );
