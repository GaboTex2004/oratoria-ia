FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 oratoria \
    && useradd --system --uid 10001 --gid 10001 --home-dir /app --shell /usr/sbin/nologin oratoria
WORKDIR /app
COPY --from=build /workspace/target/*.jar /app/application.jar
RUN mkdir -p /app/data/audio && chown -R 10001:10001 /app
USER 10001:10001
EXPOSE 8081
STOPSIGNAL SIGTERM
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
