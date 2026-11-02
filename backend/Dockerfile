# Budowany z katalogu głównego repozytorium: docker build -f backend/Dockerfile .
FROM node:22-alpine AS frontend
WORKDIR /fe
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npx ng build --output-path=/fe/out

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY backend/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
RUN rm -rf src/main/resources/static
COPY --from=frontend /fe/out/browser ./src/main/resources/static
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY --from=build /src/target/pomiary-*.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
