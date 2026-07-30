FROM alpine:3.20 AS agent-download

ARG OTEL_AGENT_VERSION=2.5.0

RUN apk add --no-cache curl \
    && curl -fL \
       "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar" \
       -o /opentelemetry-javaagent.jar

FROM node:22-alpine AS frontend-build

WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-17 AS backend-build

WORKDIR /workspace
COPY backend/pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline
COPY backend/src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache nginx curl
WORKDIR /app

COPY --from=backend-build /workspace/target/backend-1.0.0-SNAPSHOT.jar /app/backend.jar
COPY --from=frontend-build /app/dist /usr/share/nginx/html
COPY docker/nginx.conf /etc/nginx/http.d/default.conf
COPY docker/start.sh /app/start.sh
COPY --from=agent-download /opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

ENV BACKEND_PORT=8119

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
  CMD curl --fail --silent --show-error http://localhost:8080/api/ping >/dev/null || exit 1

CMD ["sh", "/app/start.sh"]

