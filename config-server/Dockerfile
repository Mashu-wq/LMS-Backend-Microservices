# ============================================================
# Multi-stage build for LMS Config Server
# Stage 1: Extract layers from the layered JAR
# Stage 2: Final minimal runtime image
# ============================================================

# Stage 1 — Extract layers
FROM eclipse-temurin:21-jre-alpine AS builder
WORKDIR /application
COPY target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 2 — Runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /application

# Security: run as non-root user
RUN addgroup -S lms && adduser -S lms -G lms
USER lms

# Copy layers in order (least-changed first for better layer caching)
COPY --from=builder /application/dependencies/ ./
COPY --from=builder /application/spring-boot-loader/ ./
COPY --from=builder /application/snapshot-dependencies/ ./
COPY --from=builder /application/application/ ./

# Health check using actuator
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -q --spider http://localhost:8888/actuator/health || exit 1

EXPOSE 8888

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+OptimizeStringConcat", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "org.springframework.boot.loader.launch.JarLauncher"]
