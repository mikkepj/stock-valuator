package com.nuvixtech.stockvaluator.ingestion.repository;

import com.nuvixtech.stockvaluator.ingestion.entity.FinancialStatement;
import com.nuvixtech.stockvaluator.ingestion.entity.StatementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FinancialStatementRepository extends JpaRepository<FinancialStatement, Long> {

    List<FinancialStatement> findByCompanyTickerAndStatementTypeOrderByFiscalYearDesc(
            String ticker, StatementType statementType);

    Optional<FinancialStatement> findByCompanyIdAndFiscalYearAndPeriodAndStatementType(
            Long companyId, Integer fiscalYear, String period, StatementType statementType);

    List<FinancialStatement> findByCompanyTickerOrderByFiscalYearDesc(String ticker);
}
