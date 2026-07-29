#!/usr/bin/env bash
#
# Startup file for mockserver standalone, that adds all jar files in libs folder to the classpath

MOCK_PATH="mockserver-netty-no-dependencies-5.15.0.jar"
for JAR in libs/*.jar; do
  MOCK_PATH="$MOCK_PATH:${JAR}"
done;

echo "Starting mockserver Using classpath = ${MOCK_PATH}"

java -cp "${MOCK_PATH}" org.mockserver.cli.Main -serverPort 1080
