# Plan de Ejecución — DCF Quality Improvements

> **Objetivo:** Mejorar la precisión del motor DCF para acercarse a herramientas profesionales como AlphaSpread, Morningstar y Simply Wall St. Cada mejora ataca una simplificación concreta del engine actual.
>
> **Rama:** `feature/dcf-quality-improvements`
>
> **Directivas:** TDD siempre (test primero). Usar context7 para cualquier librería. Todos los cambios en `valuation-engine` son Java puro sin Spring.

---

## Nivel 1 — Alto impacto, implementación moderada

### Mejora 1 — Tasa impositiva efectiva real

**Problema actual:** `WaccCalculator` usa `DEFAULT_TAX_RATE = 21%` fijo para todos los tickers.
**Impacto:** Para empresas con tasas efectivas muy distintas (NVDA ~12%, AMZN ~8%) el `Kd` se sobre/subestima, distorsionando el WACC.

**Solución:**
- Calcular `taxRate = incomeTaxExpense / (ebitda - interestExpense)` cuando los datos están disponibles
- Fallback a 21% si el ratio produce un valor fuera del rango razonable (0%–50%)
- Agregar campo `effectiveTaxRate` a `ValuationResult.breakdown`

**Archivos afectados:**
- `valuation-engine/.../WaccCalculator.java`
- `valuation-engine/.../DcfCalculator.java` (pasar datos para calcular tasa efectiva)
- `valuation-engine/test/.../WaccCalculatorTest.java`

**Tareas:**
- [x] **TEST** `WaccCalculator_effectiveTaxRate_usesRealRateWhenAvailable`
- [x] **TEST** `WaccCalculator_effectiveTaxRate_fallsBackTo21WhenOutOfRange`
- [x] Implementar cálculo de tasa impositiva efectiva en `WaccCalculator`
- [x] Agregar `effectiveTaxRate` al breakdown en `DcfCalculator`

---

### Mejora 2 — Costo de deuda por spread crediticio (Interest Coverage Ratio)

**Problema actual:** `Kd = interestExpense / totalDebt`. No refleja el costo real de nueva deuda.
**Impacto:** Empresas con deuda antigua (emitida a tasas bajas) tienen un `Kd` artificialmente bajo. Empresas en dificultades financieras, artificialmente alto.

**Solución (tablas Damodaran):**
- Calcular `interestCoverageRatio = ebitda / interestExpense`
- Mapear el ratio a un spread crediticio sobre la tasa libre de riesgo:
  ```
  ICR > 8.5x  → spread 0.63%  (AAA/AA)
  ICR 6.5–8.5 → spread 0.78%  (A+)
  ICR 5.5–6.5 → spread 0.98%  (A)
  ICR 4.25–5.5→ spread 1.13%  (A-)
  ICR 3.0–4.25→ spread 1.67%  (BBB)
  ICR 2.5–3.0 → spread 2.22%  (BB+)
  ICR 2.0–2.5 → spread 2.83%  (BB)
  ICR 1.5–2.0 → spread 3.53%  (B+)
  ICR 1.25–1.5→ spread 4.23%  (B)
  ICR 0.8–1.25→ spread 5.93%  (B-)
  ICR < 0.8   → spread 8.64%  (CCC y menor)
  ```
- `Kd = (riskFreeRate + spread) × (1 - taxRate)`
- Fallback al método actual si `interestExpense == 0` o `ebitda <= 0`
- Agregar `debtSpread` y `impliedCreditRating` al breakdown

**Archivos afectados:**
- `valuation-engine/.../WaccCalculator.java`
- `valuation-engine/test/.../WaccCalculatorTest.java`

**Tareas:**
- [x] **TEST** `WaccCalculator_debtCost_usesSpreadForHighCoverage` (ICR>8.5 → spread 0.63%)
- [x] **TEST** `WaccCalculator_debtCost_usesSpreadForLowCoverage` (ICR<0.8 → spread 8.64%)
- [x] **TEST** `WaccCalculator_debtCost_fallbackWhenNoInterestExpense`
- [x] Implementar tabla de spreads Damodaran en `WaccCalculator`
- [x] Agregar `creditSpread` al breakdown

---

### Mejora 3 — Net Debt ajustado por ítems no operativos

