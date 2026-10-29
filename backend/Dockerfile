FROM maven:3.9.9-eclipse-temurin-21 AS source
WORKDIR /workspace
COPY pom.xml settings.xml ./
COPY src ./src
FROM source AS check
CMD ["mvn", "-B", "-s", "settings.xml", "verify"]
FROM source AS build
RUN --mount=type=cache,target=/root/.m2 mvn -B -s settings.xml -DskipTests package
FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --system --uid 10001 studio && mkdir -p /app/data && chown studio /app/data
WORKDIR /app
COPY --from=build /workspace/target/studio-0.1.0-SNAPSHOT.jar app.jar
USER studio
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
