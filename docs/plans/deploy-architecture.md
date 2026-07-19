# Propuesta de Arquitectura de Deploy — Stock Valuator

> Fecha: 2026-06-06
> Alcance: FE (`stock-valuator-ui`) + BE (`stock-valuator`)
> Contexto: proyecto personal, bajo tráfico, prioridad **costo $0** (al menos por ahora)

---

## 1. Análisis del stack actual

### Frontend — `stock-valuator-ui`

| Aspecto | Detalle |
|---------|---------|
| Tipo | **SPA estática** (sin SSR) |
| Build | `tsc -b && vite build` → carpeta `dist/` |
| Runtime en prod | Ninguno — solo sirve archivos estáticos (HTML/JS/CSS) |
| Stack | React 19, Vite 8, react-router-dom 7, Tailwind v4 |
| Config de backend | Proxy `/api` en dev; en prod usa `VITE_API_URL` (build-time) |

**Implicación de deploy:** es lo más fácil. Cualquier CDN/static host gratuito sirve. **No consume horas de cómputo ni se "duerme".** El único requisito es inyectar `VITE_API_URL` en build y configurar un rewrite SPA (todas las rutas → `index.html`) para que `react-router` funcione en refresh.

### Backend — `stock-valuator`

| Aspecto | Detalle |
|---------|---------|
| Tipo | **Proceso JVM siempre vivo** (Spring Boot 3.3.7, Java 21) |
| Build | Maven multi-módulo (3) → JAR ejecutable en `api-web` |
| Base de datos | **PostgreSQL 16 obligatorio** — Flyway gestiona el schema (9 migraciones V1–V9), `ddl-auto: validate` |
| Estado | Persistente: valuaciones, watchlist, FCF estimates, statements financieros |
| Dependencias externas | **FMP API** (Financial Modeling Prep) — requiere `FMP_API_KEY` |
| Cache | Caffeine en memoria (`maximumSize=500, expireAfterWrite=24h`) |
| Scheduler | Cron **semanal** — refresco de datos sábados 6:00 AM (`0 0 6 * * SAT`) |
| Resiliencia | Resilience4j (retries/timeouts a FMP) |
| CORS | Configurable por `stockvaluator.cors.allowed-origins` (env-overridable) |
| Memoria | JVM necesita **~200–300 MB de overhead** + heap. En 512 MB es ajustado pero viable para bajo tráfico |

**Secrets necesarios en prod:** `DB_USERNAME`, `DB_PASSWORD`, `DB_URL` (o `SPRING_DATASOURCE_*`), `FMP_API_KEY`, y `STOCKVALUATOR_CORS_ALLOWED-ORIGINS` con la URL real del FE.

**Puntos críticos para el deploy del BE:**

1. **Necesita Postgres persistente.** No sirve una DB que se borre. Ojo: la mayoría de DB gratuitas tienen políticas de expiración o pausa por inactividad.
2. **El cold start de la JVM es lento** (~30 s en hosts con poca CPU). En hosts que "duermen" tras 15 min de inactividad, la primera petición tras dormir tardará bastante.
3. **El scheduler semanal NO se ejecutará si el proceso está dormido** el sábado a las 6 AM. Si el host duerme por inactividad, el refresco automático se pierde (mitigable con un trigger externo o un host que no duerma).

---

## 2. Decisión de diseño base

Se recomienda **separar las tres piezas** (patrón estándar para este stack):

```
┌─────────────────┐      ┌──────────────────────┐      ┌────────────────────┐
│  FE estático    │ ───► │  BE Spring Boot      │ ───► │  PostgreSQL 16     │
│  (CDN gratis)   │ HTTPS│  (host de cómputo)   │ JDBC │  (DB gestionada)   │
└─────────────────┘      └──────────────────────┘      └────────────────────┘
                                   │
                                   ▼
                            FMP API (externa)
```

El FE casi siempre es gratis y sin fricción. **La decisión real está en dónde corre el BE + qué Postgres usar.** Las opciones de abajo se centran en eso.

---

## 3. Opciones de deploy del Frontend (todas $0)

