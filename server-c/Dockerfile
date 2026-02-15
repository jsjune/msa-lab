FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# 이미 빌드된 jar 파일만 복사
COPY server-c/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]