FROM eclipse-temurin:21-jre-alpine AS builder
WORKDIR /application
COPY target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:21-jre-alpine
WORKDIR /application

RUN addgroup -S lms && adduser -S lms -G lms
USER lms

COPY --from=builder /application/dependencies/ ./
COPY --from=builder /application/spring-boot-loader/ ./
COPY --from=builder /application/snapshot-dependencies/ ./
COPY --from=builder /application/application/ ./

HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -q --spider http://localhost:8761/actuator/health || exit 1

EXPOSE 8761

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "org.springframework.boot.loader.launch.JarLauncher"]
