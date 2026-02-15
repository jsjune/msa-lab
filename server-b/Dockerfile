FROM gradle:jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle :server-b:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/server-b/build/libs/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
