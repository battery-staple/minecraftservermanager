# syntax=docker/dockerfile:1

ARG NODE_VERSION=20.3.1
ARG JDK_VERSION=8
ARG GRADLE_VERSION=8.8

FROM node:${NODE_VERSION}-alpine AS build_frontend

WORKDIR frontend

COPY frontend/src src
COPY frontend/public public
COPY frontend/package.json frontend/package-lock.json frontend/tsconfig.json ./

# Download dependencies as a separate step to take advantage of Docker's caching.
# Leverage a cache mount to /root/.npm to speed up subsequent builds.
RUN --mount=type=cache,target=/root/.npm  \
    npm ci --omit=dev

RUN npm run build

FROM gradle:$GRADLE_VERSION-jdk8 AS build_app

WORKDIR /app

COPY --chown=gradle:gradle build.gradle.kts settings.gradle.kts gradle.properties ./
COPY --chown=gradle:gradle src src
COPY --chown=gradle:gradle --from=build_frontend frontend/build/. src/main/resources/static/react/

RUN --mount=type=cache,target=.gradle \
    gradle shadowJar --no-daemon

FROM eclipse-temurin:${JDK_VERSION}

COPY --from=build_app /app/build/libs/minecraftservermanager-0.0.1-all.jar /bin/runner/run.jar
WORKDIR /bin/runner

CMD ["java", "-ea", "-jar", "run.jar"]