# Multi-stage build — build context is backend/ (the Maven parent).
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY . .
RUN mvn -B -q -pl metrics-service -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/metrics-service/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java","-jar","app.jar"]
