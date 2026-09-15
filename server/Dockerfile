# server/Dockerfile

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy only the dependency descriptor first — this layer is cached
# and only invalidated when pom.xml itself changes, not on every code edit.
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Now copy the rest of the source and build.
COPY src ./src
ARG APP_VERSION=dev
RUN mvn -B -DskipTests -Drevision=${APP_VERSION} package && \
    cp target/*.jar target/app.jar

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Run as a non-root user — good hygiene even in a course sandbox.
RUN addgroup -S app && adduser -S app -G app

# Create the data dir the app needs and hand it to the app user
RUN mkdir -p /app/data && chown -R app:app /app

USER app

COPY --from=build /build/target/app.jar ./app.jar

ARG APP_PORT=8080
ENV APP_PORT=${APP_PORT}
EXPOSE ${APP_PORT}

ENTRYPOINT ["java", "-jar", "/app/app.jar"]