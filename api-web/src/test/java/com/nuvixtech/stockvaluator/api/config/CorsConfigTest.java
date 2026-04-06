package com.nuvixtech.stockvaluator.api.config;

import com.nuvixtech.stockvaluator.api.valuation.controller.ValuationController;
import com.nuvixtech.stockvaluator.api.valuation.dto.ValuationResponse;
import com.nuvixtech.stockvaluator.api.valuation.service.ValuationService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = ValuationController.class,
        excludeAutoConfiguration = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JpaConfig.class))
class CorsConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ValuationService valuationService;

    @Test
    void preflight_allowedOrigin_returns200WithCorsHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/valuations/AAPL")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void preflight_allowedOriginProd_returns200WithCorsHeaders() throws Exception {
        when(valuationService.getLatestValuation("AAPL")).thenReturn(buildResponse());

        mockMvc.perform(options("/api/v1/valuations/AAPL")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    @Test
    void preflight_allowsPostMethod() throws Exception {
        mockMvc.perform(options("/api/v1/valuations/AAPL/calculate")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("POST")));
    }

    private ValuationResponse buildResponse() {
        return new ValuationResponse(
                "AAPL", "Apple Inc.", "Technology",
                new BigDecimal("200.00"), new BigDecimal("178.50"),
                new BigDecimal("12.04"), "FAIR_VALUE",
                new BigDecimal("0.089"), new BigDecimal("0.025"), 10,
                new BigDecimal("1000000000000"), new BigDecimal("48000000000"),
                Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(),
                LocalDateTime.now()
        );
    }
}
