# Stage 1: Build the artifact
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /workspace

# Copy dependencies manifest first to leverage Docker layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build executable fat JAR
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Non-root user for security best practices
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /workspace/target/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
