# Runtime image for one business service. The jar is produced on the host by
# scripts/dev-up.sh (./mvnw -DskipTests package), so the image build stays fast and
# reproducible without downloading the whole Maven dependency tree again.
FROM eclipse-temurin:17-jre

ARG JAR_FILE

WORKDIR /app

COPY ${JAR_FILE} /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
