#A sample Dockerfile for the project

#We will be using multi stage build so that the docker image is just the JAR and no dependencies or code files
# Build the Project
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy the pom.xml first to cache dependencies (speeds up future builds)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the actual source code and build the JAR
COPY src ./src
RUN mvn clean package -DskipTests



#  Using 'jammy' (Ubuntu-based) instead of 'alpine' because PDFBox
# sometimes relies on standard OS font libraries that Alpine strips out!
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy the compiled JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose the standard Spring Boot port
EXPOSE 8080

# Start the application
ENTRYPOINT ["java", "-jar", "app.jar"]