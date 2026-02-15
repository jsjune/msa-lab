FROM gradle:jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle :server-c:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/server-c/build/libs/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
