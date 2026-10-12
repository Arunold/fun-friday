# syntax=docker/dockerfile:1.7
# Fun Friday — one image: the Angular app is bundled into the Spring Boot jar.
#   docker build -t fun-friday .      docker run -p 8080:8080 -v ff-data:/app/data fun-friday
# The server listens on $PORT (default 8080), so platforms such as Render can set it.

# ---------------------------------------------------------------- 1. Frontend
FROM node:24-alpine AS frontend
WORKDIR /src
ENV HUSKY=0 CI=true
COPY package.json package-lock.json ./
COPY frontend/package.json frontend/
COPY e2e/package.json e2e/
# --ignore-scripts: no Git hooks (root "prepare") in the image; native tools ship as prebuilt packages.
RUN --mount=type=cache,target=/root/.npm npm ci --workspace frontend --ignore-scripts --no-audit --no-fund
COPY scripts scripts
COPY frontend frontend
COPY games games
RUN npm run build --workspace frontend

# ---------------------------------------------------------------- 2. Backend
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /src
COPY backend backend
COPY games games
COPY --from=frontend /src/frontend/dist frontend/dist
RUN --mount=type=cache,target=/root/.gradle \
    cd backend && ./gradlew :app:bootJar -x test --no-daemon --console=plain

# ---------------------------------------------------------------- 3. Runtime
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app && mkdir -p /app/data && chown app:app /app/data
WORKDIR /app
COPY --from=backend --chown=app:app /src/backend/app/build/libs/fun-friday.jar app.jar
USER app
# Memory follows the container limit; history (H2) lives in the /app/data volume.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    FF_DATA_DIR=/app/data
EXPOSE 8080
VOLUME ["/app/data"]
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- "http://localhost:${PORT:-8080}/actuator/health" | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
