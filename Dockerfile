# =============================================================================
# Aşama 1: Derleme (Build)
# =============================================================================
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN chmod +x mvnw && \
    ./mvnw dependency:go-offline -B

COPY frontend frontend
COPY src src

RUN ./mvnw package -Pproduction -DskipTests && \
    rm -rf /root/.m2 && \
    rm -rf /app/src /app/frontend /app/target/*.original

# =============================================================================
# Aşama 2: Çalıştırma (Runtime)
# =============================================================================
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl postgresql-client && \
    rm -rf /var/lib/apt/lists/* /var/cache/apt/* && \
    groupadd -r appuser && \
    useradd -r -g appuser appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

USER appuser

ENTRYPOINT ["java", \
    "-Duser.timezone=Europe/Istanbul", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]
