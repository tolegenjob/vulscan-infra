# Образ собирается из готового boot-jar: сначала `./gradlew bootJar` (CI делает это перед `docker build`).
# Слушает только внутреннюю docker-сеть lab-net — порт на хост по умолчанию не публикуется (см. N-1, infra/).
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
# Версия в имени jar должна совпадать с `version` в build.gradle.kts.
COPY build/libs/lab-app-0.1.0.jar /app/app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
  CMD ["wget", "-qO-", "http://localhost:8080/actuator/health"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
