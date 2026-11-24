# Build: JDK 21. Run: JRE 21 as a non-root user. Tags are pinned to exact versions (checked on Docker Hub, 24 Nov 2026).
FROM eclipse-temurin:21.0.13_9-jdk-jammy AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY src src
# Tests run in CI and in scripts/verify.sh (they need Docker for Testcontainers); the image build only packages.
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21.0.13_9-jre-jammy
RUN useradd --system --uid 10001 --no-create-home taskflow
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
USER 10001
EXPOSE 8080
# UTC: pgjdbc sends the JVM zone to PostgreSQL 17, which rejects some legacy zone aliases.
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]
