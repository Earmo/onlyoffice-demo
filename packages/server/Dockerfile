FROM eclipse-temurin:21
WORKDIR /app

ENV TZ=Asia/Shanghai

COPY onlyoffice-integration-service/target/onlyoffice-integration-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
