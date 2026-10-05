# ---- build ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

# Cache de dependencias
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:25-jre AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r photoizer \
    && useradd -r -g photoizer -d /app -s /usr/sbin/nologin photoizer

WORKDIR /app
COPY --from=build /workspace/target/*.jar /app/app.jar
RUN mkdir -p /app/uploads /app/logs && chown -R photoizer:photoizer /app

USER photoizer
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75" \
    TZ=America/Sao_Paulo \
    UPLOAD_DIR=/app/uploads \
    LOG_PATH=/app/logs

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
