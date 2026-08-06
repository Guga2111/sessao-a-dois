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

# Run as a non-privileged user with FIXED uid/gid: the security audit trail is a
# bind mount from the VPS (/var/lib/sessao-a-dois/security-audit-logs), and its
# ownership on the host has to match this uid. Letting adduser pick the id would
# make the mount permission depend on the base image. See docs/DEPLOY.md 3.4.
RUN addgroup -g 10001 -S app && adduser -u 10001 -S app -G app

# Copy only the produced Spring Boot jar from the build stage, owned by app.
COPY --from=build --chown=app:app /app/target/*.jar app.jar

USER app

# wget comes from BusyBox in the alpine base image - no need to install curl.
# Port 8080 is the INTERNAL port; 8085 in docker-compose-prod.yml is only the
# host mapping and would make the container permanently unhealthy here.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget -qO- http://localhost:8080/api/health || exit 1

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
