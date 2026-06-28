# CLAUDE.md — Stock Valuator

---

## WORKFLOW OBLIGATORIO
Para cualquier feature o fix no trivial:
- Usar Shift+Tab (plan mode) antes de ejecutar
- Crear plan file en docs/plans/ antes de escribir código
- No ejecutar sin aprobación explícita del plan

---

## ARQUITECTURA

Maven multi-módulo con 3 módulos y dependencia unidireccional estricta:

```
stock-valuator/
├── valuation-engine/   # Java puro — sin Spring, sin JPA, sin reflexión
├── data-ingestion/     # Spring + JPA + FMP API client + Resilience4j
└── api-web/            # Spring Boot app: controllers, Flyway, caché, Swagger
```

**Paquete raíz:** `com.nuvixtech.stockvaluator`

**Dependencias entre módulos:**
- `api-web` → `data-ingestion` + `valuation-engine`
- `data-ingestion` → `valuation-engine`
- `valuation-engine` → ninguno (zero dependencias internas)

**Decisiones de diseño ya tomadas:**

| Decisión | Razón |
|----------|-------|
| `valuation-engine` sin Spring | Permite tests unitarios puros sin contexto de Spring; el engine es agnóstico de framework |
| Records para inputs/outputs del engine | `CompanyFinancials`, `DcfParameters`, `ValuationResult`, `ProjectedFcf`, `ScenarioResult`, `MonteCarloResult` son inmutables por diseño |
| Registrar beans del engine en `ValuationEngineConfig` | Único punto de instanciación; evita `@Component` en el engine |
| `BigDecimal` exclusivo para cálculos financieros | Precisión decimal sin errores de punto flotante; `MathContext.DECIMAL128` en todas las operaciones |
| Flyway para migraciones | Control de versión del esquema; nunca usar `ddl-auto: update` en producción |
| JSONB en PostgreSQL para sensitivity_matrix, breakdown, scenarios, monte_carlo | Flexibilidad de schema para estructuras variables sin normalización excesiva |
| `equityValue()` en `CompanyFinancials` | Market cap si > 0, totalEquity como fallback; abstrae la fuente del valor |

---

## STACK TÉCNICO

| Capa | Tecnología | Versión | Quirks conocidos |
|------|-----------|---------|-----------------|
| Lenguaje | Java | 21 | Records, sealed classes disponibles |
| Framework | Spring Boot | 3.3.7 | `RestClient` reemplaza `RestTemplate` |
| Build | Maven | 3.9+ | Multi-módulo; `mvn -pl <módulo>` para builds parciales |
| Base de datos | PostgreSQL | 16 | JSONB requiere `@JdbcTypeCode(SqlTypes.JSON)` en Hibernate 6 |
| Migraciones | Flyway | (BOM de Spring Boot) | Archivo actual más alto: V9 |
| HTTP Client | RestClient | (Spring 6.1+) | Síncrono; `ParameterizedTypeReference<>` para listas |
| Resiliencia | Resilience4j | 2.2.0 | BOM declarado en pom raíz |
| Caché | Caffeine | (BOM de Spring Boot) | valuations: 24h; watchlist: 5min |
| API docs | springdoc-openapi | 2.6.0 | Swagger UI en `/swagger-ui.html` |
| Tests | JUnit 5 + Mockito | (Spring Boot BOM) | H2 en modo PostgreSQL para tests de api-web |
| Frontend | React + Vite + TypeScript + Recharts | — | `cd frontend && npm run dev` |

**Anti-patrones prohibidos:**
- `double` o `float` en cualquier cálculo financiero → usar `BigDecimal`
- `@Component`, `@Service`, `@Autowired` en `valuation-engine`
- `ddl-auto: update` o `create-drop` fuera de tests
- `null` en retorno de métodos públicos del engine → usar `Optional` o lanzar excepción
- Asumir APIs de Spring Boot 3.x, Hibernate 6.x o Vite de memoria → usar context7

---

## CONTRATOS DE COMPONENTES

### Entrada al engine: `CompanyFinancials` (record, 17 campos)

```java
CompanyFinancials(
    String ticker,
    List<BigDecimal> historicalFcf,      // ascendente (más antiguo primero), ≥1 elemento
    BigDecimal totalDebt,
    BigDecimal cashAndEquivalents,
    BigDecimal totalEquity,
    BigDecimal interestExpense,
    BigDecimal incomeTaxExpense,
    BigDecimal beta,
    long sharesOutstanding,              // > 0
    BigDecimal ebitda,
    BigDecimal marketCap,               // > 0 → usa para WACC; = 0 → fallback a totalEquity
    List<BigDecimal> analystFcfEstimates, // puede ser vacío
    String sector,                       // puede ser null; "Technology", "Energy", etc.
    BigDecimal operatingLeaseObligations, // opcional (null → ZERO)
    BigDecimal pensionLiabilities,       // opcional (null → ZERO)
    BigDecimal minorityInterest,         // opcional (null → ZERO)
    BigDecimal capitalExpenditure        // opcional (null → fallback a CAGR en proyección)
)
```

