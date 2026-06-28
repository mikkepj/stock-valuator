package com.nuvixtech.stockvaluator.ingestion.service;

import com.nuvixtech.stockvaluator.ingestion.client.ExchangeRateApiClient;
import com.nuvixtech.stockvaluator.ingestion.exception.CurrencyConversionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrencyConversionServiceTest {

    @Mock
    private ExchangeRateApiClient exchangeRateApiClient;

    private CurrencyConversionService service;

    @BeforeEach
    void setUp() {
        service = new CurrencyConversionService(exchangeRateApiClient);
    }

    @Test
    void getExchangeRateToUsd_usdCurrency_returnsOneWithoutCallingApi() {
        BigDecimal rate = service.getExchangeRateToUsd("USD");

        assertThat(rate).isEqualByComparingTo(BigDecimal.ONE);
        verifyNoInteractions(exchangeRateApiClient);
    }

    @Test
    void getExchangeRateToUsd_nonUsdCurrency_returnsRateFromApi() {
        BigDecimal expectedRate = new BigDecimal("0.0308");
        when(exchangeRateApiClient.getUsdRate("TWD")).thenReturn(Optional.of(expectedRate));

        BigDecimal rate = service.getExchangeRateToUsd("TWD");

        assertThat(rate).isEqualByComparingTo(expectedRate);
    }

    @Test
    void getExchangeRateToUsd_apiReturnsEmpty_throwsCurrencyConversionException() {
        when(exchangeRateApiClient.getUsdRate("XYZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getExchangeRateToUsd("XYZ"))
                .isInstanceOf(CurrencyConversionException.class)
                .hasMessageContaining("XYZ");
    }
}
