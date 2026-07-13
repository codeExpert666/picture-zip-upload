ARG MAVEN_IMAGE=maven:3.9.0-eclipse-temurin-17@sha256:8d19f7daf6e637d8ff45314861f964b08cee4279d8230c9fc4e4cc002e1c16e4
ARG RUNTIME_IMAGE=eclipse-temurin:17-jre-jammy@sha256:475d8e96b4b2bfe08999e5e854755c773af1581acdf959a4545d88f0696a2339

FROM ${MAVEN_IMAGE} AS build
ARG MAVEN_OPTS
WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=maven_settings,target=/tmp/maven-settings.xml,required=false \
    if [ -f /tmp/maven-settings.xml ]; then MAVEN_SETTINGS="-s /tmp/maven-settings.xml"; else MAVEN_SETTINGS=""; fi \
    && mvn ${MAVEN_SETTINGS} -B -ntp -DskipTests package \
    && mv target/picture-zip-upload-*.jar target/application.jar

FROM ${RUNTIME_IMAGE} AS runtime

ARG APP_UID=10001
ARG APP_GID=10001

RUN apt-get update \
    && apt-get install --yes --no-install-recommends ca-certificates curl tini \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid "${APP_GID}" app \
    && useradd --uid "${APP_UID}" --gid "${APP_GID}" --create-home --shell /usr/sbin/nologin app \
    && install -d -o app -g app \
        /app \
        /data/picture-upload-work \
        /data/pictures \
        /data/corpusImages

WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/application.jar ./application.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai" \
    SERVER_PORT=8080

USER app:app
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
    CMD curl --fail --silent --show-error "http://127.0.0.1:${SERVER_PORT}/actuator/health" >/dev/null || exit 1

ENTRYPOINT ["/usr/bin/tini", "--", "java", "-jar", "/app/application.jar"]
