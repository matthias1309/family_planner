FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 planner && mkdir -p /app/data /app/certs && chown -R planner /app
WORKDIR /app
COPY app/build/libs/app-all.jar /app/app.jar
ARG VERSION=dev
ENV FP_VERSION=$VERSION TZ=Europe/Berlin FP_DB_PATH=/app/data/family-planner.db
USER planner
EXPOSE 8095
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD curl -fsS http://127.0.0.1:8096/health || exit 1
ENTRYPOINT ["java","-Xmx256m","-XX:+UseSerialGC","-XX:+ExitOnOutOfMemoryError","-jar","/app/app.jar"]
