# ── Stage 1: Build ───────────────────────────────────────────────────────────
# We use the full Maven image so it has everything needed to compile Java code.
# eclipse-temurin:17 is the community build of OpenJDK 17 from Adoptium.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy the dependency manifest first. Docker caches each instruction as a
# layer. By copying pom.xml before the source code, Maven only re-downloads
# dependencies when pom.xml changes, not on every code change.
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

# Now copy source code and compile. -DskipTests skips unit tests here because
# CI runs them separately and we do not want the build image to need a test DB.
COPY src ./src
RUN mvn -q -DskipTests package

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
# We switch to a much smaller JRE-only image. The build tools (Maven, javac)
# are NOT included, which shrinks the final image and reduces the attack surface.
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# Install curl so the HEALTHCHECK command below can reach the Spring Actuator
# endpoint. We clean the apt cache in the same RUN step to keep the layer small.
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Security best practice: never run application processes as root inside a
# container. We create a dedicated system user and group with no home directory
# and no login shell, then switch to that user for all subsequent instructions.
RUN groupadd --system appgroup && \
    useradd --system --gid appgroup --no-create-home --shell /usr/sbin/nologin appuser

# Copy the compiled JAR from the build stage. --chown sets ownership to our
# non-root user so the process can read its own file.
COPY --from=build --chown=appuser:appgroup /app/target/*.jar app.jar

# Drop root privileges permanently.
USER appuser

# Document which port the application listens on. This is metadata only;
# the actual port binding happens in docker-compose.yml.
EXPOSE 8080

# HEALTHCHECK tells Docker how to verify the container is healthy.
# --interval=15s  : check every 15 seconds
# --timeout=5s    : fail the check if it takes more than 5 seconds
# --start-period=60s : give Spring Boot 60 seconds to start before checks begin
# --retries=5     : mark unhealthy after 5 consecutive failures
# Spring Boot Actuator exposes /actuator/health automatically.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1

# -XX:+UseContainerSupport  : makes the JVM read CPU and memory limits set by
#                             Docker/Kubernetes instead of the full host values.
# -XX:MaxRAMPercentage=75.0 : use at most 75 % of the container's memory limit
#                             for the JVM heap. Leaves 25 % for OS and threads.
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