**Problema actual:** `netDebt = totalDebt - cash`. Ignora pension liabilities y operating lease obligations.
**Impacto:** Empresas con grandes compromisos de arrendamiento (retailers, aerolíneas) tienen un enterprise value artificialmente alto.

**Solución:**
- Agregar campos opcionales a `CompanyFinancials`:
  - `operatingLeaseObligations` (BigDecimal, default ZERO)
  - `pensionLiabilities` (BigDecimal, default ZERO)
  - `minorityInterest` (BigDecimal, default ZERO)
- `adjustedNetDebt = totalDebt + operatingLeaseObligations + pensionLiabilities + minorityInterest - cash`
- Agregar los campos al endpoint de ingesta si la API FMP los provee

**Archivos afectados:**
- `valuation-engine/.../CompanyFinancials.java`
- `valuation-engine/.../DcfCalculator.java`
- `data-ingestion/.../entity/FinancialStatement.java`
- `api-web/.../service/ValuationService.java` (buildCompanyFinancials)
- `valuation-engine/test/.../DcfCalculatorTest.java`

**Tareas:**
- [x] **TEST** `DcfCalculator_netDebt_includesLeasesAndPensions`
- [x] **TEST** `DcfCalculator_netDebt_withZeroOptionalFields_matchesBasicFormula`
- [x] Agregar campos opcionales a `CompanyFinancials` (con constructor de conveniencia de 13 args)
- [x] Actualizar `DcfCalculator` para usar `adjustedNetDebt()`
- [x] Migración Flyway `V7__add_adjusted_debt_fields_to_financial_statement.sql`
- [x] Actualizar `buildCompanyFinancials` para leer los nuevos campos

---

### Mejora 4 — Prima de tamaño y liquidez en costo de equity

**Problema actual:** `Ke = Rf + β × MRP`. Sin ajuste por tamaño de empresa.
**Impacto:** Small/mid caps quedan sub-penalizadas en riesgo. Una empresa de $500M market cap tiene el mismo `Ke` que Apple.

**Solución (Duff & Phelps / Damodaran size premium):**
- Determinar prima de tamaño según market cap:
  ```
  market cap > $100B   → sizeRiskPremium = 0.00%
  market cap $10B–100B → sizeRiskPremium = 0.50%
  market cap $2B–10B   → sizeRiskPremium = 1.00%
  market cap $300M–2B  → sizeRiskPremium = 1.50%
  market cap < $300M   → sizeRiskPremium = 2.00%
  ```
- `Ke = Rf + β × MRP + sizeRiskPremium`
- Agregar `sizeRiskPremium` al breakdown

**Archivos afectados:**
- `valuation-engine/.../WaccCalculator.java`
- `valuation-engine/test/.../WaccCalculatorTest.java`

**Tareas:**
- [x] **TEST** `WaccCalculator_sizeRiskPremium_zeroForMegaCap`
- [x] **TEST** `WaccCalculator_sizeRiskPremium_appliedForSmallCap`
- [x] Implementar tabla de primas de tamaño en `WaccCalculator`
- [x] Agregar `sizeRiskPremium` al breakdown

---

## Nivel 2 — Mejora de precisión del análisis

### Mejora 5 — Proyección FCF en dos etapas con ROIC/Reinvestment Rate ✅

**Problema actual:** Decay lineal simple de año 1 a N. No modela la dinámica inversión-crecimiento.
**Impacto:** Empresas con alto capex (semiconductors, infrastructure) crecen porque reinvierten. El modelo actual no captura esa relación.

**Solución (modelo Damodaran FCFF de dos etapas):**
- **Fase 1 (años 1-5):** Proyectar FCFF usando `growthRate = ROIC × reinvestmentRate`
  - `ROIC = NOPAT / investedCapital` donde `NOPAT = ebitda × (1 - taxRate)` e `investedCapital = totalDebt + totalEquity - cash`
  - `reinvestmentRate = capex / NOPAT` (estimado del más reciente)
- **Fase 2 (años 6-10):** Decay lineal desde tasa implícita de fase 1 hacia tasa terminal
- Si los datos para ROIC no están disponibles, mantener comportamiento actual como fallback

**Archivos afectados:**
- `valuation-engine/.../FreeCashFlowProjector.java`
- `valuation-engine/.../CompanyFinancials.java` (agregar `capitalExpenditure`)
- `valuation-engine/test/.../FreeCashFlowProjectorTest.java`

