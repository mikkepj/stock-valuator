# LEARNINGS.md — Decisiones técnicas y lecciones aprendidas

> Registro de decisiones no obvias, problemas reales que enfrentamos y quirks del stack descubiertos durante el desarrollo. No duplica lo que está en el código.

---

## Decisiones de arquitectura

### valuation-engine sin Spring
**Contexto:** Al diseñar el motor DCF, teníamos la opción de usar `@Service` y DI de Spring como el resto de la app.
**Decisión:** Cero dependencias de Spring en `valuation-engine`.
**Por qué:** Tests unitarios puros sin levantar contexto de Spring (mucho más rápidos). El engine es reutilizable en cualquier contexto. Fuerza a que las dependencias sean explícitas en el constructor.
**Consecuencia:** Los beans se registran manualmente en `ValuationEngineConfig` en `api-web`. Ese archivo es el único punto de instanciación del engine.

### Records para inputs/outputs del engine
**Decisión:** `CompanyFinancials`, `DcfParameters`, `ValuationResult`, `ProjectedFcf`, `ScenarioResult`, `MonteCarloResult` son Java records.
**Por qué:** Inmutabilidad garantizada. El engine nunca muta sus inputs. Errores de concurrencia imposibles en Monte Carlo (1000 iteraciones sobre los mismos datos).
**Quirk:** Al añadir un campo nuevo al record canónico, todos los constructores de test que usaban el constructor de N args se rompen con `NoSuchMethodError`. Solución: usar el constructor de conveniencia de 13 args para tests, o actualizar todos los call sites.

### JSONB en PostgreSQL para campos flexibles
**Decisión:** `sensitivity_matrix`, `breakdown`, `scenarios`, `monte_carlo` almacenados como JSONB.
**Por qué:** Evita normalizar estructuras que cambian con cada mejora del engine. Un nuevo campo en `breakdown` no requiere migración de schema.
**Quirk Hibernate 6:** Requiere `@JdbcTypeCode(SqlTypes.JSON)` + `@Column(columnDefinition = "jsonb")`. Sin la anotación, Hibernate 6 no serializa correctamente el tipo.
**H2 en tests:** H2 no soporta JSONB nativo. En `application-test.yml` se mapea a `TEXT` y funciona.

---

## Problemas resueltos

### Bug: creditSpread = 0.0864 (CCC) para AAPL
**Síntoma:** `breakdown.creditSpread` mostraba 8.64% para Apple, que tiene >30x cobertura de intereses.
**Causa raíz:** `WaccCalculator.calculateCreditSpread()` tenía una sola condición `if (interestExpense <= 0 || ebitda <= 0)` que retornaba el spread CCC máximo. Cuando `interestExpense = 0` (FMP a veces devuelve 0), caía en el peor spread en vez del mejor.
**Fix:** Separar en dos condiciones independientes:
- `interestExpense <= 0` → spread AAA (0.0063) — sin deuda o dato no disponible
- `ebitda <= 0` → spread CCC (0.0864) — empresa en pérdidas operativas
**Lección:** Los casos "dato no disponible (= 0)" y "empresa en problemas (= 0)" deben tener semánticas distintas aunque el valor sea el mismo.

### Bug: growthExceedsRoic siempre = 0 en tests
**Síntoma:** El test que debía verificar la advertencia cuando `avgProjectedGrowth > ROIC` siempre retornaba 0.
**Causa:** El test usaba una empresa con ROIC = 20% y CAGR capeado al 30%. Con decay lineal, `avgProjectedGrowth ≈ 16% < ROIC 20%`, nunca disparando la advertencia.
**Fix:** Usar empresa con `investedCapital` enorme (~1495B) y `ebitda` pequeño (~3B) → ROIC ≈ 0.15%. Cualquier tasa proyectada supera ese ROIC.
**Lección:** Al testear un warning condicional, verificar primero manualmente que los datos de entrada realmente llevan al código al branch esperado.

