FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:55d6597badfa521bc2cf6ea3383632118d109c37e3c827d4dbf365923b6d9b37
ENV TZ="Europe/Oslo"
WORKDIR /app
COPY build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
LABEL org.opencontainers.image.source=https://github.com/navikt/esyfovarsel
