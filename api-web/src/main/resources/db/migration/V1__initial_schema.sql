-- =====================================================
-- V1__initial_schema.sql
-- Stock Valuator — Initial database schema
-- =====================================================

-- Company master data
CREATE TABLE company (
    id              BIGSERIAL       PRIMARY KEY,
    ticker          VARCHAR(10)     NOT NULL UNIQUE,
    name            VARCHAR(255)    NOT NULL,
    sector          VARCHAR(100),
    industry        VARCHAR(100),
    exchange        VARCHAR(20),
    currency        VARCHAR(5)      DEFAULT 'USD',
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_company_ticker ON company(ticker);

-- Financial statements (annual data from FMP)
CREATE TABLE financial_statement (
    id              BIGSERIAL       PRIMARY KEY,
    company_id      BIGINT          NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    fiscal_year     INTEGER         NOT NULL,
    period          VARCHAR(10)     NOT NULL DEFAULT 'FY',  -- FY, Q1, Q2, Q3, Q4
    statement_type  VARCHAR(20)     NOT NULL,                -- INCOME, BALANCE, CASHFLOW

    -- Key fields extracted and normalized
    revenue                 NUMERIC(20,2),
    operating_income        NUMERIC(20,2),
    net_income              NUMERIC(20,2),
    ebit                    NUMERIC(20,2),
    ebitda                  NUMERIC(20,2),
    interest_expense        NUMERIC(20,2),
    income_tax_expense      NUMERIC(20,2),
    total_debt              NUMERIC(20,2),
    cash_and_equivalents    NUMERIC(20,2),
    total_equity            NUMERIC(20,2),
    total_assets            NUMERIC(20,2),
    operating_cash_flow     NUMERIC(20,2),
    capital_expenditure     NUMERIC(20,2),  -- stored as positive value
    free_cash_flow          NUMERIC(20,2),
    shares_outstanding      BIGINT,

    -- Raw JSON backup from FMP
    raw_data                JSONB,

    -- Audit
    fetched_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_statement UNIQUE (company_id, fiscal_year, period, statement_type)
);

CREATE INDEX idx_financial_company ON financial_statement(company_id);
CREATE INDEX idx_financial_year ON financial_statement(fiscal_year);

-- Market data snapshot
CREATE TABLE market_data (
    id              BIGSERIAL       PRIMARY KEY,
    company_id      BIGINT          NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    price           NUMERIC(12,4)   NOT NULL,
    market_cap      NUMERIC(20,2),
    beta            NUMERIC(8,4),
    pe_ratio        NUMERIC(10,4),
    fetched_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_market_company ON market_data(company_id);

-- Valuation results (calculated by the engine)
CREATE TABLE valuation_result (
    id                  BIGSERIAL       PRIMARY KEY,
    company_id          BIGINT          NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    calculated_at       TIMESTAMP       NOT NULL DEFAULT NOW(),

    -- DCF outputs
    intrinsic_value     NUMERIC(12,4)   NOT NULL,
    market_price        NUMERIC(12,4)   NOT NULL,
    margin_of_safety    NUMERIC(8,4)    NOT NULL,  -- percentage
    verdict             VARCHAR(20)     NOT NULL,   -- UNDERVALUED, FAIR_VALUE, OVERVALUED

    -- DCF inputs used
    wacc                NUMERIC(8,6),
    terminal_growth     NUMERIC(8,6),
    projection_years    INTEGER,
    terminal_value      NUMERIC(20,2),
    net_debt            NUMERIC(20,2),

    -- Sensitivity matrix as JSON
    sensitivity_matrix  JSONB,

    -- Full breakdown for audit/debugging
    breakdown           JSONB
);

CREATE INDEX idx_valuation_company ON valuation_result(company_id);
CREATE INDEX idx_valuation_date ON valuation_result(calculated_at);

-- User watchlist
CREATE TABLE watchlist (
    id              BIGSERIAL       PRIMARY KEY,
    company_id      BIGINT          NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    added_at        TIMESTAMP       NOT NULL DEFAULT NOW(),
    notes           TEXT,

    CONSTRAINT uq_watchlist_company UNIQUE (company_id)
);