### Error: NoSuchMethodError al añadir campo a ValuationResult
**Síntoma:** Tests de `ValuationResultTest` y `DcfCalculatorTest` explotaban con `NoSuchMethodError` al añadir `monteCarloResult` y `qualityScore`.
**Causa:** Los helpers `buildResult()` en tests usaban el constructor de N args directamente.
**Fix:** Añadir los nuevos argumentos en todos los call sites (`null` para `monteCarloResult`, `0` para `qualityScore`).
**Lección:** Antes de extender un record, hacer `grep -r "new ValuationResult"` para encontrar todos los constructores explícitos.

### interestExpense = 0 desde FMP API
**Síntoma:** Para AAPL, `interestExpense` llegaba como 0 aunque la empresa tiene deuda.
**Causa:** FMP reporta `interestExpense` en el income statement a veces como 0 o negativo dependiendo del año fiscal.
**Solución:** En `ValuationService.buildCompanyFinancials()`: si INCOME devuelve 0 → fallback a CASHFLOW statement.

---

## Quirks del stack

### Maven multi-módulo: orden de compilación
`mvn test -pl valuation-engine` funciona standalone. `mvn test -pl api-web` requiere que `valuation-engine` y `data-ingestion` estén instalados en el repositorio local. Usar `mvn test -pl api-web -am` para compilar las dependencias también.

### Spring Boot RestClient vs RestTemplate
Spring Boot 3.2+ introduce `RestClient`. `FmpApiClient` usa `RestClient`. No usar `RestTemplate` en código nuevo. Para listas tipadas usar `ParameterizedTypeReference<List<T>>` — sin eso, Jackson deserializa como `LinkedHashMap`.

### Caffeine cache + @CacheEvict
`@CacheEvict` en `calculate()` limpia la entrada antes de recalcular. El orden: primero evict, luego el método produce el nuevo valor que queda cacheado en la siguiente llamada a `getLatestValuation()`.

### Flyway + IF NOT EXISTS
Las migraciones usan `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` para ser idempotentes. Útil en entornos de staging con columnas añadidas manualmente.

### Monte Carlo: perturbación de WACC sin recálculo completo
`MonteCarloAnalyzer` no recalcula el WACC completo por iteración (sería 1000 × cálculo completo). En cambio:
1. Escala el último FCF histórico para reflejar el `growthRate` perturbado
2. Ajusta `riskFreeRate` por el 50% del delta WACC para aproximar el WACC objetivo
Produce distribuciones estadísticamente razonables con mucho menor costo computacional.

### BigDecimal.pow() con exponentes fraccionales
Para potencias fraccionales (ej. CAGR = `(last/first)^(1/n)`) se debe hacer cast a `double`: `Math.pow(ratio.doubleValue(), 1.0/periods)`. La pérdida de precisión en esa operación específica es aceptable y es el patrón usado en todo el engine.

### TSM y acciones ADR: conversión de moneda en la ingesta
**Síntoma original:** `intrinsicValuePerShare` para TSM resultó ~30.662 USD cuando el precio de mercado era ~392 USD (error de ~7.700%).
**Causa raíz:** FMP devuelve estados financieros en TWD (moneda funcional), pero el engine asume USD. Adicionalmente, FMP devuelve `currency="USD"` en el `/profile` para ADRs (moneda de cotización NYSE) — esto es **incorrecto para usar como moneda de conversión**. La fuente correcta es `reportedCurrency` dentro del income statement.
**Solución implementada (`feature/currency-conversion`):**
1. `FmpIncomeStatement` añade campo `reportedCurrency` (campo 12).
2. `FinancialDataService.ingest()` lee `reportedCurrency` del primer income statement en vez de `company.getCurrency()`.
3. `CurrencyConversionService` obtiene la tasa de `ExchangeRateApiClient` (`open.er-api.com`, gratuito).
4. `FinancialDataMapper` convierte todos los campos monetarios multiplicando por `fxRateToUsd` antes de guardar en BD.
**Campos NO convertidos:** `sharesOutstanding` (unidades), `beta` (ratio), `marketCap` (FMP ya lo reporta en USD para ADRs).
**Por qué no FMP para FX:** FMP requiere suscripción premium para cotizaciones forex (HTTP 402). Se usa `open.er-api.com` — API pública gratuita, sin API key, endpoint: `GET /v6/latest/{currency}`.
**Resultado post-fix:** TSM IV = $465.46 vs precio $407.15 → `FAIR_VALUE` (+14.3%). Coherente con el mercado.
**Lección clave:** `currency` del `/profile` de FMP = moneda de cotización (USD para ADRs en NYSE). `reportedCurrency` del income statement = moneda funcional real. Siempre usar `reportedCurrency`.