Constructor de conveniencia de 13 args (sin campos opcionales de deuda ni capex):
`this(ticker, ..., sector, null, null, null, null)`

### Parámetros del cálculo: `DcfParameters` (record)
- `riskFreeRate`, `marketRiskPremium`, `terminalGrowthRate`, `projectionYears`, `marketPrice`
- `SectorDefaults.forSector(sector, price)` provee defaults sectoriales automáticamente

### Salida del engine: `ValuationResult` (record, 15 campos)
- Incluye: `intrinsicValuePerShare`, `wacc`, `verdict`, `breakdown`, `sensitivityMatrix`, `monteCarloResult` (nullable), `qualityScore`
- `breakdown` contiene: `sumPvFcfs`, `terminalValue`, `terminalValueExitMultiple`, `netDebt`, `wacc`, `effectiveTaxRate`, `creditSpread`, `sizeRiskPremium`, `roic`, `maxSustainableGrowth`, `growthExceedsRoic`

### Flujo en `ValuationService.calculate()`:
1. Ingestar si ticker no existe → `FinancialDataService.ingest()`
2. `buildCompanyFinancials()` → combina CASHFLOW + BALANCE + INCOME de DB
3. `SectorDefaults.forSector()` si sector disponible; si no, defaults de `application.yml`
4. `DcfCalculator.calculate()` → `ValuationResult`
5. `SensitivityAnalyzer.analyze()` → matriz 5×5
6. `ScenarioAnalyzer.analyze()` → [Base, Optimista, Pesimista]
7. `MonteCarloAnalyzer.analyze(..., 1000)` → percentiles p10–p90
8. Reconstruir `ValuationResult` con sensitivityMatrix + monteCarlo
9. Persistir vía `ValuationMapper.toEntity()` → `ValuationResultEntity`

---

## ESTÁNDARES DE CÓDIGO

### Java
- Records para todos los inputs/outputs del engine (inmutabilidad garantizada)
- `BigDecimal` + `MathContext.DECIMAL128` en todos los cálculos financieros
- `Optional` en lugar de retornar `null` en métodos públicos (excepto campos nullable de records donde está documentado)
- Nombres en inglés para clases, métodos y variables
- Comentarios en español solo donde la lógica financiera no sea autoevidente

### Módulo valuation-engine
- **Zero dependencias de Spring** — ni `@Component`, `@Service`, ni inyección de dependencias
- Constructores explícitos (canonical + conveniencia cuando aplica)
- Todos los métodos públicos deben tener tests unitarios

### Tests
- JUnit 5 + Mockito
- Nombre: `methodName_scenario_expectedBehavior()`
- Un assert principal por test
- Datos reales de AAPL/MSFT/XOM para casos de integración
- 122 tests verdes al cierre de la rama `feature/dcf-quality-improvements`

### Herramientas obligatorias

**Context7: SIEMPRE usar antes de implementar con cualquier librería del stack.**

Patrón obligatorio:
1. `resolve-library-id` → obtener ID
2. `get-docs` → leer documentación actualizada
3. Implementar

Aplica especialmente a: Spring Boot 3.x, Hibernate 6.x / JPA, Resilience4j 2.x, Vite config, React hooks, Recharts.

---

## PROBLEMAS CONOCIDOS Y SOLUCIONES

### Bug creditSpread=0.0864 para AAPL (resuelto en `feature/dcf-quality-improvements`)
**Síntoma:** `breakdown.creditSpread = 0.086400` (spread CCC) para Apple, que tiene >30x cobertura.
**Causa:** `WaccCalculator.calculateCreditSpread()` tenía un solo `if (interestExpense <= 0 || ebitda <= 0)` que retornaba spread CCC. Cuando `interestExpense = 0` (sin deuda o no disponible), caía en el peor spread.
**Fix:** Separar en dos condiciones: `interestExpense <= 0` → AAA (0.0063); `ebitda <= 0` → CCC (0.0864).
**Efecto en WACC:** Mínimo para AAPL (~0.002%) por el bajo `debtWeight` frente a su market cap de ~3.7T.

### Tests que usan constructor canónico de `CompanyFinancials`
Al añadir el campo `capitalExpenditure` (17° campo), todos los tests que usaban el constructor de 16 args se rompieron. Solución: agregar `null` como 17° argumento, o usar el constructor de conveniencia de 13 args.

### `ValuationResultTest.buildResult()` roto al añadir campos al record
Al añadir `monteCarloResult` (14°) y `qualityScore` (15°), el helper `buildResult()` usaba el constructor antiguo. Solución: añadir `null` y `0` como argumentos adicionales.

