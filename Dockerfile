# syntax=docker/dockerfile:1.3
ARG BASE_IMAGE=eclipse-temurin:24-jdk

FROM ${BASE_IMAGE} AS build
WORKDIR /app
# coping the Maven wrapper and configuration files
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
RUN sed -i 's/\r$//' mvnw .mvn/wrapper/maven-wrapper.properties && chmod +x mvnw
# Use the Maven wrapper to download dependencies and prepare the project
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw dependency:go-offline -B
# coping the source code
COPY src ./src
# Build the project using the Maven wrapper, skipping tests
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw clean package -DskipTests -B

FROM ${BASE_IMAGE} AS runtime
WORKDIR /app

RUN groupadd -r appgroup && useradd -r -g appgroup -d /app -s /sbin/nologin appuser \
    && mkdir -p /app/storage \
    && chown -R appuser:appgroup /app \
    && chmod 775 /app/storage

COPY --from=build --chown=appuser:appgroup /app/target/*.jar app.jar

USER appuser
ENTRYPOINT ["java","-jar","app.jar"]
