# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY platform-common/pom.xml platform-common/pom.xml
COPY platform-security/pom.xml platform-security/pom.xml
COPY platform-system/pom.xml platform-system/pom.xml
COPY platform-business/pom.xml platform-business/pom.xml
COPY platform-exchange/pom.xml platform-exchange/pom.xml
COPY platform-statistics/pom.xml platform-statistics/pom.xml
COPY platform-file/pom.xml platform-file/pom.xml
COPY platform-boot/pom.xml platform-boot/pom.xml
RUN mvn -B -ntp -DskipTests dependency:go-offline

COPY . .
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
ENV JAVA_OPTS=""
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/platform-boot/target/teacher-cert-platform.jar /app/teacher-cert-platform.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/teacher-cert-platform.jar"]
