FROM openjdk:21-jdk-slim
WORKDIR /app
COPY target/TelegramClone-0.0.1-SNAPSHOT.jar /app/TelegramClone.jar
ENTRYPOINT ["java", "-jar", "TelegramClone.jar"]
