---
name: review
description: Checklist de code review específico para stock-valuator. Cubre anti-patrones del engine (BigDecimal, sin Spring, records inmutables), correctitud financiera (FCF, WACC, creditSpread), reglas de api-web y estándares de tests.
argument-hint: "[rama o PR a revisar]"
---
## Agente encargado

Invocar al agente `java-reviewer` para ejecutar esta revisión.
El agente tiene contexto especializado del stack de este proyecto
(Java 21, Spring Boot 3.3, valuation-engine sin Spring, BigDecimal).

---

# Checklist de Code Review

Anti-patrones específicos de este proyecto.

---

## valuation-engine — Reglas absolutas

- [ ] **Sin Spring**: ningún `@Component`, `@Service`, `@Autowired`, ni import de `org.springframework` en `valuation-engine`
- [ ] **Sin double/float en cálculos**: todos los cálculos financieros usan `BigDecimal` con `MathContext.DECIMAL128`
- [ ] **Sin null retornado**: métodos públicos del engine no retornan `null` sin documentarlo explícitamente en el record
- [ ] **Records inmutables**: los constructores canónicos de records llaman `List.copyOf()` en todas las listas recibidas
- [ ] **Constructor de conveniencia actualizado**: si se añadió campo opcional al record canónico, el constructor de conveniencia de 13 args también fue actualizado
- [ ] **Tests antes que código**: existe al menos un test por cada método público nuevo

---

## Cálculos financieros — Correctitud

- [ ] **FCF**: `operatingCashFlow - |capex|` (CapEx siempre positivo en el dominio)
- [ ] **Net Debt ajustado**: incluye `operatingLeaseObligations + pensionLiabilities + minorityInterest`
- [ ] **WACC early return**: si `totalDebt == 0`, retorna `costOfEquity` directamente sin calcular Kd
- [ ] **creditSpread**: `interestExpense <= 0` → spread AAA (no CCC); `ebitda <= 0` → spread CCC
- [ ] **Terminal Value**: lanza excepción si `wacc <= terminalGrowthRate`
- [ ] **ROIC investedCapital**: puede ser negativo para empresas con caja neta → el código maneja ese caso

---

## api-web — Spring Boot

- [ ] **Beans del engine registrados**: todo componente nuevo de `valuation-engine` está en `ValuationEngineConfig`
- [ ] **Migración Flyway**: si se añade columna a DB, existe el archivo `V{N}__descripcion.sql` con `IF NOT EXISTS`
- [ ] **No ddl-auto update**: `spring.jpa.hibernate.ddl-auto` no es `update` ni `create-drop` en `application.yml`
- [ ] **JSONB**: campos de tipo `Map`/`List` en entidades JPA usan `@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition = "jsonb"`
- [ ] **@CacheEvict antes de recalcular**: `calculate()` tiene `@CacheEvict` para limpiar la entrada antes de persistir el nuevo resultado
- [ ] **safeValue para nulos de DB**: campos opcionales de `FinancialStatement` se leen con `safeValue()` en `buildCompanyFinancials()`

---

## Tests

- [ ] **Nombre del test**: sigue el patrón `methodName_scenario_expectedBehavior()`
- [ ] **Un assert principal por test**: múltiples asserts solo si verifican el mismo concepto
- [ ] **Call sites de records actualizados**: si se cambió la firma de un record, todos los `new NombreRecord(` en tests están actualizados
- [ ] **Test de bug reproducido**: si es un fix, existe un test que fallaba antes del fix y pasa después
- [ ] **No mocks del engine**: los tests de `valuation-engine` son puros (sin Mockito); Mockito solo en tests de `api-web`

---

## General

- [ ] **Sin comentarios obvios**: los comentarios explican el "por qué" financiero, no el "qué" hace el código
- [ ] **Sin código muerto**: no hay `// TODO`, variables `_unused`, ni imports sin usar
- [ ] **Sin manejo de errores inventado**: no hay `try/catch` para casos que no pueden ocurrir en el flujo normal
- [ ] **Sin abstracciones prematuras**: no hay interfaces con una sola implementación, ni factories innecesarios
