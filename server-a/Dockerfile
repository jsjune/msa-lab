FROM gradle:jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle :server-a:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/server-a/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
