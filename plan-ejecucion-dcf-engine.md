# StockValuator — Plan de ejecución

## Objetivos del proyecto

| # | Objetivo | Indicador de éxito |
|---|----------|-------------------|
| 1 | **Aprendizaje** — dominar tecnologías nuevas | Spring Boot 3, Java 17 records, RestClient, Docker, React, Resilience4j, CI/CD |
| 2 | **Herramienta personal** — valor intrínseco en clicks | Dado un ticker, obtener DCF desglosado en <3 segundos |
| 3 | **Potencial de ingresos** — si la solución madura | API pública, landing page, modelo freemium |

---

## Nombre del proyecto: `stock-valuator`

**Repositorio:** Git Flow (main, develop, feature/*, release/*, hotfix/*)

**Stack definitivo:**
- Java 17 + Spring Boot 3.2+
- Maven multi-module (3 módulos)
- PostgreSQL 16
- FMP API (free tier → paid si escala)
- React + Recharts (dashboard)
- Docker + Docker Compose
- JUnit 5 + Mockito (testing)

---

## Fase 0 — Fundación (Semana 1)

**Objetivo:** Proyecto compilable, corriendo en Docker, con CI básico.

### Tareas

1. **Crear repositorio Git** con Git Flow inicializado
2. **Generar proyecto** en Spring Initializr:
   - Spring Boot 3.2+, Java 17, Maven
   - Dependencias: Spring Web, Spring Data JPA, PostgreSQL Driver, Validation, Actuator
3. **Estructurar Maven multi-module:**
   ```
   stock-valuator/
   ├── pom.xml (parent)
   ├── data-ingestion/
   │   └── pom.xml
   ├── valuation-engine/
   │   └── pom.xml
   └── api-web/
       └── pom.xml
   ```
4. **Docker Compose** con dos servicios:
   ```yaml
   services:
     app:
       build: .
       ports: ["8080:8080"]
       depends_on: [db]
     db:
       image: postgres:16
       volumes: [pgdata:/var/lib/postgresql/data]
       environment:
         POSTGRES_DB: stockvaluator
         POSTGRES_USER: sv_user
         POSTGRES_PASSWORD: ${DB_PASSWORD}
   ```
5. **Schema inicial** (Flyway migrations):
   - `company` (ticker, name, sector, exchange)
   - `financial_statement` (company_id, year, statement_type, data JSONB)
   - `valuation_result` (company_id, date, intrinsic_value, market_price, margin_of_safety)
6. **Health check** — `GET /actuator/health` responde 200

### Entregable
Proyecto corre con `docker-compose up`, conecta a PostgreSQL, health check verde.

### Tecnologías nuevas que aprendes
- Maven multi-module con BOM parent
- Docker multi-stage build
- Flyway migrations
- Spring Boot 3.2 features (nuevo RestClient)

---

## Fase 1 — Data Ingestion (Semanas 2-3)

**Objetivo:** Dado un ticker, extraer los 5 años de financial statements desde FMP y persistirlos.

### Tareas

1. **Registrar cuenta FMP** — obtener API key gratuita (250 req/día)
2. **Crear DTOs de FMP** como Java records:
   - `FmpIncomeStatement`
   - `FmpBalanceSheet`
   - `FmpCashFlowStatement`
   - `FmpQuote`
   - `FmpEnterpriseValue`
3. **Implementar `FmpApiClient`** usando `RestClient` (Spring Boot 3.2):
   - Métodos: `getIncomeStatements(ticker, years)`, `getBalanceSheets(...)`, etc.
   - Manejo de errores: 429 (rate limit), 404 (ticker no encontrado), timeout
4. **Implementar `FinancialDataMapper`:**
   - Transforma DTOs de FMP → entidades de dominio
   - Normaliza campos (CapEx negativo → positivo, fechas String → LocalDate)
   - Calcula FCF = Operating Cash Flow - |CapEx|
5. **Implementar `FinancialDataService`** (orquestador):
   - Dado un ticker, llama al client, mapea, y persiste via repository
   - Guarda raw JSON en columna JSONB como backup
6. **Rate limiting** con Resilience4j `@RateLimiter`
7. **`@Scheduled` job** semanal para refresh de watchlist:
   ```java
   @Scheduled(cron = "0 0 6 * * SAT")
   public void refreshWatchlist() { ... }
   ```
8. **Tests:**
   - Unit tests del Mapper (datos conocidos → resultado esperado)
   - Integration test del Client con WireMock (mock de FMP API)
   - Repository test con @DataJpaTest + Testcontainers (PostgreSQL real)

### Entregable
`POST /api/v1/ingest/{ticker}` descarga y persiste los financials. Log confirma datos guardados.

### Tecnologías nuevas que aprendes
- `RestClient` (reemplazo moderno de RestTemplate)
- Resilience4j (rate limiting, retry, circuit breaker)
- WireMock para testing de APIs externas
- Testcontainers para PostgreSQL en tests
- Java 17 records como DTOs

---

## Fase 2 — Valuation Engine (Semanas 3-4)

**Objetivo:** Lógica DCF pura, sin dependencias de framework. 100% testeable.

### Tareas

1. **`FreeCashFlowProjector`** — proyecta FCF futuros:
   - Input: últimos 5 años de FCF histórico
   - Calcula CAGR histórico como tasa base de crecimiento
   - Proyecta N años (configurable, default 10)
   - Aplica decay: la tasa de crecimiento decrece hacia la tasa terminal
2. **`WaccCalculator`** — calcula Weighted Average Cost of Capital:
   - Cost of Equity via CAPM: `Rf + Beta × (Rm - Rf)`
   - Cost of Debt: `Interest Expense / Total Debt × (1 - Tax Rate)`
   - WACC: `E/(E+D) × Ke + D/(E+D) × Kd`
   - Risk-free rate y market premium configurables
3. **`TerminalValueCalculator`:**
   - Gordon Growth: `FCF_last × (1 + g) / (WACC - g)`
   - Exit Multiple como alternativa: `EBITDA × multiple`
4. **`DcfCalculator`** — orquestador principal:
   - Suma PV de FCFs proyectados + PV de Terminal Value
   - Resta Net Debt (Total Debt - Cash)
   - Divide por Shares Outstanding
   - Retorna `ValuationResult` con intrinsic value + margin of safety
5. **Sensitivity analysis:**
   - Varía growth rate ±2% y WACC ±1%
   - Genera matriz de valores intrínsecos
6. **Validación contra FMP DCF endpoint:**
   - Compara tu cálculo vs el endpoint `/api/v3/discounted-cash-flow/{ticker}`
   - Documenta diferencias y ajusta supuestos
7. **Tests exhaustivos:**
   - Test con datos reales de AAPL, MSFT, GOOG (resultados conocidos)
   - Test de edge cases: FCF negativo, empresa sin deuda, growth > WACC
   - Test de sensitivity matrix
   - Property-based tests: intrinsic value siempre > 0 si FCFs > 0

### Entregable
Dado un `CompanyFinancials`, el engine retorna `ValuationResult` con DCF desglosado. Tests verdes.

### Tecnologías nuevas que aprendes
- Diseño de librería Java pura (sin Spring)
- Property-based testing (jqwik o similar)
- Modelado financiero en código
- Sensitivity analysis programática

---

## Fase 3 — API REST (Semana 5)

**Objetivo:** Endpoints que exponen la valuación para cualquier consumidor.

### Tareas

1. **Endpoints principales:**
   ```
   GET  /api/v1/valuations/{ticker}          → DCF completo
   GET  /api/v1/valuations/{ticker}/summary   → resumen: precio, valor, margen
   POST /api/v1/valuations/{ticker}/calculate → fuerza recálculo
   GET  /api/v1/watchlist                     → lista de tickers monitoreados
   POST /api/v1/watchlist/{ticker}            → agrega ticker
   DELETE /api/v1/watchlist/{ticker}          → remueve ticker
   ```
2. **Response DTOs** bien estructurados:
   ```json
   {
     "ticker": "AAPL",
     "companyName": "Apple Inc",
     "currentPrice": 178.50,
     "intrinsicValue": 162.30,
     "marginOfSafety": -9.1,
     "verdict": "OVERVALUED",
     "dcfBreakdown": {
       "projectedFcfs": [...],
       "terminalValue": 2840000000000,
       "wacc": 0.0892,
       "netDebt": 49000000000,
       "sharesOutstanding": 15500000000
     },
     "sensitivityMatrix": {...},
     "lastUpdated": "2026-03-28T06:00:00Z"
   }
   ```
3. **Swagger/OpenAPI** con springdoc-openapi
4. **Exception handling** global con `@ControllerAdvice`
5. **Caching** con `@Cacheable` (Spring Cache + Caffeine):
   - Valuaciones cacheadas por 24h
   - Invalidación cuando llegan datos nuevos
6. **Tests:** MockMvc para cada endpoint

### Entregable
Swagger UI en `/swagger-ui.html` con todos los endpoints documentados y funcionales.

### Tecnologías nuevas que aprendes
- springdoc-openapi (reemplazo de Springfox)
- Caffeine cache
- Response DTOs con record patterns

---

## Fase 4 — Dashboard (Semanas 6-7)

**Objetivo:** Interfaz visual donde en 2 clicks ves el DCF desglosado de un ticker.

### Tareas

1. **Setup React** (Vite + TypeScript):
   - Carpeta `frontend/` en el mismo repo
   - Proxy a `localhost:8080` en dev
2. **Pantalla principal — Watchlist:**
   - Tabla con tickers, precio actual, valor intrínseco, margen, veredicto
   - Color coding: verde (undervalued >15%), amarillo (fair value ±15%), rojo (overvalued)
   - Botón "Agregar ticker"
3. **Pantalla de detalle — Ticker:**
   - Header: nombre, sector, precio vs valor con gauge visual
   - Gráfico de barras: FCF históricos + proyectados (Recharts)
   - Tabla: desglose del DCF (WACC, Terminal Value, Net Debt)
   - Heatmap de sensitivity analysis
4. **Search:** Autocomplete de tickers (endpoint de FMP)
5. **Responsive:** Mobile-friendly para revisar desde el celular
6. **Docker multi-stage build:**
   - Stage 1: build React → static files
   - Stage 2: Spring Boot sirve los static files + API

### Entregable
Dashboard funcional en `localhost:8080` con watchlist y detalle de ticker.

### Tecnologías nuevas que aprendes
- React + TypeScript + Vite
- Recharts para visualización financiera
- Docker multi-stage builds
- Servir SPA desde Spring Boot

---

## Fase 5 — Polish y Deploy (Semana 8)

**Objetivo:** Producción-ready, desplegado en cloud.

### Tareas

1. **Observabilidad:**
   - Structured logging con SLF4J + Logback
   - Métricas con Micrometer + Actuator
   - Endpoint `/actuator/prometheus` (opcional)
2. **Seguridad básica:**
   - API key para endpoints sensibles
   - CORS configurado para el dashboard
   - Rate limiting en endpoints públicos
3. **Deploy a Railway o Render:**
   - Dockerfile optimizado (multi-stage, layered JARs)
   - Variables de entorno: DB_URL, FMP_API_KEY, etc.
   - PostgreSQL managed (plan gratuito o starter)
   - Dominio custom (opcional)
4. **CI/CD:**
   - GitHub Actions: build → test → docker push → deploy
   - Pipeline dispara en push a `main`
5. **Monitoreo:**
   - Alerta si el job de refresh falla
   - n8n workflow: si precio < valor intrínseco × 0.8 → notificación Telegram

### Entregable
App corriendo en `https://stockvaluator.up.railway.app` (o similar). CI/CD verde.

---

## Fase 6 — Monetización (Futuro, si aplica)

**Solo si la Fase 5 está sólida y hay interés externo.**

### Posibles fuentes de ingreso

1. **API pública (SaaS):**
   - Plan free: 10 valuaciones/día
   - Plan pro ($9/mes): valuaciones ilimitadas + sensitivity analysis + alertas
   - Plan enterprise: API keys para equipos, webhook notifications
2. **Dashboard premium:**
   - Comparación side-by-side de múltiples tickers
   - Screener: "muéstrame todas las acciones con margen de seguridad >30%"
   - Exportar reportes PDF
3. **Contenido educativo:**
   - Blog/newsletter: "Las 10 acciones más subvaloradas esta semana según DCF"
   - Curso: "Cómo construir tu propio motor de valuación"

### Tareas técnicas adicionales
- Spring Security + JWT para autenticación
- Stripe integration para pagos
- Landing page (NuvixTech como vehículo)
- Rate limiting por plan de usuario
- Multi-tenancy básico

---

## Resumen de timeline

```
Semana 1    ██░░░░░░░░  Fase 0: Setup del proyecto
Semana 2-3  ████░░░░░░  Fase 1: Data Ingestion (FMP API)
Semana 3-4  ██████░░░░  Fase 2: Valuation Engine (DCF)
Semana 5    ████████░░  Fase 3: API REST
Semana 6-7  ██████████  Fase 4: Dashboard
Semana 8    ██████████  Fase 5: Deploy + Polish
Futuro      ░░░░░░░░░░  Fase 6: Monetización (si aplica)
```

## Principios de ejecución

1. **Vertical slices** — Cada fase produce algo que funciona end-to-end, no capas incompletas.
2. **Tests first para el engine** — El módulo `valuation-engine` se desarrolla con TDD estricto. Es el core del negocio.
3. **Trunk-based en solitario** — Aunque uses Git Flow por práctica, no te atasques en branches largos. Feature branches cortos, merge frecuente.
4. **Ship early** — No esperes a tener todo perfecto. Con la Fase 3 lista ya tienes valor: puedes consultar valuaciones desde Postman.
5. **Aprendizaje deliberado** — Cada fase introduce 3-4 tecnologías nuevas. Documenta lo que aprendas en un `LEARNINGS.md` en el repo.