| Opción | Costo | Setup | Limitaciones | Compatibilidad |
|--------|-------|-------|--------------|----------------|
| **Vercel** ⭐ | $0 (Hobby) | Muy bajo — detecta Vite solo, deploy on push | Uso personal/no comercial en Hobby; 100 GB/mes banda | Total. Rewrite SPA en `vercel.json`. Ya previsto en CLAUDE.md (Fase 4.5) |
| **Netlify** | $0 | Muy bajo | 100 GB/mes banda | Total. `_redirects` para SPA |
| **Cloudflare Pages** | $0 | Bajo | Builds/mes limitados, banda **ilimitada** | Total. Mejor CDN/banda del grupo |
| **GitHub Pages** | $0 | Medio (Actions para build) | Sin rewrites nativos cómodos para SPA; requiere truco 404 | Funciona pero es el más incómodo para router |

**Recomendación FE: Vercel** (alineado con el plan existente) o **Cloudflare Pages** si prefieres banda ilimitada. Ambos gratis de forma sostenible para este caso.

---

## 4. Opciones de deploy del Backend + Base de datos

> Aquí está el verdadero trade-off. Ordenadas de **menor a mayor esfuerzo de setup**.

### Opción A — Render (Web Service) + Neon (Postgres) ⭐ *Recomendada para arrancar rápido*

| | |
|---|---|
| **Costo** | **$0** |
| **Setup** | Bajo-medio. Render: conectar repo, build con `mvn package`, start con `java -jar`. Neon: crear proyecto, copiar connection string a env vars |
| **BE** | Render free: 512 MB RAM, 0.1 CPU, **se duerme tras 15 min** de inactividad (cold start ~1 min + arranque JVM) |
| **DB** | **Neon** en vez del Postgres de Render. Neon free no expira a los 30 días (el de Render **sí**), tiene scale-to-zero y reanuda en <500 ms. 0.5 GB storage — sobra para este dominio |
| **Limitaciones** | El sleep de Render hace lenta la 1ª petición tras inactividad. **El scheduler semanal se pierde si el servicio está dormido el sábado 6 AM** (mitigable: cron-ping externo tipo cron-job.org, o llamar al endpoint de refresco manualmente). 750 horas-instancia/mes (suficiente con sleep) |
| **Compatibilidad** | Total. Spring Boot + Postgres es el caso canónico. Solo cuidar memoria (perfil JVM `-XX:MaxRAMPercentage=75`) |

> ⚠️ **Clave:** usar **Neon** y NO el Postgres free de Render — este último **expira a los 30 días** (+14 de gracia) y luego borra los datos. Neon evita ese problema.

### Opción B — Railway (BE + Postgres en un solo lugar)

| | |
|---|---|
| **Costo** | $0 *teórico* con $5 de crédito/mes — pero el BE JVM **consume ese crédito rápido** (la JVM no escala a cero; corriendo 24/7 supera $5/mes) |
| **Setup** | **El más bajo.** Detecta el proyecto, provisiona Postgres con un click, env vars autoinyectadas |
| **BE** | 512 MB / 0.5 vCPU. No duerme → scheduler semanal **sí funciona** |
| **DB** | Postgres gestionado en la misma plataforma, persistente |
| **Limitaciones** | El crédito gratis **no alcanza para 24/7** de un proceso JVM. Sirve para demos cortas o si aceptas pagar ~$5–10/mes cuando crezca. No es "gratis sostenible 24/7" |
| **Compatibilidad** | Excelente DX. El mejor si en algún momento aceptas pagar poco |

### Opción C — Oracle Cloud Always Free (VM ARM) + Postgres en la misma VM

| | |
|---|---|
| **Costo** | **$0 real y sostenible** (Always Free, sin expiración) |
| **Setup** | **Alto.** Provisionar VM, instalar JDK 21, Postgres 16, systemd para el JAR, Nginx/Caddy como reverse proxy + TLS, firewall, security lists. Todo manual |
| **BE** | VM Ampere ARM: hasta **4 OCPU + 24 GB RAM** gratis. Sobradísimo. **No duerme** → scheduler semanal funciona perfecto |
| **DB** | Postgres 16 instalado en la misma VM (o Autonomous DB de Oracle). Persistente, sin límites de las DB serverless |
| **Limitaciones** | Capacidad ARM a veces "Out of Capacity" al crear. Mantenimiento del SO es tu responsabilidad (parches, backups, TLS). Requiere tarjeta para registrarse. Verificar que el JAR ARM corra (Java es multiplataforma, sin problema) |
| **Compatibilidad** | Total y con muchísimo margen de recursos. Es la opción "gratis de verdad para siempre" a cambio de operar tú la infra |

