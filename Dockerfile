FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:086efc0e24d318f47e804fc58b0b1ad5356bc05d27f2c174955f13c9a1efa6a8
ENV TZ="Europe/Oslo"
WORKDIR /app
COPY build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
LABEL org.opencontainers.image.source=https://github.com/navikt/esyfovarsel
