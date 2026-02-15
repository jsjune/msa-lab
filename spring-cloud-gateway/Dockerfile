FROM gradle:jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle :spring-cloud-gateway:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/spring-cloud-gateway/build/libs/*.jar app.jar
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]
