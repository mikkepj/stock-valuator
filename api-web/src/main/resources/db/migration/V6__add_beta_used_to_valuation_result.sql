ALTER TABLE valuation_result
    ADD COLUMN IF NOT EXISTS beta_used NUMERIC(6, 4);
