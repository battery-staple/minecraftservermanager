# syntax=docker/dockerfile:1

ARG JRE_VERSION=8
ARG JDK_VERSION=21
ARG GRADLE_VERSION=8.8

FROM gradle:$GRADLE_VERSION-jdk$JDK_VERSION-graal AS build_app

WORKDIR /app

COPY --chown=gradle:gradle build.gradle.kts settings.gradle.kts gradle.properties ./
COPY --chown=gradle:gradle src src

COPY --chown=gradle:gradle shared/build.gradle.kts shared/gradle.properties shared/
COPY --chown=gradle:gradle shared/src shared/src

COPY --chown=gradle:gradle monitor/build.gradle.kts monitor/gradle.properties monitor/
COPY --chown=gradle:gradle monitor/src monitor/src

RUN --mount=type=cache,target=.gradle \
    gradle :monitor:nativeCompile --no-daemon

FROM eclipse-temurin:$JRE_VERSION-jre

COPY --from=build_app /app/monitor/build/native/nativeCompile/monitor /bin/runner/monitor

WORKDIR /bin/runner

ENV minSpaceMB=256
ENV maxSpaceMB=1024
ENV name=test

VOLUME data /monitor

CMD ["./monitor"]