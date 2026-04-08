package com.nuvixtech.stockvaluator.api.valuation.service;

import com.nuvixtech.stockvaluator.api.exception.TickerNotFoundException;
import com.nuvixtech.stockvaluator.ingestion.entity.FcfEstimate;
import com.nuvixtech.stockvaluator.ingestion.repository.CompanyRepository;
import com.nuvixtech.stockvaluator.ingestion.repository.FcfEstimateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class FcfEstimateService {

    private final FcfEstimateRepository fcfEstimateRepository;
    private final CompanyRepository companyRepository;

    public FcfEstimateService(FcfEstimateRepository fcfEstimateRepository,
                               CompanyRepository companyRepository) {
        this.fcfEstimateRepository = fcfEstimateRepository;
        this.companyRepository = companyRepository;
    }

    /**
     * Guarda las estimaciones de FCF para un ticker dado.
     * Reemplaza cualquier estimación previa (upsert por año fiscal).
     * Los años fiscales se asignan desde el año siguiente al actual.
     *
     * @param ticker    ticker de la empresa
     * @param estimates lista de FCF estimados en orden ascendente (año+1, año+2, ...)
     */
    @Transactional
    public void save(String ticker, List<BigDecimal> estimates) {
        String t = ticker.toUpperCase();
        var company = companyRepository.findByTicker(t)
                .orElseThrow(() -> new TickerNotFoundException(t));

        // Eliminar estimaciones previas y hacer flush antes de insertar
        // para evitar violación de restricción única (company_id, fiscal_year)
        fcfEstimateRepository.deleteByCompanyTicker(t);
        fcfEstimateRepository.flush();

        int startYear = LocalDate.now().getYear() + 1;
        for (int i = 0; i < estimates.size(); i++) {
            var estimate = new FcfEstimate(company, startYear + i, estimates.get(i));
            fcfEstimateRepository.save(estimate);
        }
    }

    /**
     * Retorna los FCF estimados ordenados por año ascendente.
     * Lista vacía si no hay estimaciones para el ticker.
     */
    public List<BigDecimal> getEstimates(String ticker) {
        return fcfEstimateRepository
                .findByCompanyTickerOrderByFiscalYearAsc(ticker.toUpperCase())
                .stream()
                .map(FcfEstimate::getEstimatedFcf)
                .toList();
    }
}