**Tareas:**
- [x] **TEST** `FreeCashFlowProjector_twoStage_phase1UsesRoicGrowth`
- [x] **TEST** `FreeCashFlowProjector_twoStage_phase2DecaysToTerminal`
- [x] **TEST** `FreeCashFlowProjector_twoStage_fallbackWhenRoicDataMissing`
- [x] Agregar `capitalExpenditure` a `CompanyFinancials`
- [x] Implementar proyección de dos etapas en `FreeCashFlowProjector`

---

### Mejora 6 — Validador ROIC vs WACC (consistencia del crecimiento) ✅

**Problema actual:** El modelo proyecta crecimiento sin verificar si la empresa tiene el retorno sobre el capital para sostenerlo.
**Impacto:** Una empresa con ROIC del 5% no puede crecer al 15% de forma sostenible sin destruir valor.

**Solución:**
- Calcular `ROIC = NOPAT / investedCapital` con los datos disponibles
- Si `avgProjectedGrowth > ROIC` → agregar advertencia `"growthExceedsRoic": 1` en breakdown
- Calcular `maxSustainableGrowth = ROIC × reinvestmentRate` y exponerlo en breakdown
- No bloquear el cálculo, solo agregar señales de alerta que el FE puede mostrar

**Archivos afectados:**
- `valuation-engine/.../DcfCalculator.java`
- `valuation-engine/test/.../DcfCalculatorTest.java`

**Tareas:**
- [x] **TEST** `DcfCalculator_roicWarning_setWhenGrowthExceedsRoic`
- [x] **TEST** `DcfCalculator_roicWarning_notSetWhenGrowthBelowRoic`
- [x] Implementar cálculo ROIC y advertencias en `DcfCalculator`
- [x] Agregar `roic`, `maxSustainableGrowth`, `growthExceedsRoic` al breakdown

---

### Mejora 7 — Exit Multiple como método alternativo para Terminal Value

**Problema actual:** Solo Gordon Growth Model para terminal value. Es extremadamente sensible a `WACC - g`.
**Impacto:** Una variación de 0.5% en WACC puede cambiar el IV en 20-30%. Los profesionales usan Exit Multiple como segunda opinión.

**Solución:**
- Agregar tabla de `EV/EBITDA` de salida por sector:
  ```
  Technology       → 20x
  Semiconductors   → 18x
  Software         → 25x
  Healthcare       → 15x
  Consumer Goods   → 14x
  Energy           → 8x
  Financials       → 12x
  Industrials      → 12x
  Default          → 14x
  ```
- `TV_exitMultiple = EBITDA_N × sectorMultiple / (1 + WACC)^N`
- Exponer ambos en breakdown: `terminalValueGordon` y `terminalValueExitMultiple`
- El IV base sigue usando Gordon Growth; Exit Multiple se muestra como referencia

**Archivos afectados:**
- `valuation-engine/.../TerminalValueCalculator.java`
- `valuation-engine/.../CompanyFinancials.java` (agregar `sector`)
- `valuation-engine/.../ValuationResult.java` (breakdown con ambos TV)
- `valuation-engine/test/.../TerminalValueCalculatorTest.java`

**Tareas:**
- [x] **TEST** `TerminalValueCalculator_exitMultiple_technologySector`
- [x] **TEST** `TerminalValueCalculator_exitMultiple_defaultSectorWhenUnknown`
- [x] **TEST** `TerminalValueCalculator_exitMultiple_isLowerThanGordonForHighGrowthAssumptions`
- [x] Agregar `sector` a `CompanyFinancials`
- [x] Implementar `calculateExitMultiple()` en `TerminalValueCalculator`
- [x] Exponer `terminalValue` y `terminalValueExitMultiple` en breakdown

---

## Nivel 3 — Diferenciadores respecto a herramientas básicas

### Mejora 8 — Parámetros por sector (Sector-Aware DCF) ✅

**Problema actual:** Todos los tickers usan los mismos defaults de WACC, terminal growth y MRP.
**Impacto:** Una empresa de semiconductores y una de utilities tienen perfiles de riesgo radicalmente distintos.

