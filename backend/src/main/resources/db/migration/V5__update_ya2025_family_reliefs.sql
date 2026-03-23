ALTER TABLE relief_categories
    ADD COLUMN max_quantity INTEGER;

ALTER TABLE relief_categories
    ADD CONSTRAINT chk_relief_categories_max_quantity
        CHECK (max_quantity IS NULL OR max_quantity >= 0);

UPDATE relief_categories
SET
    description = 'Fixed relief if the husband or wife is certified as disabled.',
    max_amount = 6000.00,
    unit_amount = 6000.00,
    requires_category_code = NULL
WHERE policy_year_id = '11111111-1111-4111-8111-111111111111'
  AND code = 'disabled_spouse';

UPDATE relief_categories
SET
    description = 'Relief per unmarried child aged below 18 years old, with no quantity cap.',
    max_amount = 2000.00,
    unit_amount = 2000.00,
    max_quantity = NULL
WHERE policy_year_id = '11111111-1111-4111-8111-111111111111'
  AND code = 'child_below_18';

UPDATE relief_categories
SET
    description = 'Relief per unmarried disabled child in diploma, degree or higher education, with no quantity cap.',
    max_amount = 8000.00,
    unit_amount = 8000.00,
    max_quantity = NULL
WHERE policy_year_id = '11111111-1111-4111-8111-111111111111'
  AND code = 'disabled_child_higher_education';
