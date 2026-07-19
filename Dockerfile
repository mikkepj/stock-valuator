# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copiar primero los pom.xml para cachear la descarga de dependencias
# (capa Docker reutilizable mientras los pom no cambien)
COPY pom.xml .
COPY valuation-engine/pom.xml valuation-engine/
COPY data-ingestion/pom.xml data-ingestion/
COPY api-web/pom.xml api-web/
RUN mvn -q -B dependency:go-offline

# Copiar el código y compilar solo api-web + sus módulos internos
COPY . .
RUN mvn -q -B -pl api-web -am clean package -DskipTests

# ---- run stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/api-web/target/*.jar app.jar

# Mantener la JVM dentro de los 512 MB del plan free de Render
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
