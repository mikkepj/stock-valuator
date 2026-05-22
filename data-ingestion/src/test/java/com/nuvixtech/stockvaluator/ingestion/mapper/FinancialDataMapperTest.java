package com.nuvixtech.stockvaluator.ingestion.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuvixtech.stockvaluator.ingestion.dto.fmp.FmpBalanceSheet;
import com.nuvixtech.stockvaluator.ingestion.dto.fmp.FmpCashFlowStatement;
import com.nuvixtech.stockvaluator.ingestion.dto.fmp.FmpIncomeStatement;
import com.nuvixtech.stockvaluator.ingestion.entity.Company;
import com.nuvixtech.stockvaluator.ingestion.entity.FinancialStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialDataMapperTest {

    private FinancialDataMapper mapper;
    private Company company;

    @BeforeEach
    void setUp() {
        mapper = new FinancialDataMapper(new ObjectMapper());
        company = new Company("TSM", "Taiwan Semiconductor");
        company.setCurrency("TWD");
    }

    @Test
    void toIncomeEntity_usdRate_valuesUnchanged() {
        var dto = new FmpIncomeStatement("2023-12-31", "TSM", "FY",
                1_000_000L, 200_000L, 150_000L, 250_000L,
                10_000L, 30_000L, 5_000_000_000L, "2023", "USD");

        FinancialStatement entity = mapper.toIncomeEntity(dto, company, BigDecimal.ONE);

        assertThat(entity.getEbitda()).isEqualByComparingTo(new BigDecimal("250000"));
        assertThat(entity.getSharesOutstanding()).isEqualTo(5_000_000_000L);
    }

    @Test
    void toIncomeEntity_nonUsdRate_convertsMonetaryFieldsButNotShares() {
        var dto = new FmpIncomeStatement("2023-12-31", "TSM", "FY",
                32_000_000L, 6_400_000L, 4_800_000L, 8_000_000L,
                320_000L, 960_000L, 5_189_000_000L, "2023", "TWD");
        BigDecimal twdToUsd = new BigDecimal("0.031250"); // 1 TWD = 0.03125 USD (32:1)

        FinancialStatement entity = mapper.toIncomeEntity(dto, company, twdToUsd);

        // 8_000_000 TWD × 0.031250 = 250_000 USD
        assertThat(entity.getEbitda()).isEqualByComparingTo(new BigDecimal("250000.000000"));
        // sharesOutstanding NO se convierte
        assertThat(entity.getSharesOutstanding()).isEqualTo(5_189_000_000L);
    }

    @Test
    void toBalanceEntity_nonUsdRate_convertsMonetaryFields() {
        var dto = new FmpBalanceSheet("2023-12-31", "TSM", "FY",
                320_000_000L, 96_000_000L, 640_000_000L, 1_000_000_000L, "2023");
        BigDecimal twdToUsd = new BigDecimal("0.031250");

        FinancialStatement entity = mapper.toBalanceEntity(dto, company, twdToUsd);

        // 320_000_000 TWD × 0.031250 = 10_000_000 USD
        assertThat(entity.getTotalDebt()).isEqualByComparingTo(new BigDecimal("10000000.000000"));
        // 96_000_000 TWD × 0.031250 = 3_000_000 USD
        assertThat(entity.getCashAndEquivalents()).isEqualByComparingTo(new BigDecimal("3000000.000000"));
    }

    @Test
    void toCashFlowEntity_nonUsdRate_convertsFreeCashFlow() {
        var dto = new FmpCashFlowStatement("2023-12-31", "TSM", "FY",
                160_000_000L, -32_000_000L, 128_000_000L, "2023");
        BigDecimal twdToUsd = new BigDecimal("0.031250");

        FinancialStatement entity = mapper.toCashFlowEntity(dto, company, twdToUsd);

        // 128_000_000 TWD × 0.031250 = 4_000_000 USD
        assertThat(entity.getFreeCashFlow()).isEqualByComparingTo(new BigDecimal("4000000.000000"));
    }

    @Test
    void toCashFlowEntity_usdRate_valuesUnchanged() {
        var dto = new FmpCashFlowStatement("2023-12-31", "AAPL", "FY",
                100_000L, -20_000L, 80_000L, "2023");

        FinancialStatement entity = mapper.toCashFlowEntity(dto, company, BigDecimal.ONE);

        assertThat(entity.getFreeCashFlow()).isEqualByComparingTo(new BigDecimal("80000"));
        assertThat(entity.getCapitalExpenditure()).isEqualByComparingTo(new BigDecimal("20000"));
    }
}
