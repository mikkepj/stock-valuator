# Workflow: Nueva Feature (TDD obligatorio)

Stack: Java 21 · Spring Boot 3.3 · Maven multi-módulo

---

## PASO 0 — Context7 ANTES de escribir código

**Obligatorio.** Antes de usar cualquier librería o API del framework:

```
1. mcp__context7__resolve-library-id  → obtener el ID de la librería
2. mcp__context7__get-library-docs    → leer la documentación actualizada
3. Implementar con la API real
```

Aplica especialmente a: Spring Boot 3.x, Hibernate 6.x / JPA, Resilience4j 2.x, Vite, React hooks, Recharts.
**Nunca asumir APIs de memoria.**

---

## PASO 1 — Decidir en qué módulo va el código

| Si la lógica es... | Va en |
|-------------------|-------|
| Cálculo financiero puro, sin I/O | `valuation-engine` |
| Acceso a DB, FMP API, entidades JPA | `data-ingestion` |
| Endpoint REST, caché, Flyway, config Spring | `api-web` |

Regla: `valuation-engine` no puede importar nada de Spring ni de los otros módulos.

---

## PASO 2 — Escribir el test (en rojo)

### Si el código va en valuation-engine:
```java
// Archivo: valuation-engine/src/test/java/.../NombreClaseTest.java
class NombreClaseTest {
    @Test
    void methodName_scenario_expectedBehavior() {
        // Arrange: datos mínimos para el caso
        // Act: llamar al método
        // Assert: un assert principal
    }
}
```

Ejecutar para confirmar que falla:
```bash
mvn test -pl valuation-engine -Dtest=NombreClaseTest
```

### Si el código va en api-web:
```bash
mvn test -pl api-web -am -Dtest=NombreControllerTest
```

---

## PASO 3 — Implementar el mínimo código

### En valuation-engine
- Clase normal con constructor explícito (sin `@Component`)
- `BigDecimal` + `MathContext.DECIMAL128` para todos los cálculos
- Si es un record: constructor canónico con `Objects.requireNonNull` para campos obligatorios
- Si añades campo a un record existente: buscar y actualizar TODOS los call sites:
  ```bash
  grep -r "new NombreRecord(" --include="*.java"
  ```

### En api-web
- Registrar beans nuevos de valuation-engine en `ValuationEngineConfig`
- Si requiere columna nueva en DB: crear `V{N+1}__descripcion.sql` en `db/migration/`
- Usar `IF NOT EXISTS` en `ALTER TABLE` para idempotencia

---

## PASO 4 — Verificar que el test pasa

```bash
mvn test -pl valuation-engine    # solo el engine
mvn test                         # suite completa (todos los módulos)
```

No avanzar al siguiente paso si hay tests en rojo.

---

## PASO 5 — Refactorizar si es necesario

Solo si hay duplicación real o el código es confuso. No añadir abstracciones especulativas.

---

## PASO 6 — Verificar compilación completa

```bash
mvn compile -pl api-web -am
```

---

## PASO 7 — Validación manual (NO hacer commit hasta completar esto)

Para cambios en la API REST:
1. Arrancar el servidor: `mvn spring-boot:run -q`
2. Probar en Postman el endpoint afectado
3. Verificar el JSON de respuesta completo
4. Confirmar con el usuario que el resultado es correcto

**Directiva del equipo: no subir nada a git hasta validar desde Postman o el frontend.**

---

## Checklist antes de commit

- [ ] `mvn test` → todos verdes
- [ ] `mvn compile -pl api-web -am` → sin errores
- [ ] Validación manual en Postman completada
- [ ] Migración Flyway añadida si hay cambio de schema
- [ ] `ValuationEngineConfig` actualizado si hay beans nuevos
- [ ] Plan de mejoras (`plan-*.md`) actualizado con estado de tareas
