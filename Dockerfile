# ==============================================================================
# Multi-Stage Dockerfile for KhmerMart Backend
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build Application
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and POM first for layer caching
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Copy source code and package executable JAR
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 ./mvnw clean package -DskipTests -B

# ------------------------------------------------------------------------------
# Stage 2: Minimal Production Runtime
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="KhmerMart Team <dev@khmermart.local>"
LABEL description="KhmerMart E-Commerce Backend Service"

WORKDIR /app

# Create non-privileged service user and uploads directory
RUN addgroup -S khmermart && adduser -S khmermart -G khmermart && \
    mkdir -p /app/uploads && chown -R khmermart:khmermart /app

USER khmermart:khmermart

# Copy executable jar from builder stage
COPY --from=builder --chown=khmermart:khmermart /build/target/khmermart-backend-1.0.0.jar app.jar

EXPOSE 4000

ENV PORT=4000 \
    SPRING_PROFILES_ACTIVE=dev \
    JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:4000/api/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