### ScenarioAnalyzer: Optimista puede dar IV menor que Base
Observado en AAPL: el escenario Optimista (CAGR × 1.30) puede producir un IV menor que el Base cuando el engine usa el path `projectWithRoic` internamente. El escalado de FCFs para escenarios opera sobre `historicalFcf`, pero el engine puede tomar un camino distinto con los datos escalados. Estado: conocido, no bloqueante.

### SectorDefaults: Technology usa MRP = 4.5%, no 5.5%
Los defaults globales (`application.yml`) usan `marketRiskPremium = 0.045`. Los defaults sectoriales de `SectorDefaults` también usan 4.5% para la mayoría de sectores (Damodaran 2024). El único sector con 5.0% es Financials. No confundir con el 5.5% que aparecía en versiones anteriores del CLAUDE.md.

---

## Tooling y flujo de trabajo Claude Code

### Migración de `.claude/commands/` a `.claude/skills/`
**Contexto:** Claude Code introdujo el sistema de skills como reemplazo del sistema de commands.
**Estructura nueva:**
```
.claude/skills/<nombre>/SKILL.md
```
**Frontmatter YAML requerido en cada SKILL.md:**
```yaml
---
name: <nombre-del-skill>
description: <una línea usada para decidir relevancia en el autocompletado>
argument-hint: "[argumento opcional visible en el prompt]"
---
```
**Diferencia clave con commands:** El sistema de skills carga el `SKILL.md` como contexto del skill antes de invocarlo. El campo `description` es lo que aparece en el listado de skills disponibles y lo que el modelo usa para decidir cuándo invocar el skill automáticamente.
**Skills migrados en este proyecto:** `fix`, `new-feature`, `review` — los tres preservan el contenido original de sus respectivos `commands/*.md`.

---

## Phase 0 — Project Setup

### Maven multi-module with Spring Boot 3.3

- The `spring-boot-maven-plugin` goes ONLY in the runnable module (`api-web`), not in library modules.
- Use `<dependencyManagement>` in the parent POM for internal module versions — avoids hardcoding versions in child POMs.
- The `valuation-engine` module is intentionally framework-free. This forces clean architecture: the core business logic has zero coupling to Spring.

### Java 21 records as configuration

- `@ConfigurationProperties` works natively with records in Spring Boot 3.x. No setters needed.
- Example: `FmpApiProperties` is a record that maps directly from `application.yml`.

### RestClient (new in Spring Boot 3.2)

- Replaces `RestTemplate` as the modern synchronous HTTP client.
- Fluent API, better error handling, and plays well with `@Bean` configuration.
- Unlike `WebClient`, it's synchronous — simpler mental model for our use case.

### Flyway

- Migrations live in `src/main/resources/db/migration/` with naming `V{n}__{description}.sql`.
- Spring Boot auto-detects and runs them on startup.
- `ddl-auto: validate` in JPA ensures entities match the Flyway-managed schema.

---

## Phase 1 — Data Ingestion

*(To be filled)*

---

## Phase 2 — Valuation Engine

*(To be filled)*
