FROM gradle:9.0.0-jdk21 AS build

WORKDIR /workspace
COPY settings.gradle build.gradle gradle.properties ./
COPY src ./src
RUN gradle --no-daemon bootJar

FROM eclipse-temurin:21-jre

WORKDIR /app
ARG APP_VERSION=dev
ENV SPRING_PROFILES_ACTIVE=prod
ENV APP_VERSION=${APP_VERSION}
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
RUN groupadd --system --gid 10001 triread \
    && useradd --system --uid 10001 --gid triread --no-create-home triread
USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
