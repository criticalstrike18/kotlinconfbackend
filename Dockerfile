# Use a JDK 17 base image
FROM openjdk:17-jdk-slim

# Set the working directory inside the container
WORKDIR /app

# Copy Gradle wrapper files (for consistent Gradle version)
COPY gradlew gradlew.bat ./
COPY gradle/ /app/gradle

# Copy project files
COPY . /app

# Set executable permission for gradlew
RUN chmod +x gradlew

## Pre-download dependencies and build the project (this speeds up subsequent builds)
#RUN ./gradlew :backend:dependencies

# Expose the backend port (adjust if your backend uses a different port)
EXPOSE 8080

# Command to run when the container starts
CMD ["./gradlew", ":backend:run", "--stacktrace"]