### Opción D — Koyeb (BE) + Neon (Postgres)

| | |
|---|---|
| **Costo** | $0 |
| **Setup** | Bajo (git-based). Similar a Render |
| **BE** | 1 web service, 512 MB / 0.1 vCPU |
| **Limitaciones** | RAM ajustada para JVM bajo carga. (Nota: Koyeb fue adquirido por Mistral AI en feb-2026 — su free tier podría cambiar de enfoque). Banda 1 GB |
| **Compatibilidad** | Buena, equivalente a Render. Menos probado para este caso |

---

## 5. Comparativa resumida del Backend

| Opción | Costo real 24/7 | Setup | Scheduler semanal | DB persistente | Riesgo principal |
|--------|-----------------|-------|-------------------|----------------|------------------|
| **A. Render + Neon** ⭐ | $0 | Bajo-medio | ⚠️ falla si duerme | ✅ (Neon) | Cold start + scheduler |
| **B. Railway** | ~$5–10/mes | **Muy bajo** | ✅ | ✅ | No es gratis 24/7 |
| **C. Oracle Always Free** | **$0** | **Alto** | ✅ | ✅ | Operación manual / capacidad ARM |
| **D. Koyeb + Neon** | $0 | Bajo | ⚠️ si duerme | ✅ (Neon) | RAM, futuro incierto |

---

## 6. Recomendación final

### Para arrancar YA con mínimo esfuerzo y $0 → **Opción A**

```
FE  → Vercel (o Cloudflare Pages)
BE  → Render (Web Service free)
DB  → Neon (Postgres serverless free)
```

**Por qué:**
- Es 100 % gratis y sostenible (Neon no expira como el Postgres de Render).
- Setup en una tarde, sin administrar servidores.
- Cubre de sobra "proyecto personal, bajo tráfico".

**Mitigaciones a aplicar:**
1. **Scheduler semanal:** como Render duerme, configurar un **cron-ping externo gratuito** (p. ej. cron-job.org) que golpee un endpoint el sábado ~5:55 AM para despertar el servicio antes del refresco; o exponer un endpoint manual de refresco y dispararlo tú.
2. **Memoria JVM:** añadir `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 -XX:+UseSerialGC` para vivir cómodo en 512 MB.
3. **Cold start:** asumible para uso personal (la 1ª petición tras dormir tarda ~1–1.5 min). Si molesta, un ping cada 14 min lo mantiene despierto (consume horas-instancia, vigilar el límite de 750 h/mes).
4. **CORS:** setear `STOCKVALUATOR_CORS_ALLOWED-ORIGINS=https://<tu-fe>.vercel.app`.
5. **FE:** setear `VITE_API_URL=https://<tu-be>.onrender.com/api/v1` en las env vars de build de Vercel.

### Si quieres $0 de verdad para siempre, sin sleep y con el scheduler garantizado → **Opción C (Oracle Always Free)**

A cambio de operar tú la VM (JDK, Postgres, Nginx+TLS, backups). Recursos enormes (24 GB RAM). Ideal como **paso 2** cuando el proyecto madure y el sleep/cold-start de Render moleste.

### Si en el futuro aceptas pagar ~$5–10/mes por la mejor DX → **Opción B (Railway)**

Todo en un lugar, scheduler garantizado, cero ops. La transición desde A es trivial.

---

## 7. Pasos siguientes sugeridos (para Opción A)

