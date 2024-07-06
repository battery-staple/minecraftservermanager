# syntax=docker/dockerfile:1

ARG JDK_VERSION=8
ARG GRADLE_VERSION=8.8

FROM gradle:$GRADLE_VERSION-jdk$JDK_VERSION AS build_app

WORKDIR /monitor

COPY --chown=gradle:gradle build.gradle.kts settings.gradle.kts gradle.properties ./
COPY --chown=gradle:gradle src src

RUN --mount=type=cache,target=.gradle \
    gradle shadowJar --no-daemon

FROM eclipse-temurin:$JDK_VERSION-jre

COPY --from=build_app /monitor/build/libs/monitor-all.jar /bin/runner/run.jar
COPY data/1.8.9---RELEASE.jar /monitor/minecraftserver.jar

WORKDIR /bin/runner

ENV minSpaceMB=256
ENV maxSpaceMB=1024

CMD ["java", "-ea", "-jar", "run.jar"]