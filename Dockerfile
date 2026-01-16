# Multi-stage build for optimized image

# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# Make mvnw executable and download dependencies
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and build
COPY src src
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime - optimized for large file handling
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install useful tools
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Copy the built jar
COPY --from=builder /app/target/*.jar app.jar

# Create uploads directory with proper permissions
RUN mkdir -p /app/uploads && chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM settings optimized for large file uploads:
# - Xmx2g: Max heap size (adjust based on your server)
# - XX:+UseG1GC: G1 garbage collector for better large allocation handling
# - XX:MaxDirectMemorySize: For NIO operations
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxDirectMemorySize=512m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
