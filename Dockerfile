# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-17@sha256:4015718012bbf1113ec6cfae2b950be328d90265ceb60f92b26c3ea7c4d14ee8 AS build
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

FROM eclipse-temurin:17-jre-jammy@sha256:89e68b9bb83713510b63e2059a415792a7fc77e14b739a7d7ede97f6d9ca2c38
WORKDIR /app
ENV JAVA_OPTS=""
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 teacher-cert \
    && useradd --system --uid 10001 --gid 10001 --home-dir /app --shell /usr/sbin/nologin teacher-cert \
    && mkdir -p /var/lib/teacher-cert/video-probe \
    && chown -R 10001:10001 /app /var/lib/teacher-cert
COPY --from=build --chown=10001:10001 /workspace/platform-boot/target/teacher-cert-platform.jar /app/teacher-cert-platform.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/teacher-cert-platform.jar"]
