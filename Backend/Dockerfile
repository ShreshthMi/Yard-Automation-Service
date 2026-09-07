# Stage 1: Build
# No Maven wrapper is checked in, and all dependencies (Spring Boot, POI) come
# from Maven Central, so a plain maven image is enough - no private-registry
# auth needed here (unlike the frontend's internal Nexus/GitLab setup).
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy the POM first and prime the local repo so source-only changes don't
# invalidate the dependency-download layer.
COPY Backend/YardAutomationService/pom.xml ./
RUN mvn -B dependency:go-offline

COPY Backend/YardAutomationService/src ./src
RUN mvn -B clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/YardAutomationService.jar app.jar

# app.protocol-definition-path defaults to "protocol_definition.xlsx", resolved
# against the working directory, so the sheet has to live next to the jar.
COPY Backend/protocol_definition.xlsx ./protocol_definition.xlsx

# 8080: Spring Boot HTTP/WebSocket API. 9000/udp: FSE protocol packet ingest
# (app.udp.port).
EXPOSE 8080
EXPOSE 9000/udp
ENTRYPOINT ["java", "-jar", "app.jar"]
