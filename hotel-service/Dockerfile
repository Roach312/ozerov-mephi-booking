# Контекст сборки - корень репозитория (см. docker-compose.yml).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY eureka-server/pom.xml eureka-server/
COPY api-gateway/pom.xml api-gateway/
COPY hotel-service/pom.xml hotel-service/
COPY booking-service/pom.xml booking-service/
COPY hotel-service/src hotel-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q -pl hotel-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/hotel-service/target/hotel-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
