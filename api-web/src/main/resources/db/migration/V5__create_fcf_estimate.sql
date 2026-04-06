CREATE TABLE IF NOT EXISTS fcf_estimate (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT NOT NULL REFERENCES company(id),
    fiscal_year     INTEGER NOT NULL,
    estimated_fcf   NUMERIC(20, 2) NOT NULL,
    source          VARCHAR(50) NOT NULL DEFAULT 'MANUAL',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_fcf_estimate UNIQUE (company_id, fiscal_year)
);
