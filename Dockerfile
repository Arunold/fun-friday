# ==========================================
# Stage 1: Build Frontend (Angular)
# ==========================================
FROM node:20-alpine AS frontend-build

WORKDIR /app/frontend

# Copy package files first for better caching
COPY frontend/package*.json ./

# Install dependencies
RUN npm ci

# Copy source files
COPY frontend/ ./

# Build for production
RUN npm run build -- --configuration=production

# ==========================================
# Stage 2: Build Backend (Spring Boot)
# ==========================================
FROM gradle:8.10-jdk21-alpine AS backend-build

WORKDIR /app/backend

# Copy gradle files first for better caching
COPY backend/build.gradle backend/settings.gradle* ./
COPY backend/gradle ./gradle

# Download dependencies (cached layer)
RUN gradle dependencies --no-daemon || true

# Copy source files
COPY backend/src ./src

# Copy frontend build output to static resources
COPY --from=frontend-build /app/frontend/dist/fun-friday-game-ui/browser ./src/main/resources/static

# Build the application
RUN gradle bootJar --no-daemon -x test -x checkstyleMain -x checkstyleTest

# ==========================================
# Stage 3: Runtime Image
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1001 appgroup && \
    adduser -u 1001 -G appgroup -D appuser

# Copy the built JAR
COPY --from=backend-build /app/backend/build/libs/*.jar app.jar

# Change ownership
RUN chown -R appuser:appgroup /app

USER appuser

# Expose port (Render uses PORT env variable)
EXPOSE 8080

# Health check
HEALTHCHECK --interval=300s --timeout=3s --start-period=10s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/api/game-types || exit 1

# Run the application
# Render sets PORT env variable, we use it or default to 8080
ENTRYPOINT ["java", "-jar", "-Dserver.port=${PORT:-8080}", "app.jar"]
