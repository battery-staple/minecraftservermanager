# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jre

COPY ./build/libs/minecraftservermanager-0.0.1-all.jar /bin/runner/run.jar
WORKDIR /bin/runner

CMD ["java", "-ea", "-jar", "run.jar"]