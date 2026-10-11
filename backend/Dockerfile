# ==============================================================================
# PKampus backend - production image for Oracle A1 (ARM Ampere, Always Free)
# Multi-stage: Gradle build (JDK 21) -> minimal JRE 21 runtime.
# eclipse-temurin:21-jre-alpine publishes linux/arm64, so the same
# Dockerfile works on A1 (ARM) and on x86 dev machines.
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: build executable JAR
# ------------------------------------------------------------------------------
FROM gradle:8.10-jdk21-alpine AS builder
WORKDIR /app

# Dependency layer first (better Docker cache)
COPY gradle gradle
COPY gradlew settings.gradle.kts gradle.properties build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# Sources + Flyway migrations (V1..V12, part of the JAR)
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# ------------------------------------------------------------------------------
# Stage 2: runtime (prod profile, Supabase + MinIO on VM + Resend SMTP)
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S pkampus && adduser -S pkampus -G pkampus \
    && apk add --no-cache wget

COPY --from=builder /app/build/libs/*.jar app.jar
RUN chown pkampus:pkampus app.jar
USER pkampus

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS=""

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
