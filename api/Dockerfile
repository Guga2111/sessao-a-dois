# syntax=docker/dockerfile:1

# ---- Stage 1: build ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy only the files needed to resolve dependencies first, so this layer is
# cached and re-used while application source (src/) changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Now copy the source and build the runnable jar (tests skipped for image build).
COPY src/ src/
RUN ./mvnw -B -q -Dmaven.test.skip=true package

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Copy only the produced Spring Boot jar from the build stage.
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
