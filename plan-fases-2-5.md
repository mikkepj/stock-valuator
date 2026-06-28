# Stock Valuator — Plan de Ejecución Fases 2–5

> **Contexto:** Fase 1 completada. El endpoint `POST /api/v1/ingest/{ticker}` funciona y persiste datos en PostgreSQL. Este plan cubre la implementación del motor DCF, la API REST, el dashboard y el deploy.
>
> **Directivas obligatorias:** TDD siempre (test primero, luego implementación). Usar context7 para consultar documentación de librerías.

---

## Fase 2 — Valuation Engine

**Objetivo:** Módulo Java puro (`valuation-engine`) sin Spring que recibe `CompanyFinancials` y retorna `ValuationResult` con DCF completo y sensitivity matrix.

**Paquete:** `com.nuvixtech.stockvaluator.valuation`

### 2.1 Records de Input/Output

- [x] **TEST** `CompanyFinancialsTest` — constructor falla con datos nulos o listas vacías
- [x] Crear record `CompanyFinancials`: `ticker`, `historicalFcf` (List BigDecimal ASC), `totalDebt`, `cashAndEquivalents`, `totalEquity`, `interestExpense`, `incomeTaxExpense`, `beta`, `sharesOutstanding`, `ebitda`
- [x] **TEST** `ProjectedFcfTest` — verifica año y valor proyectado
- [x] Crear record `ProjectedFcf`: `year` (int), `projectedValue` (BigDecimal), `growthRateApplied` (BigDecimal)
- [x] **TEST** `ValuationResultTest` — verifica cálculo de `marginOfSafety` y asignación de `verdict`
- [x] Crear record `ValuationResult`: `ticker`, `intrinsicValuePerShare`, `marketPrice`, `marginOfSafety`, `verdict` (enum UNDERVALUED/FAIR_VALUE/OVERVALUED), `wacc`, `terminalGrowthRate`, `projectionYears`, `terminalValue`, `netDebt`, `projectedFcfs`, `sensitivityMatrix`, `breakdown`
- [x] Crear record `DcfParameters`: `riskFreeRate`, `marketRiskPremium`, `terminalGrowthRate`, `projectionYears`, `marketPrice`

### 2.2 FreeCashFlowProjector

- [x] **TEST** `FreeCashFlowProjectorTest` — caso nominal 5 años históricos → 10 proyectados con decay; FCF negativo en años iniciales; un solo año histórico
- [x] Implementar `FreeCashFlowProjector.project(historicalFcf, terminalGrowthRate, projectionYears)`:
  - CAGR calculado sobre todos los históricos como tasa inicial
  - Decay lineal desde CAGR hasta `terminalGrowthRate` a lo largo de los años de proyección
  - Retorna `List<ProjectedFcf>` con rate aplicado en cada año

### 2.3 WaccCalculator

- [x] **TEST** `WaccCalculatorTest` — empresa con deuda y equity conocidos; empresa sin deuda (D=0); tax shield reduce correctamente el costo de deuda
- [x] Implementar `WaccCalculator.calculate(financials, riskFreeRate, marketRiskPremium)`:
  - Cost of Equity: CAPM `= Rf + Beta × (Rm - Rf)`
  - Cost of Debt: `= interestExpense / totalDebt × (1 − taxRate)`
  - `taxRate = incomeTaxExpense / (netIncome + incomeTaxExpense)`
  - WACC ponderado por `E/(E+D)` y `D/(E+D)`

### 2.4 TerminalValueCalculator

- [x] **TEST** `TerminalValueCalculatorTest` — Gordon Growth con WACC > g; WACC == g lanza excepción; PV correctamente descontado al año N
- [x] Implementar `TerminalValueCalculator.calculate(lastProjectedFcf, wacc, terminalGrowthRate, projectionYears)`:
  - Gordon Growth: `FCF_N × (1 + g) / (WACC - g)` descontado a presente

### 2.5 DcfCalculator (orquestador)

