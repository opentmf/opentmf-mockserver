#!/usr/bin/env bash
set -Eeuo pipefail

# Defaults (overridable at runtime)
: "${SERVER_PORT:=1080}"

# Only pass propertyFile if it exists (avoids failing when /config isn't mounted)
PROP_OPT=""
if [ -f /config/mockserver.properties ]; then
  PROP_OPT="-Dmockserver.propertyFile=/config/mockserver.properties"
fi

# Optional: show the exact command for debugging
echo "Launching MockServer on port ${SERVER_PORT}..."
echo "JVM_OPTS: ${JVM_OPTS:-<empty>}"
echo "DEBUG_OPTS: ${DEBUG_OPTS:-<empty>}"
echo "JAVA_TOOL_OPTIONS: ${JAVA_TOOL_OPTIONS:-<empty>}"

exec java ${JVM_OPTS:+$JVM_OPTS} ${DEBUG_OPTS:+$DEBUG_OPTS} \
  -cp /opt/mockserver/mockserver-netty.jar:/libs/* \
  ${PROP_OPT} \
  org.mockserver.cli.Main -serverPort "${SERVER_PORT}"
