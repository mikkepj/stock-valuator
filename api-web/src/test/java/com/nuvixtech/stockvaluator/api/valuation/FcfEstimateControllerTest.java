package com.nuvixtech.stockvaluator.api.valuation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuvixtech.stockvaluator.api.config.JpaConfig;
import com.nuvixtech.stockvaluator.api.exception.TickerNotFoundException;
import com.nuvixtech.stockvaluator.api.valuation.controller.FcfEstimateController;
import com.nuvixtech.stockvaluator.api.valuation.dto.FcfEstimateRequest;
import com.nuvixtech.stockvaluator.api.valuation.service.FcfEstimateService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = FcfEstimateController.class,
        excludeAutoConfiguration = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JpaConfig.class))
class FcfEstimateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FcfEstimateService fcfEstimateService;

    @Test
    void saveEstimates_validRequest_returns204() throws Exception {
        var request = new FcfEstimateRequest(List.of(
                new BigDecimal("99000000000"),
                new BigDecimal("122000000000"),
                new BigDecimal("146000000000"),
                new BigDecimal("174000000000"),
                new BigDecimal("210000000000")
        ));

        mockMvc.perform(post("/api/v1/companies/MSFT/fcf-estimates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(fcfEstimateService).save("MSFT", request.estimates());
    }

    @Test
    void saveEstimates_unknownTicker_returns404() throws Exception {
        var request = new FcfEstimateRequest(List.of(new BigDecimal("99000000000")));

        doThrow(new TickerNotFoundException("UNKNOWN"))
                .when(fcfEstimateService).save(eq("UNKNOWN"), any());

        mockMvc.perform(post("/api/v1/companies/UNKNOWN/fcf-estimates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void saveEstimates_emptyList_returns400() throws Exception {
        var request = new FcfEstimateRequest(List.of());

        mockMvc.perform(post("/api/v1/companies/MSFT/fcf-estimates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
