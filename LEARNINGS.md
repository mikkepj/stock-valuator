# Learnings Journal

Documenting new technologies, patterns, and insights learned during development.

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
