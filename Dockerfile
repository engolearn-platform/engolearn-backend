# Build the executable Spring Boot JAR with Java 21.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

# Run with a slim JRE image; Render supplies PORT at runtime.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN groupadd --system --gid 10001 spring \
    && useradd --system --uid 10001 --gid spring --home-dir /app --shell /usr/sbin/nologin spring

COPY --from=build --chown=spring:spring /workspace/target/engolearn-0.0.1-SNAPSHOT.jar /app/app.jar

USER spring
EXPOSE 10000

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65.0", "-jar", "/app/app.jar"]
