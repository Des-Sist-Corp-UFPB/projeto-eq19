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

ENV BACKEND_PORT=8119

EXPOSE 8080

CMD ["sh", "/app/start.sh"]
