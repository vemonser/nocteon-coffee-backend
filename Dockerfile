# ============================================================================
# Build stage
# ============================================================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Cache dependencies first
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests -q

# ============================================================================
# Runtime stage
# ============================================================================
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the built jar
COPY --from=build /build/target/*.jar /app/app.jar

# Expose the application port
EXPOSE 8080

# Health check
# Probe with a real GET. `wget --spider` issues a HEAD request, and
# SecurityConfig permits the public read paths for HttpMethod.GET only, so HEAD
# falls through to anyRequest().authenticated() -> 401 and the container was
# marked unhealthy on every probe. `-O /dev/null` discards the response body.
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 -O /dev/null http://localhost:8080/api/settings || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
