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
- [ ] **TEST** `WaccCalculator_effectiveTaxRate_usesRealRateWhenAvailable`
- [ ] **TEST** `WaccCalculator_effectiveTaxRate_fallsBackTo21WhenOutOfRange`
- [ ] Implementar cálculo de tasa impositiva efectiva en `WaccCalculator`
- [ ] Agregar `effectiveTaxRate` al breakdown en `DcfCalculator`

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
- [ ] **TEST** `WaccCalculator_debtCost_usesSpreadForHighCoverage` (ICR>8.5 → spread 0.63%)
- [ ] **TEST** `WaccCalculator_debtCost_usesSpreadForLowCoverage` (ICR<0.8 → spread 8.64%)
- [ ] **TEST** `WaccCalculator_debtCost_fallbackWhenNoInterestExpense`
- [ ] Implementar tabla de spreads Damodaran en `WaccCalculator`
- [ ] Agregar `debtSpread` e `impliedCreditRating` al breakdown

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
- [ ] **TEST** `DcfCalculator_netDebt_includesLeasesAndPensions`
- [ ] **TEST** `DcfCalculator_netDebt_withZeroOptionalFields_matchesBasicFormula`
- [ ] Agregar campos opcionales a `CompanyFinancials`
- [ ] Actualizar `DcfCalculator` para usar `adjustedNetDebt`
- [ ] Migración Flyway `V7__add_lease_pension_to_financial_statement.sql`
- [ ] Actualizar `buildCompanyFinancials` para leer los nuevos campos

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
- [ ] **TEST** `WaccCalculator_sizeRiskPremium_zeroForMegaCap`
- [ ] **TEST** `WaccCalculator_sizeRiskPremium_appliedForSmallCap`
- [ ] Implementar tabla de primas de tamaño en `WaccCalculator`
- [ ] Agregar `sizeRiskPremium` al breakdown

---

## Nivel 2 — Mejora de precisión del análisis

### Mejora 5 — Proyección FCF en dos etapas con ROIC/Reinvestment Rate

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
- [ ] **TEST** `FreeCashFlowProjector_twoStage_phase1UsesRoicGrowth`
- [ ] **TEST** `FreeCashFlowProjector_twoStage_phase2DecaysToTerminal`
- [ ] **TEST** `FreeCashFlowProjector_twoStage_fallbackWhenRoicDataMissing`
- [ ] Agregar `capitalExpenditure` a `CompanyFinancials`
- [ ] Implementar proyección de dos etapas en `FreeCashFlowProjector`

---

### Mejora 6 — Validador ROIC vs WACC (consistencia del crecimiento)

**Problema actual:** El modelo proyecta crecimiento sin verificar si la empresa tiene el retorno sobre el capital para sostenerlo.
**Impacto:** Una empresa con ROIC del 5% no puede crecer al 15% de forma sostenible sin destruir valor.

**Solución:**
- Calcular `ROIC = NOPAT / investedCapital` con los datos disponibles
- Si `projectedGrowthRate > ROIC` → agregar advertencia `"growthExceedsRoic": true` en breakdown
- Calcular `maxSustainableGrowth = ROIC × reinvestmentRate` y exponerlo en breakdown
- No bloquear el cálculo, solo agregar señales de alerta que el FE puede mostrar

**Archivos afectados:**
- `valuation-engine/.../DcfCalculator.java`
- `valuation-engine/test/.../DcfCalculatorTest.java`

**Tareas:**
- [ ] **TEST** `DcfCalculator_roicWarning_setWhenGrowthExceedsRoic`
- [ ] **TEST** `DcfCalculator_roicWarning_notSetWhenGrowthBelowRoic`
- [ ] Implementar cálculo ROIC y advertencias en `DcfCalculator`
- [ ] Agregar `roic`, `maxSustainableGrowth`, `growthExceedsRoic` al breakdown

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
- [ ] **TEST** `TerminalValueCalculator_exitMultiple_technologySector`
- [ ] **TEST** `TerminalValueCalculator_exitMultiple_defaultSectorWhenUnknown`
- [ ] **TEST** `TerminalValueCalculator_exitMultiple_isLowerThanGordonForHighGrowthAssumptions`
- [ ] Agregar `sector` a `CompanyFinancials`
- [ ] Implementar `calculateExitMultiple()` en `TerminalValueCalculator`
- [ ] Exponer ambos métodos en breakdown

---

## Nivel 3 — Diferenciadores respecto a herramientas básicas

### Mejora 8 — Parámetros por sector (Sector-Aware DCF)

**Problema actual:** Todos los tickers usan los mismos defaults de WACC, terminal growth y MRP.
**Impacto:** Una empresa de semiconductores y una de utilities tienen perfiles de riesgo radicalmente distintos.

**Solución:**
- Crear clase `SectorDefaults` en `valuation-engine` con tabla de defaults por sector:
  ```
  Sector             | Beta Damodaran | ERP   | Terminal Growth | Exit Multiple
  Technology         | 1.48           | 4.5%  | 3.0%            | 20x
  Semiconductors     | 1.52           | 4.5%  | 3.0%            | 18x
  Software (SaaS)    | 1.10           | 4.5%  | 3.5%            | 25x
  Healthcare         | 0.89           | 4.5%  | 2.5%            | 15x
  Consumer Defensive | 0.57           | 4.5%  | 2.0%            | 14x
  Energy             | 1.05           | 4.5%  | 1.5%            | 8x
  Financials         | 0.47           | 5.0%  | 2.5%            | 12x
  Industrials        | 0.98           | 4.5%  | 2.5%            | 12x
  Utilities          | 0.37           | 4.5%  | 2.0%            | 10x
  ```
