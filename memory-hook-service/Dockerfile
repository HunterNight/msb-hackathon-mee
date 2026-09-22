# syntax=docker/dockerfile:1.7
# Build with: docker build --platform linux/amd64 -t vcr.vngcloud.vn/<repo>/memory-hook-service:v1.0.0 .
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar -x test
# Boot 4's tools jarmode writes the runnable jar plus a lib/ directory; renaming it keeps the
# entrypoint identical across every service.
RUN java -Djarmode=tools -jar build/libs/*.jar extract --destination /app \
 && mv /app/*.jar /app/app.jar

FROM gcr.io/distroless/java25-debian13:nonroot
WORKDIR /app
COPY --from=build /app/ ./
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseZGC -Dspring.threads.virtual.enabled=true"
EXPOSE 8080
USER nonroot
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