- [x] **TEST** `DcfCalculatorTest` — flujo completo con datos de AAPL 2023 ±15% del valor conocido; net debt resta correctamente; división por `sharesOutstanding`
- [x] Implementar `DcfCalculator.calculate(financials, params)` retorna `ValuationResult`:
  - Suma PV de FCFs proyectados + PV del terminal value
  - Net debt = `totalDebt - cashAndEquivalents`
  - Intrinsic value per share = `(sumPVFcf + TV - netDebt) / sharesOutstanding`
  - Verdict: UNDERVALUED si margen > 15%, OVERVALUED si < -15%, FAIR_VALUE en el resto

### 2.6 SensitivityAnalyzer

- [x] **TEST** `SensitivityAnalyzerTest` — matriz 5×5 (growth ±2% en pasos de 1%, WACC ±1% en pasos de 0.5%); celda central == resultado base del DcfCalculator
- [x] Implementar `SensitivityAnalyzer.analyze(financials, baseParams, calculator)` retorna `Map<String, Map<String, BigDecimal>>`
  - Eje X: WACC en `{-1%, -0.5%, 0, +0.5%, +1%}`
  - Eje Y: terminal growth en `{-2%, -1%, 0, +1%, +2%}`

---

## Fase 3 — API REST

**Objetivo:** Endpoints REST en `api-web` que orquesten ingestion + engine, con caché y manejo de errores uniforme.

**Paquete nuevo:** `com.nuvixtech.stockvaluator.api.valuation`

### 3.1 Entidad y Repositorio ValuationResult

- [x] Crear entidad JPA `ValuationResultEntity` mapeando tabla `valuation_result` existente; campos `sensitivityMatrix` y `breakdown` como `@JdbcTypeCode(JSON)`
- [x] Crear `ValuationResultRepository` con método `findFirstByCompanyTickerOrderByCalculatedAtDesc(String ticker)`

### 3.2 Response DTOs y Mapper

- [x] Crear record `ValuationResponse`: todos los campos de `ValuationResult` + `companyName`, `sector`, `lastUpdated`
- [x] Crear record `WatchlistItemResponse`: `ticker`, `companyName`, `currentPrice`, `intrinsicValue`, `marginOfSafety`, `verdict`
- [x] Crear `ValuationMapper` (sin librerías externas): `ValuationResult → ValuationResultEntity`, `ValuationResultEntity → ValuationResponse`

### 3.3 Servicio de Valuación

- [x] **TEST** `ValuationServiceTest` con Mockito — cache hit no llama al engine; `forceCalculate` invalida cache; ticker sin datos lanza `TickerNotFoundException`
- [x] Implementar `ValuationService`:
  - `getValuation(ticker)` con `@Cacheable("valuations")`
  - `forceCalculate(ticker)` con `@CacheEvict` — ingesta si necesario, corre engine, persiste
  - Método privado `buildCompanyFinancials(ticker)` que lee de `FinancialStatementRepository` y `MarketDataRepository`
  - `DcfParameters` defaults via `@Value`: `riskFreeRate=0.045`, `marketRiskPremium=0.055`, `terminalGrowthRate=0.025`, `projectionYears=10`

### 3.4 Endpoints de Valuación

- [x] **TEST** `ValuationControllerTest` con MockMvc — GET 200 con body; POST 200 con cálculo fresco; ticker inexistente → 404
- [x] Implementar `ValuationController`:
  - `GET /api/v1/valuations/{ticker}` → `ValuationResponse` cacheado
  - `POST /api/v1/valuations/{ticker}/calculate` → fuerza recálculo

### 3.5 Endpoints Watchlist

- [x] Crear entidad JPA `WatchlistEntry` mapeando tabla `watchlist` existente
- [x] Crear `WatchlistRepository` con `findByCompanyTicker(String ticker)`
- [x] **TEST** `WatchlistControllerTest` con MockMvc — GET lista, POST agrega, DELETE 404 si no existe
- [x] Implementar `WatchlistController`:
  - `GET /api/v1/watchlist` → `List<WatchlistItemResponse>`
  - `POST /api/v1/watchlist/{ticker}` → agrega y retorna 201
  - `DELETE /api/v1/watchlist/{ticker}` → 204 o 404

### 3.6 Exception Handling y Caché

