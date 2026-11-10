# Runtime image for the course service. Upload confirmation downloads the stored video and runs
# ffprobe on it, so this is the only service image that carries a media probe.
#
# A static ffprobe is copied in as a single file instead of installing the Debian ffmpeg package:
# measured locally the runtime image grows by about 149 MB this way against about 566 MB with the
# package, and no extra shared libraries enter the scan surface. Override FFPROBE_IMAGE with a
# registry mirror when Docker Hub is not reachable from the build host.
ARG FFPROBE_IMAGE=mwader/static-ffmpeg:7.1
FROM ${FFPROBE_IMAGE} AS probe

FROM eclipse-temurin:17-jre

ARG JAR_FILE

COPY --from=probe /ffprobe /usr/local/bin/ffprobe

WORKDIR /app

COPY ${JAR_FILE} /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
