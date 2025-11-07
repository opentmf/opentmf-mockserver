# syntax=docker/dockerfile:1.7
ARG MOCKSERVER_VERSION=5.15.0

# --- Fetch the MockServer shaded JAR (standalone) -----------------------------
FROM alpine:3.20 AS fetch
ARG MOCKSERVER_VERSION
RUN apk add --no-cache curl jq
RUN curl -fsSL -o /mockserver-netty-shaded.jar \
  "https://repo1.maven.org/maven2/org/mock-server/mockserver-netty/${MOCKSERVER_VERSION}/mockserver-netty-${MOCKSERVER_VERSION}-shaded.jar"

# --- Final runtime: tiny distro + JRE + curl + tini ---------------------------
FROM eclipse-temurin:11-jre-alpine
RUN apk add --no-cache tini curl

# Non-root user & writable dirs
RUN addgroup -S mockserver && adduser -S -G mockserver mockserver

WORKDIR /opt/mockserver
RUN mkdir -p /opt/mockserver /libs /config \
 && chown -R mockserver:mockserver /opt/mockserver /libs /config

USER mockserver

# MockServer server jar + your callback extensions
COPY --chown=mockserver:mockserver --from=fetch  /mockserver-netty-shaded.jar /opt/mockserver/mockserver-netty.jar
COPY --chown=mockserver:mockserver target/*.jar /libs/opentmf-extensions.jar

# Copy entrypoint and make it executable
COPY --chown=mockserver:mockserver docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

ENV SERVER_PORT=1080 \
    DEBUG_PORT=5005
EXPOSE 1080 \
       5005

# Healthcheck uses the same port via sh -c for env expansion
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=5 \
  CMD ["sh","-c","curl -fsS -X PUT http://127.0.0.1:${SERVER_PORT}/mockserver/status >/dev/null || exit 1"]

# Sensible defaults; users can still override via -e JAVA_TOOL_OPTIONS / JAVA_OPTS
ENV JVM_OPTS="-Dfile.encoding=UTF-8 -Dmockserver.logLevel=WARN"
ENV DEBUG_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${DEBUG_PORT}"

# tini as PID1; script does the rest
ENTRYPOINT ["/sbin/tini","-g","--","/usr/local/bin/docker-entrypoint.sh"]
# no CMD needed; entrypoint decides everything
