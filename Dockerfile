FROM ghcr.io/navikt/baseimages/temurin:19
LABEL org.opencontainers.image.source=https://github.com/navikt/esyfovarsel
COPY build/libs/*.jar app.jar
