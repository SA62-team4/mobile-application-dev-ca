# Build stage.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cache dependencies before copying source.
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

# Compile app; tests run in CI.
COPY src ./src
RUN mvn -q -DskipTests package

# Runtime stage.
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# curl is used by HEALTHCHECK.
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Run as a non-root user.
RUN groupadd --system appgroup && \
    useradd --system --gid appgroup --no-create-home --shell /usr/sbin/nologin appuser

# Copy the compiled JAR.
COPY --from=build --chown=appuser:appgroup /app/target/*.jar app.jar

# Drop root privileges permanently.
USER appuser

# Compose binds the port.
EXPOSE 8080

# Spring Boot health probe.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1

# Respect container limits and reserve memory for native threads.
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
