FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY target/*.jar TelegramClone.jar
ENTRYPOINT ["java", "-jar", "TelegramClone.jar"]