**Solución:**
- Crear clase `SectorDefaults` en `valuation-engine` con tabla de defaults por sector:
  ```
  Sector             | ERP   | Terminal Growth
  Technology         | 4.5%  | 3.0%
  Semiconductors     | 4.5%  | 3.0%
  Software (SaaS)    | 4.5%  | 3.5%
  Healthcare         | 4.5%  | 2.5%
  Consumer Defensive | 4.5%  | 2.0%
  Energy             | 4.5%  | 1.5%
  Financials         | 5.0%  | 2.5%
  Industrials        | 4.5%  | 2.5%
  Utilities          | 4.5%  | 2.0%
  ```
- Si `CompanyFinancials.sector` está disponible, `ValuationService` usa parámetros sectoriales
- El `betaOverride` del usuario sigue prevaleciendo sobre el beta sectorial

**Archivos afectados:**
- `valuation-engine/.../SectorDefaults.java` (nuevo)
- `api-web/.../service/ValuationService.java`
- `valuation-engine/test/.../SectorDefaultsTest.java` (nuevo, 6 tests)

**Tareas:**
- [x] **TEST** `SectorDefaults_technologySector_returnsSectorBeta`
- [x] **TEST** `SectorDefaults_unknownSector_returnsGlobalDefaults`
- [x] **TEST** `SectorDefaults_nullSector_returnsGlobalDefaults`
- [x] Crear clase `SectorDefaults` con tabla Damodaran
- [x] Actualizar `ValuationService` para usar `SectorDefaults.forSector()` cuando sector disponible

---

### Mejora 9 — Simulación Monte Carlo sobre FCF y WACC ✅

**Problema actual:** Solo 3 escenarios con multiplicadores arbitrarios (1.30x / 0.75x).
**Impacto:** No captura la distribución real de posibles valores. Oculta la incertidumbre real del modelo.

**Solución:**
- Generar N=1000 simulaciones variando simultáneamente:
  - `growthRate ~ Normal(μ=CAGR, σ=5%)`
  - `wacc ~ Normal(μ=baseWacc, σ=1%)`
  - `terminalGrowth ~ Uniform(1.5%, 3.5%)`
- Calcular IV para cada simulación
- Retornar: `p10`, `p25`, `p50`, `p75`, `p90` de la distribución resultante
- `MonteCarloResult` record con percentiles en `ValuationResult`
- Expuesto en la API como `monteCarlo` en `ValuationResponse`

**Archivos afectados:**
- `valuation-engine/.../MonteCarloAnalyzer.java` (nuevo)
- `valuation-engine/.../MonteCarloResult.java` (nuevo record)
- `valuation-engine/.../ValuationResult.java`
- `api-web/.../dto/ValuationResponse.java`
- `api-web/.../db/migration/V8__add_monte_carlo_to_valuation_result.sql` (nuevo)
- `valuation-engine/test/.../MonteCarloAnalyzerTest.java` (nuevo, 9 tests)

**Tareas:**
- [x] **TEST** `MonteCarloAnalyzer_returns1000Simulations`
- [x] **TEST** `MonteCarloAnalyzer_p50ApproximatesBaseScenario`
- [x] **TEST** `MonteCarloAnalyzer_p10AlwaysLowerThanP90`
- [x] Crear `MonteCarloResult` record con campos: `p10`, `p25`, `p50`, `p75`, `p90`, `simulationCount`
- [x] Crear `MonteCarloAnalyzer` (sin Spring, sin librerías externas — usar `java.util.Random`)
- [x] Agregar `monteCarloResult` a `ValuationResult`
- [x] Migración Flyway V8 para persistir percentiles Monte Carlo
- [x] Agregar `monteCarlo` a `ValuationResponse`

---

### Mejora 10 — Quality Score del negocio ✅

**Problema actual:** La herramienta solo produce un valor intrínseco, sin contexto sobre la calidad del negocio que lo genera.
**Impacto:** Un IV de $150 en una empresa con FCF decreciente y deuda creciente tiene mucho menos confiabilidad que el mismo IV en una empresa con FCF creciente y sin deuda.

**Solución:**
- Calcular un `qualityScore` de 0–100 basado en 5 dimensiones (0/10/20 puntos cada una):
  - FCF Growth, FCF Consistency, ROIC vs WACC, Leverage, Margin Trend
- `qualityScore` en `ValuationResult`, `ValuationResponse` y `ValuationResultEntity`
- Flyway V9 para columna `quality_score INTEGER` en `valuation_result`

