FROM gcr.io/distroless/java21
ENV TZ="Europe/Oslo"
WORKDIR /app
COPY build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
LABEL org.opencontainers.image.source=https://github.com/navikt/esyfovarsel
