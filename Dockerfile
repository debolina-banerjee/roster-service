FROM eclipse-temurin:21-jdk

# Install required font libraries
RUN apt-get update && apt-get install -y \
    fontconfig \
    libfreetype6 \
    && rm -rf /var/lib/apt/lists/*

# Copy jar file
COPY target/roster-service-0.0.1-SNAPSHOT.jar app.jar

# Run application
ENTRYPOINT ["java","-Djava.awt.headless=true","-jar","/app.jar"]