**Archivos afectados:**
- `valuation-engine/.../QualityScoreCalculator.java` (nuevo)
- `valuation-engine/.../ValuationResult.java`
- `api-web/.../dto/ValuationResponse.java`
- `api-web/.../db/migration/V9__add_quality_score_to_valuation_result.sql` (nuevo)
- `valuation-engine/test/.../QualityScoreCalculatorTest.java` (nuevo, 8 tests)

**Tareas:**
- [x] **TEST** `QualityScoreCalculator_perfectScore_allPositiveSignals`
- [x] **TEST** `QualityScoreCalculator_zeroScore_allNegativeSignals`
- [x] **TEST** `QualityScoreCalculator_partialScore_mixedSignals`
- [x] **TEST** `QualityScoreCalculator_roicDimension_requiresWaccForComparison`
- [x] Crear `QualityScoreCalculator`
- [x] Agregar `qualityScore` (int 0–100) a `ValuationResult`
- [x] Agregar `qualityScore` a `ValuationResponse`

---

## Orden de implementación recomendado

```
Mejora 1 → Mejora 2 → Mejora 7   (corrigen las mayores fuentes de error en WACC y TV)
    ↓
Mejora 4 → Mejora 3               (refinan el costo de equity y net debt)
    ↓
Mejora 8                          (habilita parámetros sectoriales — prerequisito suave para Mejora 5)
    ↓
Mejora 5 → Mejora 6               (proyección de dos etapas con validación ROIC)
    ↓
Mejora 10 → Mejora 9              (quality score primero — más simple; Monte Carlo al final)
```

## Dependencias entre mejoras

| Mejora | Prerequisito |
|--------|-------------|
| Mejora 2 | Mejora 1 (necesita tasa impositiva real para calcular Kd correcto) |
| Mejora 5 | Mejora 8 (sector-aware facilita ROIC defaults por industria) |
| Mejora 6 | Mejora 5 (necesita ROIC calculado en la proyección) |
| Mejora 7 | Mejora 8 (exit multiples son sectoriales) |
| Mejora 9 | Mejoras 1-4 (Monte Carlo sobre un WACC mejorado es más significativo) |
| Mejora 10 | Mejora 6 (usa ROIC vs WACC como una dimensión del score) |

---

## Estado final — Validación Postman (2026-05-19)

Todas las mejoras implementadas y validadas en Postman desde rama `feature/dcf-quality-improvements`.

| Campo validado | Ticker | Resultado observado | Estado |
|----------------|--------|---------------------|--------|
| `breakdown.creditSpread` | AAPL | `0.006300` (spread AAA — ICR alto) | ✅ Correcto |
| `breakdown.creditSpread` | XOM | spread real según ICR de Energy | ✅ Correcto |
| `breakdown.effectiveTaxRate` | AAPL | `0.143457` (14.3% efectivo vs 21% default) | ✅ Correcto |
| `breakdown.sizeRiskPremium` | AAPL | `0.000000` (mega cap >$100B) | ✅ Correcto |
| `breakdown.roic` | AAPL | `0.823753` (82.4% — empresa de alta calidad) | ✅ Correcto |
| `breakdown.terminalValueExitMultiple` | AAPL | `1,604,043,547,418` (20x Technology, PV descontado) | ✅ Correcto |
| `breakdown.terminalValueExitMultiple` | XOM | `377,346,801,390` (8x Energy, PV descontado) | ✅ Correcto |
| `monteCarlo.p50` | AAPL | `~136` (aproxima IV base de $146) | ✅ Correcto |
| `qualityScore` | AAPL | `90/100` | ✅ Correcto |
| `terminalGrowthRate` | MSFT | `0.030` (3% sectorial Technology — parámetro del modelo, no dato de empresa) | ✅ Esperado |
| `wacc` | AAPL | `0.0924` (sin variación tras fix creditSpread — debtWeight ~2-3% de market cap) | ✅ Matemáticamente correcto |

**Problema identificado fuera de scope:** TSM reporta financieros en TWD. El engine no tiene conversión de moneda → `intrinsicValuePerShare` aparece en TWD produciendo valores irreales ($30,662). Pendiente como issue separado.

**Tests:** todos los módulos verdes al cierre de la rama.