- [x] Implementar `GlobalExceptionHandler` (`@ControllerAdvice`): `TickerNotFoundException → 404`, `IngestionException → 422`, `Exception → 500`; body uniforme `ErrorResponse` record con `timestamp`, `status`, `error`, `path`
- [x] Crear `CacheConfig`: cache `valuations` con TTL 24h, `watchlist` con TTL 5min

---

## Fase 3.6 — Calibración DCF y Escenarios ✅

**Objetivo:** Mejorar la precisión del motor DCF ajustando parámetros de mercado y añadiendo tres escenarios de valuación (Base, Optimista, Pesimista). Soporte para estimaciones de analistas y cap de CAGR histórico.

**Rama:** `feature/dcf-scenarios-calibration`

### 3.6.1 WACC con market cap y ERP implícito

- [x] **TEST** `WaccCalculatorTest` — MSFT con market cap $2.78T produce WACC ~9.2%–9.5%
- [x] `CompanyFinancials` agrega campo `marketCap` — `equityValue()` retorna market cap si > 0, fallback a totalEquity
- [x] `WaccCalculator` usa `financials.equityValue()` para ponderar (market cap >> equity en libros para large caps)
- [x] `marketRiskPremium` cambiado de `0.055` → `0.045` (ERP implícito de mercado) en `application.yml`

### 3.6.2 Tres escenarios de valuación (Base, Optimista, Pesimista)

- [x] **TEST** `ScenarioAnalyzerTest` — 3 escenarios; nombres correctos; Optimista > Base > Pesimista; todos IV > 0; Base == DcfCalculator; null checks
- [x] Crear record `ScenarioResult`: `scenarioName`, `intrinsicValuePerShare`, `marginOfSafety`, `verdict`, `initialGrowthRate`, `terminalGrowthRate`, `wacc`
- [x] Crear clase `ScenarioAnalyzer` en `valuation-engine` (sin Spring):
  - **Base:** DcfCalculator directo
  - **Optimista:** CAGR × 1.30, cap 25%, WACC base
  - **Pesimista:** CAGR × 0.75, riskFreeRate + 0.5%
  - Si hay `analystFcfEstimates`, el CAGR se calcula sobre ellas

### 3.6.3 Integración en ValuationService y API

- [x] Crear `ScenarioResultDto` record en `api-web`
- [x] Agregar `List<ScenarioResultDto> scenarios` a `ValuationResponse`
- [x] Actualizar `ValuationService.calculate()` — llama `ScenarioAnalyzer.analyze()`, pasa scenarios al mapper
- [x] Actualizar `ValuationMapper` — `toEntity(result, scenarios, company)`, deserializa scenarios desde jsonb
- [x] Migración `V4__add_scenarios_to_valuation_result.sql` — columna `scenarios jsonb`
- [x] `ValuationEngineConfig` registra bean `ScenarioAnalyzer`

### 3.6.4 Estimaciones de analistas (FCF manual desde Koyfin)

- [x] Entidad `FcfEstimate` + `FcfEstimateRepository`
- [x] Migración `V5__create_fcf_estimate.sql`
- [x] `FcfEstimateService.save()` — reemplaza estimaciones existentes, años fiscales desde currentYear+1
- [x] `POST /api/v1/companies/{ticker}/fcf-estimates` con validación `@NotEmpty` → 204
- [x] `CompanyFinancials` agrega campo `analystFcfEstimates` (separado de `historicalFcf`)
- [x] `FreeCashFlowProjector.projectWithEstimates()` — usa estimates para años 1-N sin recalcular CAGR; proyecta años N+1..10 con decay desde tasa implícita del último año estimado
- [x] `ValuationService.buildCompanyFinancials()` pasa `historicalFcf` e `analystFcfEstimates` como campos separados

### 3.6.5 Cap de CAGR histórico

- [x] **TEST** `FreeCashFlowProjectorTest` — CAGR extremo cappado al 30%; CAGR moderado no afectado
- [x] `FreeCashFlowProjector.calculateInitialRate()` — cap de 30% para evitar que históricos excepcionales inflen el IV irrealmente

---

## Fase 4 — Dashboard

**Objetivo:** SPA React + TypeScript servida por Spring Boot con watchlist visual y detalle de ticker.

### 4.1 Setup Frontend

