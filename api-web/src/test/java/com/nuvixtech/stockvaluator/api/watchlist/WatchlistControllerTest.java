package com.nuvixtech.stockvaluator.api.watchlist;

import com.nuvixtech.stockvaluator.api.exception.TickerNotFoundException;
import com.nuvixtech.stockvaluator.api.valuation.dto.WatchlistItemResponse;
import com.nuvixtech.stockvaluator.api.watchlist.controller.WatchlistController;
import com.nuvixtech.stockvaluator.api.watchlist.service.WatchlistService;
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
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = WatchlistController.class,
        excludeAutoConfiguration = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JpaConfig.class))
class WatchlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WatchlistService watchlistService;

    @Test
    void getWatchlist_returnsListOf200() throws Exception {
        when(watchlistService.getWatchlist()).thenReturn(List.of(
                new WatchlistItemResponse("AAPL", "Apple Inc.", new BigDecimal("178.50"),
                        new BigDecimal("200.00"), new BigDecimal("12.04"), "FAIR_VALUE"),
                new WatchlistItemResponse("MSFT", "Microsoft Corp.", new BigDecimal("310.00"),
                        new BigDecimal("380.00"), new BigDecimal("22.58"), "UNDERVALUED")
        ));

        mockMvc.perform(get("/api/v1/watchlist").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[1].verdict").value("UNDERVALUED"));
    }

    @Test
    void getWatchlist_empty_returnsEmptyArray() throws Exception {
        when(watchlistService.getWatchlist()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/watchlist").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void addToWatchlist_newTicker_returns201() throws Exception {
        var item = new WatchlistItemResponse("GOOG", "Alphabet Inc.",
                new BigDecimal("150.00"), new BigDecimal("180.00"),
                new BigDecimal("20.00"), "UNDERVALUED");
        when(watchlistService.add("GOOG")).thenReturn(item);

        mockMvc.perform(post("/api/v1/watchlist/GOOG")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticker").value("GOOG"));
    }

    @Test
    void addToWatchlist_unknownTicker_returns404() throws Exception {
        when(watchlistService.add("UNKNOWN"))
                .thenThrow(new TickerNotFoundException("UNKNOWN"));

        mockMvc.perform(post("/api/v1/watchlist/UNKNOWN")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeFromWatchlist_existingTicker_returns204() throws Exception {
        doNothing().when(watchlistService).remove("AAPL");

        mockMvc.perform(delete("/api/v1/watchlist/AAPL"))
                .andExpect(status().isNoContent());
    }

    @Test
    void removeFromWatchlist_notInWatchlist_returns404() throws Exception {
        doThrow(new TickerNotFoundException("TSLA")).when(watchlistService).remove("TSLA");

        mockMvc.perform(delete("/api/v1/watchlist/TSLA"))
                .andExpect(status().isNotFound());
    }
}
