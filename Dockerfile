FROM gradle:6.8.0-jdk8 as builder

COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build
RUN gradle shadowJar

FROM arm64v8/openjdk:8-jre-slim

RUN mkdir /app
RUN mkdir -p /app/var/output

COPY --from=builder /home/gradle/src/build/libs/*.jar /app
COPY config/app/dev.properties /app
