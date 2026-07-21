FROM eclipse-temurin:21-jdk-slim
WORKDIR /app
COPY target/*.jar TelegramClone.jar
ENTRYPOINT ["java", "-jar", "TelegramClone.jar"]
