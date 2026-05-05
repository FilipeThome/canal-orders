# syntax=docker/dockerfile:1.6

# ==============================================================================
# Stage 1 — Build the fat jar with Gradle.
#
# We use the official Gradle image with JDK 21 (Alpine variant) so the build
# step itself stays small. The Gradle cache is mounted as a BuildKit cache
# mount so repeat builds don't re-download dependencies every time.
# ==============================================================================
FROM gradle:8.7-jdk21-alpine AS build
WORKDIR /workspace

# Copy only build files first to maximise layer-caching for dependencies.
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle --no-daemon dependencies > /dev/null 2>&1 || true

COPY src ./src
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle --no-daemon bootJar -x test

# ==============================================================================
# Stage 2 — Runtime image.
#
# eclipse-temurin:21-jre-alpine is ~75MB compressed; the fat jar is ~25MB;
# total runtime image lands around ~100MB, which is roughly 5x smaller than
# a default `eclipse-temurin:21` (JDK) image.
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# Run as a non-root user — best practice on any production image.
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app

COPY --from=build /workspace/build/libs/app.jar /app/app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8080

# JVM tuning:
#   * MaxRAMPercentage lets the container manager (k8s/Docker) cap memory.
#   * UseContainerSupport is on by default in JDK 21.
#   * ExitOnOutOfMemoryError ensures the orchestrator restarts on OOM.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