- `DcfParameters.forSector(String sector)` retorna parámetros ajustados para ese sector
- Si `CompanyFinancials.sector` está disponible, `ValuationService` usa parámetros sectoriales como punto de partida
- El `betaOverride` del usuario sigue prevaleciendo sobre el beta sectorial

**Archivos afectados:**
- `valuation-engine/.../SectorDefaults.java` (nuevo)
- `valuation-engine/.../DcfParameters.java`
- `api-web/.../service/ValuationService.java`
- `valuation-engine/test/.../SectorDefaultsTest.java` (nuevo)

**Tareas:**
- [ ] **TEST** `SectorDefaults_technologySector_returnsSectorBeta`
- [ ] **TEST** `SectorDefaults_unknownSector_returnsGlobalDefaults`
- [ ] **TEST** `DcfParameters_forSector_usesCorrectTerminalGrowth`
- [ ] Crear clase `SectorDefaults`
- [ ] Agregar factory method `DcfParameters.forSector()`
- [ ] Actualizar `ValuationService` para usar sector si disponible

---

### Mejora 9 — Simulación Monte Carlo sobre FCF y WACC

**Problema actual:** Solo 3 escenarios con multiplicadores arbitrarios (1.30x / 0.75x).
**Impacto:** No captura la distribución real de posibles valores. Oculta la incertidumbre real del modelo.

**Solución:**
- Generar N=1000 simulaciones variando simultáneamente:
  - `growthRate ~ Normal(μ=CAGR, σ=stdDevHistoricalFcf)`
  - `wacc ~ Normal(μ=baseWacc, σ=0.01)` (±1% std)
  - `terminalGrowth ~ Uniform(1.5%, 3.5%)`
- Calcular IV para cada simulación
- Retornar: `p10`, `p25`, `p50`, `p75`, `p90` de la distribución resultante
- Agregar `MonteCarloResult` record con percentiles al `ValuationResult`
- Exponer en la API como campo adicional en `ValuationResponse`

**Archivos afectados:**
- `valuation-engine/.../MonteCarloAnalyzer.java` (nuevo)
- `valuation-engine/.../MonteCarloResult.java` (nuevo record)
- `valuation-engine/.../ValuationResult.java`
- `api-web/.../dto/ValuationResponse.java`
- `valuation-engine/test/.../MonteCarloAnalyzerTest.java` (nuevo)

**Tareas:**
- [ ] **TEST** `MonteCarloAnalyzer_returns1000Simulations`
- [ ] **TEST** `MonteCarloAnalyzer_p50ApproximatesBaseScenario`
- [ ] **TEST** `MonteCarloAnalyzer_p10AlwaysLowerThanP90`
- [ ] Crear `MonteCarloResult` record con campos: `p10`, `p25`, `p50`, `p75`, `p90`, `simulationCount`
- [ ] Crear `MonteCarloAnalyzer` (sin Spring, sin librerías externas — usar `java.util.Random`)
- [ ] Agregar `monteCarloResult` a `ValuationResult`
- [ ] Migración Flyway para persistir percentiles Monte Carlo
- [ ] Agregar `monteCarloResult` a `ValuationResponse`

---

### Mejora 10 — Quality Score del negocio

**Problema actual:** La herramienta solo produce un valor intrínseco, sin contexto sobre la calidad del negocio que lo genera.
**Impacto:** Un IV de $150 en una empresa con FCF decreciente y deuda creciente tiene mucho menos confiabilidad que el mismo IV en una empresa con FCF creciente y sin deuda.

**Solución:**
- Calcular un `qualityScore` de 0–100 basado en 5 dimensiones (20 puntos cada una):

  | Dimensión | Señal positiva (20 pts) | Señal negativa (0 pts) |
  |-----------|------------------------|------------------------|
  | **FCF Growth** | CAGR histórico > 10% | CAGR < 0% (FCF decreciente) |
  | **FCF Consistency** | Todos los años FCF > 0 | Algún año FCF negativo |
  | **ROIC vs WACC** | ROIC > WACC (value creation) | ROIC < WACC (value destruction) |
  | **Leverage** | netDebt / ebitda < 2x | netDebt / ebitda > 4x |
  | **Margin Trend** | FCF margin estable o creciente | FCF margin cayendo >3pp |

- `qualityScore` se agrega a `ValuationResult` y `ValuationResponse`
- El FE puede mostrar un badge de confiabilidad asociado al IV

**Archivos afectados:**
- `valuation-engine/.../QualityScoreCalculator.java` (nuevo)
- `valuation-engine/.../ValuationResult.java`
- `api-web/.../dto/ValuationResponse.java`
- `valuation-engine/test/.../QualityScoreCalculatorTest.java` (nuevo)

**Tareas:**
- [ ] **TEST** `QualityScoreCalculator_perfectScore_allPositiveSignals`
- [ ] **TEST** `QualityScoreCalculator_zeroScore_allNegativeSignals`
- [ ] **TEST** `QualityScoreCalculator_partialScore_mixedSignals`
- [ ] **TEST** `QualityScoreCalculator_roicDimension_requiresWaccForComparison`
- [ ] Crear `QualityScoreCalculator`
- [ ] Agregar `qualityScore` (int 0–100) a `ValuationResult`
- [ ] Agregar `qualityScore` a `ValuationResponse`

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
