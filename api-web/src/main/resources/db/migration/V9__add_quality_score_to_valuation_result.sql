ALTER TABLE valuation_result
    ADD COLUMN IF NOT EXISTS quality_score INTEGER;