- [ ] Crear `frontend/` con Vite + React + TypeScript
- [ ] Instalar dependencias: `recharts`, `axios`, `react-router-dom`
- [ ] Configurar proxy en `vite.config.ts`: `/api` → `http://localhost:8080`
- [ ] Crear tipos TypeScript espejando `ValuationResponse` y `WatchlistItemResponse`

### 4.2 Pantalla Watchlist

- [ ] Componente `WatchlistPage`: tabla con ticker/nombre/precio/valor/margen/veredicto
- [ ] Color coding: verde (UNDERVALUED), amarillo (FAIR_VALUE), rojo (OVERVALUED)
- [ ] Componente `AddTickerModal`: llama `POST /api/v1/watchlist/{ticker}` y refresca lista
- [ ] Botón "Recalcular" por fila que llama `POST /api/v1/valuations/{ticker}/calculate`

### 4.3 Pantalla Detalle de Ticker

- [ ] Componente `TickerDetailPage` con route `/ticker/:ticker`
- [ ] Header: nombre, sector, precio vs valor intrínseco, badge de veredicto
- [ ] `FcfBarChart` con Recharts: barras históricas (sólido) + proyectadas (transparente)
- [ ] `DcfBreakdownTable`: WACC, terminal growth, terminal value, net debt, shares outstanding
- [ ] `SensitivityHeatmap`: tabla con gradiente de color, eje X = WACC, eje Y = growth rate

### 4.4 Integración con Spring Boot

- [ ] Configurar script de build en `frontend/package.json` que copia `dist/` a `api-web/src/main/resources/static/`
- [ ] Agregar ejecución del build frontend como fase del lifecycle Maven en `api-web/pom.xml` (plugin `exec-maven-plugin`)
- [ ] Configurar Spring Boot para servir `index.html` en rutas no-API (SPA fallback en `WebMvcConfigurer`)

---

## Fase 5 — Deploy y Polish

**Objetivo:** Aplicación observable, segura y desplegable con CI/CD automatizado.

### 5.1 Archivos de Proyecto

- [x] Crear `CLAUDE.md` en raíz: arquitectura, convenciones, directivas TDD y context7, comandos útiles
- [ ] Actualizar `README.md`: badges de CI, arquitectura ASCII, instrucciones de setup, ejemplos curl, variables de entorno

### 5.2 Observabilidad

- [ ] Agregar `micrometer-registry-prometheus` en `api-web/pom.xml`
- [ ] Habilitar `/actuator/prometheus` en `application.yml`
- [ ] Agregar métrica custom `valuation.calculations.total` (Counter) en `ValuationService`
- [ ] Configurar Logback JSON appender para perfil `prod`

### 5.3 Seguridad Básica

- [ ] Agregar `spring-boot-starter-security` a `api-web/pom.xml`
- [ ] Implementar `SecurityConfig`: `/actuator/**` requiere rol ADMIN; `/api/v1/**` público con rate limiting; CORS configurado
- [ ] Agregar config `resilience4j.ratelimiter.instances.api` en `application.yml`: 100 req/min

### 5.4 GitHub Actions CI/CD

- [ ] Crear `.github/workflows/ci.yml`: trigger en push a `main`/`develop`; jobs `test` (mvn verify), `build` (mvn package -DskipTests)
- [ ] Crear `.github/workflows/deploy.yml`: trigger manual + push a `main`; deploy a Railway o Render vía CLI o deploy hook
- [ ] Agregar secrets en GitHub: `FMP_API_KEY`, `DB_PASSWORD`, `RAILWAY_TOKEN`

---

## Archivos clave

| Archivo | Propósito |
|---------|-----------|
| `valuation-engine/src/main/java/com/nuvixtech/stockvaluator/valuation/` | Implementar todos los componentes DCF |
| `api-web/src/main/resources/db/migration/V1__initial_schema.sql` | Tabla `valuation_result` ya creada |
| `data-ingestion/src/main/java/.../service/FinancialDataService.java` | Fuente de datos para el engine |
| `data-ingestion/src/main/java/.../entity/FinancialStatement.java` | Entidad con campos FCF, deuda, equity |
| `api-web/src/main/resources/application.yml` | Config de caché, parámetros DCF por defecto |
