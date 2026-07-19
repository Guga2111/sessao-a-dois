# syntax=docker/dockerfile:1

# ---- Stage 1: build ----
# JDK 21 image; uses the Maven wrapper (./mvnw) present in api/ to package the app.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy only the files needed to resolve dependencies first, so this layer is
# cached and re-used while application source (src/) changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Now copy the source and build the runnable jar (tests skipped for image build).
COPY src/ src/
RUN ./mvnw -B -q -DskipTests package

# ---- Stage 2: runtime ----
# Lean JRE 21 base; carries only the built jar, not the JDK or Maven.
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Copy only the produced Spring Boot jar from the build stage.
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
