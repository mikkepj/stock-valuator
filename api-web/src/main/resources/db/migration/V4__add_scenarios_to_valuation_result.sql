ALTER TABLE valuation_result
    ADD COLUMN IF NOT EXISTS scenarios jsonb;
