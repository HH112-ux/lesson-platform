# Runtime image for the course service. Upload confirmation downloads the stored video and runs
# ffprobe on it, and the transcode worker runs ffmpeg on the same file, so this is the only service
# image that carries media tooling.
#
# Both binaries come from one static build instead of the Debian ffmpeg package: measured locally
# the runtime image grows from 702 MB to 857 MB (+155 MB) with the two static binaries, against
# 1.07 GB with the apt package (same base plus the jar), and no extra shared libraries enter the
# scan surface. Override FFPROBE_IMAGE with a registry mirror when Docker Hub is not reachable from
# the build host.
ARG FFPROBE_IMAGE=mwader/static-ffmpeg:7.1
FROM ${FFPROBE_IMAGE} AS probe

FROM eclipse-temurin:17-jre

ARG JAR_FILE

COPY --from=probe /ffprobe /usr/local/bin/ffprobe
COPY --from=probe /ffmpeg /usr/local/bin/ffmpeg

WORKDIR /app

COPY ${JAR_FILE} /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
