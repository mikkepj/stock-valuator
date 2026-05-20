ALTER TABLE valuation_result
    ADD COLUMN IF NOT EXISTS monte_carlo JSONB;
