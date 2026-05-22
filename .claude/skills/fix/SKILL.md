---
name: fix
description: Workflow de fix de bug con TDD obligatorio (test-first) para el proyecto stock-valuator. Reproduce el bug con un test que falla, aplica el fix mínimo y valida sin regresiones.
argument-hint: "[descripción breve del bug]"
---

# Workflow: Fix de Bug (test-first)

---

## PASO 0 — Documentar el bug antes de tocarlo

Crear `docs/plans/fix-<nombre>.md` con:

### Síntoma
[Qué ocurre exactamente, cuándo, con qué datos de ejemplo]

### Causa raíz hipotética
[Primera hipótesis — se confirma o descarta en PASO 2]

### Opciones de fix
[Al menos 2 opciones con trade-offs — elegir una antes de implementar]

### Test que reproduce el bug
[Given / When / Then del test que debe fallar primero]

### Criterio de éxito
[Qué debe pasar para considerar el bug resuelto]

**Esperar aprobación antes de continuar con el PASO 1.**

---

## PASO 1 — Reproducir el bug con un test

Antes de tocar el código de producción, escribir un test que falle por la razón correcta.

```java
@Test
void methodName_bugScenario_correctBehavior() {
    // Reproduce exactamente el caso que falla en producción
    // El test debe fallar AHORA y pasar DESPUÉS del fix
}
```

Ejecutar para confirmar que falla:
```bash
mvn test -pl valuation-engine -Dtest=ClaseAfectadaTest
```

Si el bug es en api-web:
```bash
mvn test -pl api-web -am -Dtest=ClaseAfectadaTest
```

---

## PASO 2 — Identificar la causa raíz

Antes de modificar código, entender qué rama lógica produce el resultado incorrecto.

Preguntas guía:
- ¿El dato de entrada es el esperado o viene corrupto desde antes?
- ¿El método afectado tiene un `if` con lógica combinada que debería ser separada?
- ¿El bug existe en el engine (`valuation-engine`) o en la capa de mapeo/servicio?

Para bugs de WACC/FCF/IV: revisar `breakdown` en la respuesta — expone los valores intermedios que permiten rastrear el origen.

---

## PASO 3 — Aplicar el fix mínimo

- Modificar solo lo necesario para que el test del Paso 1 pase
- No refactorizar código circundante en el mismo commit
- No añadir manejo de errores para casos que no pueden ocurrir

---

## PASO 4 — Verificar no regresiones

```bash
mvn test                         # suite completa
```

Si hay tests en rojo después del fix, son regresiones — corregirlas antes de avanzar.

---

## PASO 5 — Validar en Postman si el fix afecta la API

Para bugs en el resultado del cálculo DCF:
1. Arrancar servidor: `mvn spring-boot:run -q`
2. `POST /api/v1/valuations/calculate` con `{"ticker": "AAPL"}`
3. Verificar el campo afectado en `breakdown`
4. Comparar con el valor esperado

**No hacer commit hasta que el usuario confirme el resultado en Postman.**

---

## Patrones de bugs conocidos en este proyecto

| Síntoma | Lugar probable | Qué revisar |
|---------|---------------|-------------|
| `creditSpread` incorrecto | `WaccCalculator.calculateCreditSpread()` | Condiciones `interestExpense <= 0` vs `ebitda <= 0` separadas |
| WACC no cambia al variar parámetros | `WaccCalculator.calculate()` | Si `totalDebt == 0` retorna early sin calcular Kd |
| IV inflado respecto a lo esperado | `DcfCalculator` / `FreeCashFlowProjector` | Verificar si usa path ROIC o CAGR; revisar `capex` en CompanyFinancials |
| `qualityScore = 0` sin razón | `QualityScoreCalculator` | `historicalFcf` con negativos o menos de 2 puntos |
| `NoSuchMethodError` en tests | Record con campos nuevos | Buscar todos los `new NombreRecord(` y actualizar args |
| Monte Carlo `p50` muy distinto al IV base | `MonteCarloAnalyzer` | CAGR histórico negativo → growthRate base = terminalRate |

## Checklist antes de commit

- [ ] Test que reproduce el bug → en verde
- [ ] `mvn test` → suite completa sin regresiones
- [ ] Validación en Postman completada (si afecta API)
- [ ] `docs/plans/fix-<nombre>.md` actualizado con causa raíz y fix aplicado