### Monte Carlo: WACC no se perturba directamente
`MonteCarloAnalyzer` no recalcula el WACC completo por iteración (demasiado costoso × 1000 simulaciones). En su lugar: ajusta `riskFreeRate` para aproximar el WACC objetivo, y escala el último FCF histórico para reflejar el `growthRate` perturbado.

### H2 para tests de api-web
H2 no soporta JSONB nativo. Usar `@Column(columnDefinition = "jsonb")` junto con `@JdbcTypeCode(SqlTypes.JSON)` funciona en producción (PostgreSQL), pero en tests H2 se mapea a `TEXT`. Configuración en `application-test.yml`.

### `interestExpense` puede ser 0 en FMP
La API de FMP a veces devuelve `interestExpense = 0` en el income statement incluso para empresas con deuda. El `ValuationService` hace fallback al cashflow statement, pero puede seguir siendo 0. Esto es correcto: activa el spread AAA en `calculateCreditSpread`, no CCC.

---

## WORKFLOW DE DESARROLLO

### Crear nueva feature
```bash
git checkout develop
git checkout -b feature/<nombre>
# 1. Escribir test (que falla)
# 2. Implementar mínimo código
# 3. Pasar tests
mvn test -pl valuation-engine        # engine puro
mvn test                             # suite completa (122+ tests)
# 4. Commit solo cuando todo verde
```

### Fix de bug
```bash
git checkout develop
git checkout -b fix/<nombre>
# 1. Escribir test que reproduce el bug (debe fallar)
# 2. Corregir código
# 3. Verificar test verde + no regresiones
mvn test
```

### Antes de cada commit
```bash
mvn test                             # todos los módulos
mvn compile -pl api-web -am          # verificar que api-web compila
```

### Antes de merge a develop
- Todos los tests verdes (`mvn test`)
- Validación manual en Postman para cambios que afecten la API
- Actualizar `plan-*.md` con estado de tareas completadas
- **No hacer commit/push hasta validar desde Postman o el frontend** (directiva del equipo)

### Migración Flyway
Archivos en `api-web/src/main/resources/db/migration/`:
- Último: `V9__add_quality_score_to_valuation_result.sql`
- Próximo: `V10__...sql`
- Usar `IF NOT EXISTS` en `ALTER TABLE` para idempotencia

---

## CONTEXTO DE NEGOCIO

### ¿Qué hace el sistema?
Calcula el valor intrínseco de acciones usando el modelo DCF (Discounted Cash Flow). Dado un ticker (ej. "AAPL"), ingesta datos financieros de FMP API, ejecuta el motor de valoración y devuelve un precio intrínseco con su margen de seguridad respecto al precio de mercado.

### Glosario del dominio DCF

| Término | Significado |
|---------|------------|
| **FCF** | Free Cash Flow = Operating Cash Flow − CapEx |
| **CAGR** | Compound Annual Growth Rate — tasa de crecimiento compuesta histórica |
| **WACC** | Weighted Average Cost of Capital — promedio ponderado del costo de capital |
| **Ke** | Costo de equity: `Rf + β × MRP + sizeRiskPremium` (CAPM ajustado) |
| **Kd** | Costo de deuda: `(Rf + creditSpread) × (1 - taxRate)` |
| **MRP** | Market Risk Premium — prima de riesgo de mercado |
| **CAPM** | Capital Asset Pricing Model: `Ke = Rf + β × MRP` |
| **ROIC** | Return on Invested Capital: `NOPAT / investedCapital` |
| **NOPAT** | Net Operating Profit After Tax: `EBITDA × (1 - taxRate)` |
| **ICR** | Interest Coverage Ratio: `EBITDA / interestExpense` |
| **Net Debt ajustado** | `totalDebt + leases + pensiones + minorityInterest - cash` |
| **Terminal Value** | Valor de la empresa más allá del horizonte de proyección (Gordon Growth Model) |
| **Exit Multiple** | TV alternativo: `EBITDA_N × sectorMultiple / (1+WACC)^N` |
| **Margin of Safety** | `(IV - marketPrice) / marketPrice × 100` |
| **Verdict** | UNDERVALUED si margen > 15%, OVERVALUED si margen < −15%, FAIR_VALUE en el resto |
| **Quality Score** | 0–100 pts: FCF Growth + FCF Consistency + ROIC vs WACC + Leverage + Margin Trend |

### Reglas financieras clave
- CapEx siempre positivo en el dominio (la API FMP puede devolverlo negativo; el mapper lo normaliza)
- `investedCapital = totalDebt + totalEquity - cash` (puede ser negativo para empresas con caja neta)
- WACC debe ser siempre > `terminalGrowthRate` (si no, `TerminalValueCalculator` lanza excepción)
- Spread crediticio: ICR ≥ 8.5x → AAA (0.63%); ICR < 0.8x → CCC (8.64%)
- Size risk premium: mega cap (>$100B) → 0%; small cap (<$300M) → 2%
