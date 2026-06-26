FROM node:22-alpine AS frontend-build

WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN VITE_API_BASE_URL=/api npm run build

FROM maven:3.9.9-eclipse-temurin-17 AS backend-build

WORKDIR /workspace
COPY backend/pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline
COPY backend/src ./src
COPY --from=frontend-build /app/dist ./src/main/resources/public
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache curl
WORKDIR /app

COPY --from=backend-build /workspace/target/backend-1.0.0-SNAPSHOT.jar /app/backend.jar

ENV BACKEND_PORT=8119

EXPOSE 8119

HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:8119/ping || exit 1

CMD ["java", "-jar", "/app/backend.jar"]