> **Orden recomendado para empezar mañana:** Paso 1 (código) → Paso 2 (Neon) → Paso 3 (Render) → Paso 4 (cron-ping) → Paso 5 (Vercel) → Paso 6 (smoke test).
> Los pasos 1 y 2 pueden hacerse en paralelo. Tiempo estimado total: **media jornada** si no hay sorpresas.
>
> **Notas de verificación contra el código real del BE (`C:\workspaces\intellij\stock-valuator`):**
> - El binding de CORS vive en `api-web/.../api/config/WebConfig.java` vía `@Value("${stockvaluator.cors.allowed-origins:...}")` — **ya funciona**, solo hay que pasar la env var con el nombre correcto (ver Paso 1.2, ojo al nombre sin guion).
> - **No existe `mvnw`** en el repo → el build en Render necesita Maven disponible. La vía más robusta es un **Dockerfile multi-stage** (Paso 1.3), no el buildpack.
> - `@EnableScheduling` está activo en `StockValuatorApplication`, pero **no hay ningún `@Scheduled` implementado** todavía: la propiedad `stockvaluator.scheduling.refresh-cron` del YAML no dispara nada. Por eso el refresco semanal se hace hoy con un **cron-ping externo al endpoint de ingestión** (Paso 4), no con el scheduler interno.

---

### Paso 1 — Preparar el BE para cloud (cambios de código y config)

Trabajar en una rama `feature/cloud-ready` desde `develop` (git flow del proyecto).

**1.1 — Externalizar la URL del datasource.**
Hoy `api-web/src/main/resources/application.yml` tiene la `url` fija a `localhost`. Cambiar a que sea overridable por env, manteniendo el default local para desarrollo:

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/stockvaluator}
    username: ${DB_USERNAME:sv_user}
    password: ${DB_PASSWORD:sv_pass}
```

> Spring Boot ya reconoce `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` como env vars estándar. Usar esos nombres en Render evita tener que tocar el YAML para username/password, pero dejar los `${...}` explícitos lo hace inequívoco.

**1.2 — Confirmar el nombre de la env var de CORS (¡detalle crítico!).**
`WebConfig.java` lee `stockvaluator.cors.allowed-origins`. Por el *relaxed binding* de Spring, la env var correspondiente es:

```
STOCKVALUATOR_CORS_ALLOWEDORIGINS
```

> ⚠️ **No** es `STOCKVALUATOR_CORS_ALLOWED-ORIGINS` (con guion) ni `..._ALLOWED_ORIGINS` (con guion bajo extra). Spring mapea `allowed-origins` → `ALLOWEDORIGINS`. Como el campo es `String[]`, el valor se separa por comas: `https://tu-fe.vercel.app`. No hace falta cambiar código aquí, solo setear bien la variable en Render (Paso 3).

**1.3 — Añadir un `Dockerfile` en la raíz del BE** (no existe hoy; recomendado sobre el buildpack porque no hay `mvnw` y el proyecto es multi-módulo):

