package com.nuvixtech.stockvaluator.api.valuation;

import com.nuvixtech.stockvaluator.api.exception.TickerNotFoundException;
import com.nuvixtech.stockvaluator.api.valuation.controller.ValuationController;
import com.nuvixtech.stockvaluator.api.valuation.dto.ValuationResponse;
import com.nuvixtech.stockvaluator.api.valuation.service.ValuationService;
import com.nuvixtech.stockvaluator.api.config.JpaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = ValuationController.class,
        excludeAutoConfiguration = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JpaConfig.class))
class ValuationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ValuationService valuationService;

    @Test
    void getValuation_existingTicker_returns200WithBody() throws Exception {
        when(valuationService.getLatestValuation("AAPL")).thenReturn(buildResponse("AAPL"));

        mockMvc.perform(get("/api/v1/valuations/AAPL")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.verdict").value("FAIR_VALUE"))
                .andExpect(jsonPath("$.intrinsicValuePerShare").value(200.00));
    }

    @Test
    void getValuation_unknownTicker_returns404() throws Exception {
        when(valuationService.getLatestValuation("UNKNOWN"))
                .thenThrow(new TickerNotFoundException("UNKNOWN"));

        mockMvc.perform(get("/api/v1/valuations/UNKNOWN")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void calculate_existingTicker_returns200WithFreshResult() throws Exception {
        when(valuationService.calculate("MSFT", null)).thenReturn(buildResponse("MSFT"));

        mockMvc.perform(post("/api/v1/valuations/MSFT/calculate")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("MSFT"));

        verify(valuationService).calculate("MSFT", null);
    }

    @Test
    void calculate_unknownTicker_returns404() throws Exception {
        when(valuationService.calculate("UNKNOWN", null))
                .thenThrow(new TickerNotFoundException("UNKNOWN"));

        mockMvc.perform(post("/api/v1/valuations/UNKNOWN/calculate")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    private ValuationResponse buildResponse(String ticker) {
        return new ValuationResponse(
                ticker, ticker + " Inc.", "Technology",
                new BigDecimal("200.00"), new BigDecimal("178.50"),
                new BigDecimal("12.04"), "FAIR_VALUE",
                new BigDecimal("0.089"), new BigDecimal("0.025"), 10,
                new BigDecimal("1000000000000"), new BigDecimal("48000000000"),
                null,
                Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(),
                LocalDateTime.now()
        );
    }
}
