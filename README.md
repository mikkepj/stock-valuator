# Stock Valuator

DCF-based intrinsic value calculator for stocks. Automated analysis using Financial Modeling Prep API data.

## Tech Stack

- **Java 21** + **Spring Boot 3.3**
- **Maven** multi-module (3 modules)
- **PostgreSQL 16**
- **FMP API** for financial data
- **Flyway** for database migrations
- **Resilience4j** for API resilience
- **Caffeine** for caching
- **springdoc-openapi** for Swagger UI

## Project Structure

```
stock-valuator/
├── pom.xml                     # Parent POM
├── valuation-engine/           # Pure Java DCF logic (no framework)
├── data-ingestion/             # FMP API client + persistence
└── api-web/                    # REST API + Flyway + App entry point
```

## Prerequisites

- Java 21 (JDK)
- Maven 3.9+
- PostgreSQL 16 running on localhost:5432

## Setup

### 1. Create the database

Open pgAdmin or `psql` and run:

```sql
CREATE USER sv_user WITH PASSWORD 'sv_pass';
CREATE DATABASE stockvaluator OWNER sv_user;
GRANT ALL PRIVILEGES ON DATABASE stockvaluator TO sv_user;
```

### 2. Set environment variables (optional)

If you use different credentials, set these:

```
DB_USERNAME=your_user
DB_PASSWORD=your_password
FMP_API_KEY=your_fmp_key
```

### 3. Build and run

```bash
# From the project root
mvn clean install

# Run the application
cd api-web
mvn spring-boot:run
```

### 4. Verify

- Health check: http://localhost:8080/actuator/health
- API info: http://localhost:8080/api/v1/info
- Swagger UI: http://localhost:8080/swagger-ui.html

## Modules

### valuation-engine
Pure Java library. Zero Spring dependencies. Contains DCF calculation logic: FCF projection, WACC, terminal value, sensitivity analysis. 100% unit-testable.

### data-ingestion
Connects to FMP API via `RestClient`. Deserializes financial statements into typed Java records. Persists normalized data to PostgreSQL. Handles rate limiting and retries with Resilience4j.

### api-web
Spring Boot application entry point. REST controllers, Flyway migrations, caching, Swagger/OpenAPI documentation. Depends on both other modules.

## License

Private — NuvixTech
