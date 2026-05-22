# Plan: Conversión de moneda para tickers ADR (TSM y similares)

**Rama:** `feature/currency-conversion`  
**Estado:** COMPLETADO ✅

---

## Problema

Para tickers ADR como TSM (Taiwan Semiconductor), FMP API devuelve los estados financieros en la moneda funcional de la empresa (TWD), no en USD. El motor DCF asume inputs en USD, produciendo un `intrinsicValuePerShare` completamente erróneo (~30.662 USD vs ~392 USD real).

**Causa raíz:** `FinancialDataMapper` mapeaba FCFs, EBITDA, deuda y cash 1:1 desde FMP sin considerar la moneda. Adicionalmente, FMP devuelve `currency="USD"` en el `/profile` endpoint para ADRs (moneda de cotización en NYSE), pero los estados financieros están en la moneda funcional. La fuente correcta es `reportedCurrency` dentro del income statement.

**Decisión tomada:** Convertir en la ingesta (Opción A) — la BD almacena siempre en USD; `buildCompanyFinancials()` no cambia.

---

## Archivos creados

- `data-ingestion/.../client/ExchangeRateApiClient.java` — cliente para `open.er-api.com` (gratuito, sin API key)
- `data-ingestion/.../dto/ExchangeRateResponse.java` — DTO para respuesta de ExchangeRate-API
- `data-ingestion/.../dto/fmp/FmpForexQuote.java` — record DTO (no usado en producción; puede eliminarse)
- `data-ingestion/.../exception/CurrencyConversionException.java` — `RuntimeException` con mensaje de moneda
- `data-ingestion/.../service/CurrencyConversionService.java` — `@Service`; USD→retorna 1; no-USD→llama ExchangeRateApiClient
- `data-ingestion/src/test/.../service/CurrencyConversionServiceTest.java` — 3 tests
- `data-ingestion/src/test/.../mapper/FinancialDataMapperTest.java` — 5 tests (conversión con rate=1 y rate≠1)

## Archivos modificados

- `FmpIncomeStatement.java` — añadido campo 12: `reportedCurrency`
- `FinancialDataMapper.java` — los 3 métodos de mapeo reciben `BigDecimal fxRateToUsd`; helper `convert(long, BigDecimal)`
- `FinancialDataService.java` — inyecta `CurrencyConversionService`; lee `reportedCurrency` del primer income statement; pasa tasa al mapper
- `FmpApiClient.java` — eliminado método `getExchangeRate()` (FMP requiere suscripción premium para forex)
- `ScenarioAnalyzer.java` — fix: cap relativo para escenario Optimista cuando CAGR base supera el cap absoluto de 25%
- `ScenarioAnalyzerTest.java` — añadido test `analyze_highCagrAboveCap_optimistaStillGreaterThanBase()`

---

## Decisiones clave durante la implementación

| Decisión | Razón |
|----------|-------|
| ExchangeRate-API en vez de FMP `/fx` | FMP requiere suscripción premium para cotizaciones forex (HTTP 402) |
| `reportedCurrency` del income statement, no `currency` del profile | FMP devuelve `currency="USD"` en el profile para ADRs (moneda de cotización NYSE), pero los financials están en la moneda funcional |
| `ExchangeRateApiClient` separado de `FmpApiClient` | Separación de responsabilidades; URLs base distintas; facilita mocking en tests |
| No convertir `sharesOutstanding`, `beta`, `marketCap` | Son unidades/ratios, no moneda; FMP ya reporta `marketCap` en USD para ADRs |

## Fix adicional resuelto en esta rama

**Bug:** `ScenarioAnalyzer` producía Optimista < Base cuando el CAGR histórico superaba el cap absoluto de 25% (caso TSM con CAGR ~43%).

**Causa:** `optimistaGrowth = min(CAGR × 1.30, 25%)` → con CAGR=43%, el optimista quedaba en 25% < 43% base.

**Fix:** Si `CAGR × 1.30 > 25%`, usar `CAGR × 1.10` — garantiza siempre Optimista > Base > Pesimista.

---

## Campos monetarios convertidos por mapper

**`toIncomeEntity`:** `revenue`, `operatingIncome`, `netIncome`, `ebitda`, `interestExpense`, `incomeTaxExpense`  
**`toBalanceEntity`:** `totalDebt`, `cashAndEquivalents`, `totalEquity`, `totalAssets`  
**`toCashFlowEntity`:** `operatingCashFlow`, `capitalExpenditure`, `freeCashFlow`  
**NO convertidos:** `sharesOutstanding`, `beta`, `marketCap`

---

## Resultado de validación

TSM post-implementación:
- IV base: **$465.46** (vs $30.662 antes de la fix)
- Precio de mercado: $407.15
- Margen de seguridad: +14.3% → `FAIR_VALUE`
- WACC: 10.03%, ROIC: 66.3% — coherentes con TSMC real

---

## Tests al cierre

- `valuation-engine`: 123 tests ✅ (+1 nuevo para ScenarioAnalyzer)
- `data-ingestion`: 8 tests ✅ (+5 nuevos para mapper y CurrencyConversionService)
- `api-web`: 21 tests ✅
- **Total: 152 tests**