```dockerfile
# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY valuation-engine/pom.xml valuation-engine/
COPY data-ingestion/pom.xml data-ingestion/
COPY api-web/pom.xml api-web/
RUN mvn -q -B dependency:go-offline
COPY . .
RUN mvn -q -B -pl api-web -am clean package -DskipTests

# ---- run stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/api-web/target/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

> El stage de build copia primero los `pom.xml` para cachear dependencias (capa Docker reutilizable). `-pl api-web -am` compila api-web **y** los módulos de los que depende (`valuation-engine`, `data-ingestion`). `JAVA_TOOL_OPTIONS` mantiene la JVM dentro de los 512 MB de Render.

**1.4 — Verificar local antes de subir:**

```powershell
# Compila el JAR como lo hará Render
mvn -pl api-web -am clean package -DskipTests
# Prueba el Docker build localmente (si tienes Docker)
docker build -t stock-valuator-be .
```

**1.5 — Commit + merge a `develop`** (`--no-ff`, según git flow). Render desplegará desde la rama que elijas (típicamente `main` o `develop`).

---

### Paso 2 — Crear la base de datos en Neon

1. Registrarse en [neon.tech](https://neon.tech) (login con GitHub, sin tarjeta).
2. **Create project** → nombre `stock-valuator`, región la más cercana a la región de Render (p. ej. ambos en US East) para minimizar latencia.
3. Neon crea un Postgres 16 con una rama `main` y una base `neondb` por defecto. Sirve tal cual.
4. En el dashboard, **Connection string** → copiar la cadena en formato **JDBC** o adaptarla. Neon da algo como:
   ```
   postgresql://user:pass@ep-xxx.us-east-2.aws.neon.tech/neondb?sslmode=require
   ```
   Para Spring/JDBC convertir a:
   ```
   jdbc:postgresql://ep-xxx.us-east-2.aws.neon.tech/neondb?sslmode=require
   ```
   y separar el usuario y password (van en `DB_USERNAME` / `DB_PASSWORD`).
5. **No** hace falta crear tablas: **Flyway corre las 9 migraciones (V1–V9) automáticamente** al arrancar el BE (`baseline-on-migrate: true`). Verificar luego que la tabla `flyway_schema_history` se haya poblado.

> **Nota sobre `ddl-auto: validate`:** como Flyway crea el schema antes de que Hibernate valide, el orden de arranque ya está bien resuelto en el proyecto. No cambiar a `update`.

---

### Paso 3 — Desplegar el BE en Render

1. Registrarse en [render.com](https://render.com) (sin tarjeta para free).
2. **New → Web Service** → conectar el repo `stock-valuator` de GitHub.
3. Configuración:
   - **Environment:** `Docker` (usa el `Dockerfile` del Paso 1.3).
   - **Branch:** la que despliegas (`main` o `develop`).
   - **Instance type:** `Free`.
   - **Health check path:** `/actuator/health` (ya expuesto; ver `management.endpoints` en el YAML).
4. **Environment variables** (en *Settings → Environment*):

   | Variable | Valor |
   |----------|-------|
   | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://ep-xxx...neon.tech/neondb?sslmode=require` |
   | `DB_USERNAME` | usuario de Neon |
   | `DB_PASSWORD` | password de Neon |
   | `FMP_API_KEY` | tu API key de Financial Modeling Prep |
   | `STOCKVALUATOR_CORS_ALLOWEDORIGINS` | `https://<tu-fe>.vercel.app` (rellenar tras Paso 5; al inicio puedes poner un placeholder) |

5. **Deploy.** El primer build tarda varios minutos (descarga dependencias Maven). Seguir los logs.
6. **Verificar arranque:**
   ```
   https://<tu-be>.onrender.com/actuator/health   → {"status":"UP"}
   https://<tu-be>.onrender.com/api/v1/info
   ```
   Revisar en los logs que Flyway aplicó V1–V9 sin error y que Hibernate validó el schema.

> **Memoria:** si ves `OutOfMemoryError` en arranque, confirmar que `JAVA_TOOL_OPTIONS` del Dockerfile se aplicó (aparece en los primeros logs de la JVM). Si persiste, bajar `MaxRAMPercentage` a `70`.

---

### Paso 4 — Configurar el cron-ping externo (refresco de datos)

Como (a) Render duerme tras 15 min y (b) no hay `@Scheduled` implementado, el refresco semanal se hace disparando el endpoint de ingestión desde un cron externo gratuito.

