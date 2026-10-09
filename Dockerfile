FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace
COPY gradlew gradle.properties settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew
RUN --mount=type=secret,id=github_actor \
    --mount=type=secret,id=github_token \
    export GITHUB_ACTOR="$(cat /run/secrets/github_actor)" GITHUB_TOKEN="$(cat /run/secrets/github_token)" && \
    ./gradlew --no-daemon dependencies

COPY src src
RUN --mount=type=secret,id=github_actor \
    --mount=type=secret,id=github_token \
    export GITHUB_ACTOR="$(cat /run/secrets/github_actor)" GITHUB_TOKEN="$(cat /run/secrets/github_token)" && \
    ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre

RUN useradd --system --uid 10001 --create-home appuser
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/routine-service.jar
RUN chown -R appuser:appuser /app
USER appuser

ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=64m -XX:MaxDirectMemorySize=32m -Xss512k -XX:ActiveProcessorCount=1 -XX:+ExitOnOutOfMemoryError"
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/routine-service.jar"]
