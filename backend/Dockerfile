FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/poker-night-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
