ALTER TABLE financial_statement
    ADD COLUMN IF NOT EXISTS operating_lease_obligations NUMERIC(20, 2),
    ADD COLUMN IF NOT EXISTS pension_liabilities          NUMERIC(20, 2),
    ADD COLUMN IF NOT EXISTS minority_interest            NUMERIC(20, 2);
