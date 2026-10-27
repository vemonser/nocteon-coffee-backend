# ============================================================================
# Build stage
# ============================================================================
FROM maven:3.9-eclipse-temurin-25 AS build

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
FROM eclipse-temurin:25-jre

WORKDIR /app

# Copy the built jar
COPY --from=build /build/target/*.jar /app/app.jar

# Run as a non-root user; the app only writes to stdout and /tmp.
RUN useradd --system --uid 10001 --create-home appuser \
    && chown -R appuser:appuser /app
USER appuser

# Expose the application port
EXPOSE 8080

# Health check.
# Probe with a real GET. `wget --spider` issues a HEAD request, and
# SecurityConfig permits the public read paths for HttpMethod.GET only, so HEAD
# falls through to anyRequest().authenticated() -> 401 and the container was
# marked unhealthy on every probe. Temurin 25 JRE also no longer ships wget, so
# use bash's /dev/tcp and read the status line directly.
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /api/settings HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && head -1 <&3 | grep -q '200'"]

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
