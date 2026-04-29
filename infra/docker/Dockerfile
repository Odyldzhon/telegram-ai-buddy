# syntax=docker/dockerfile:1.7

# ─── Stage 1: build the fat-JAR with Maven ──────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache dependencies first for faster incremental builds
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline

# Now copy sources and build
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests package

# ─── Stage 2: minimal runtime image ─────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as non-root
RUN useradd --system --uid 1001 --create-home appuser
USER appuser

COPY --from=build /workspace/target/*.jar /app/app.jar

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]


