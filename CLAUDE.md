# CLAUDE.md — Stock Valuator

Guía de referencia para Claude Code al trabajar en este proyecto.

---

## Arquitectura del proyecto

Maven multi-módulo con 3 módulos:

```
stock-valuator/
├── data-ingestion/     # Cliente FMP API, DTOs, entidades JPA, persistencia
├── valuation-engine/   # Lógica DCF pura — Java sin Spring, sin frameworks
└── api-web/            # Spring Boot app: controllers REST, Flyway, Swagger
```

**Paquete raíz:** `com.nuvixtech.stockvaluator`

**Dependencias entre módulos:**
- `api-web` depende de `data-ingestion` y `valuation-engine`
- `data-ingestion` depende de `valuation-engine`
- `valuation-engine` no depende de ningún módulo interno

---

## Directivas obligatorias

### 1. TDD siempre
Escribir el test **antes** de la implementación en todos los casos, salvo que el usuario indique explícitamente lo contrario.

Orden obligatorio:
1. Escribir el test (que falla en rojo)
2. Implementar el mínimo código para que pase
3. Refactorizar si es necesario

### 2. Usar context7 siempre
Antes de usar cualquier librería o API del framework (Spring Boot, Resilience4j, Caffeine, Recharts, etc.), consultar context7 para obtener la documentación actualizada. No asumir APIs de memoria.

---

## Convenciones de código

### Java
- Records para DTOs e inputs/outputs inmutables
- `BigDecimal` para todos los cálculos financieros (nunca `double` ni `float`)
- `Optional` en lugar de retornar `null`
- Nombres en inglés para clases, métodos y variables
- Comentarios en español solo donde la lógica financiera no sea autoevidente

### Módulo valuation-engine
- **Zero dependencias de Spring** — ni `@Component`, ni `@Service`, ni inyección de dependencias
- Constructores explícitos, sin reflexión ni magia de frameworks
- Todos los métodos públicos deben tener tests unitarios

### Tests
- JUnit 5 + Mockito
- Nombres de método: `methodName_scenario_expectedBehavior()`
- Un assert principal por test (múltiples asserts solo si verifican el mismo concepto)
- Datos financieros de AAPL/MSFT/GOOG para casos de integración

---

## Stack técnico

| Capa | Tecnología |
|------|-----------|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.3 |
| Build | Maven 3.9+ (multi-módulo) |
| Base de datos | PostgreSQL 16 |
| Migraciones | Flyway |
| HTTP Client | RestClient (Spring) |
| Resiliencia | Resilience4j |
| Cache | Caffeine |
| API docs | springdoc-openapi |
| Tests | JUnit 5 + Mockito |
| Frontend | React + Vite + TypeScript + Recharts |

---

## Comandos útiles

```bash
# Compilar todo el proyecto
mvn compile

# Ejecutar todos los tests
mvn test

# Ejecutar tests de un módulo específico
mvn test -pl valuation-engine

# Build completo con tests
mvn verify

# Build sin tests
mvn package -DskipTests

# Arrancar la app (requiere PostgreSQL corriendo)
cd api-web && mvn spring-boot:run

# Dev frontend
cd frontend && npm run dev
```

---

## Variables de entorno requeridas

```
FMP_API_KEY=<tu API key de financialmodelingprep.com>
DB_USERNAME=sv_user        # default si no se especifica
DB_PASSWORD=sv_pass        # default si no se especifica
```

---

## Parámetros DCF por defecto

| Parámetro | Valor | Descripción |
|-----------|-------|-------------|
| `riskFreeRate` | 0.045 | Tasa libre de riesgo (US 10Y Treasury) |
| `marketRiskPremium` | 0.055 | Prima de riesgo de mercado histórica |
| `terminalGrowthRate` | 0.025 | Tasa de crecimiento terminal (≈ inflación) |
| `projectionYears` | 10 | Años de proyección de FCF |

---

## Reglas financieras clave

- **FCF** = Operating Cash Flow − |CapEx| (CapEx siempre positivo en el dominio)
- **Net Debt** = Total Debt − Cash & Equivalents
- **Intrinsic Value/share** = (PV FCFs + PV Terminal Value − Net Debt) / Shares Outstanding
- **Margin of Safety** = (Intrinsic Value − Market Price) / Market Price × 100
- **Verdict:** UNDERVALUED si margen > 15%, OVERVALUED si margen < −15%, FAIR_VALUE en el resto
