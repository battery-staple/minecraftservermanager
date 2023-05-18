FROM openjdk:8

COPY ./build/libs/minecraftservermanager-0.0.1-all.jar /bin/runner/run.jar
WORKDIR /bin/runner

CMD ["java", "-jar", "run.jar"]