1. Crear cuenta en [cron-job.org](https://cron-job.org) (gratis).
2. **Crear un cronjob** que haga:
   ```
   POST https://<tu-be>.onrender.com/api/v1/ingest/AAPL
   ```
   - Repetir por cada ticker que quieras refrescar (o, mejor, **implementar más adelante** un endpoint `POST /api/v1/ingest/refresh-all` que recorra la watchlist; hoy `IngestionController` solo acepta un ticker).
   - **Schedule:** sábado a las **05:55 AM** (5 min antes de la hora histórica `0 0 6 * * SAT`), para dar margen al cold-start del wake-up.
3. **(Opcional) Keep-alive** para evitar cold starts en horario de uso: un segundo cronjob `GET /actuator/health` cada **14 minutos**.
   > ⚠️ Vigilar el límite de **750 horas-instancia/mes** de Render. Un keep-alive 24/7 consume ~720 h/mes — cabe justo, pero no dejes dos servicios free corriendo. Si solo te importa el refresco semanal, **no** pongas keep-alive y deja que duerma.

> **Mejora futura (no bloqueante):** implementar un `@Scheduled(cron = "${stockvaluator.scheduling.refresh-cron}")` real en el BE que recorra la watchlist. Pero eso **solo sirve si el proceso no duerme** — con Render free seguirías necesitando el ping para despertarlo. Por eso el cron externo es la solución correcta para esta arquitectura.

---

### Paso 5 — Desplegar el FE en Vercel

1. Registrarse en [vercel.com](https://vercel.com) con GitHub.
2. **Add New → Project** → importar `stock-valuator-ui`. Vercel autodetecta Vite (build `vite build`, output `dist`).
3. **Environment Variables** → añadir:
   ```
   VITE_API_URL = https://<tu-be>.onrender.com/api/v1
   ```
   > Es build-time: si la cambias luego, hay que **redeploy**. Confirmar que `src/api/client.ts` use `import.meta.env.VITE_API_URL` como `baseURL` en producción.
4. **Rewrite SPA** — crear `vercel.json` en la raíz del FE para que las rutas de `react-router` no den 404 en refresh:
   ```json
   {
     "rewrites": [{ "source": "/(.*)", "destination": "/index.html" }]
   }
   ```
5. **Deploy.** Vercel da una URL `https://<tu-fe>.vercel.app`.
6. **Cerrar el círculo de CORS:** volver a Render y poner esa URL exacta en `STOCKVALUATOR_CORS_ALLOWEDORIGINS`; Render redeploya el BE.

---

### Paso 6 — Smoke test end-to-end

1. Abrir `https://<tu-fe>.vercel.app`. La primera carga puede tardar ~1–1.5 min si el BE estaba dormido (cold start) — es esperado.
2. **Agregar `AAPL`** a la watchlist (ejercita `POST /watchlist/AAPL` → ingestión + valuación).
3. Verificar en el detalle: escenarios, breakdown DCF, heatmap de sensibilidad, y los campos de Fase 6 (QualityScore, MonteCarlo) si el BE los devuelve.
4. **Recalcular DCF** con un beta override → confirma el `POST /valuations/AAPL/calculate`.
5. Abrir DevTools → Network: confirmar que **no hay errores CORS** y que las llamadas van a `onrender.com`.
6. **Verificar persistencia:** recargar la página; los datos deben seguir ahí (confirma que Neon persiste y Flyway no recreó nada).
7. **(Día siguiente)** confirmar que el cron-job del sábado se ejecutó: revisar el historial en cron-job.org y los logs de ingestión en Render.

#### Checklist final

- [ ] BE responde `UP` en `/actuator/health`
- [ ] Flyway aplicó V1–V9 (tabla `flyway_schema_history` poblada en Neon)
- [ ] FE carga y consume el BE sin errores CORS
- [ ] Flujo completo AAPL: agregar → valuar → recalcular
- [ ] Datos persisten tras recargar
- [ ] Cron-ping de refresco creado y verificado
- [ ] `VITE_API_URL` y `STOCKVALUATOR_CORS_ALLOWEDORIGINS` apuntan a las URLs reales de prod

---

## Fuentes

- [Platforms with a real free tier for developers in 2026 — Render](https://render.com/articles/platforms-with-a-real-free-tier-for-developers-in-2026)
- [Free PostgreSQL instances now expire after 30 days (Render Changelog)](https://render.com/changelog/free-postgresql-instances-now-expire-after-30-days-previously-90)
- [Deploy for Free — Render Docs](https://render.com/docs/free)
- [Free Hosting for Spring Boot 2026 — BSWEN](https://docs.bswen.com/blog/2026-02-28-springboot-free-hosting/)
- [Neon vs Supabase Free Tier — 2026](https://agentdeals.dev/neon-vs-supabase)
- [Railway vs Render 2026 — The Software Scout](https://thesoftwarescout.com/railway-vs-render-2026-best-platform-for-deploying-apps/)
- [Oracle Cloud Free Tier 2026 — cloudpricecheck](https://cloudpricecheck.com/free-tier/oracle)
- [Deploy a Spring Boot App — Koyeb Docs](https://www.koyeb.com/docs/deploy/spring-boot)
