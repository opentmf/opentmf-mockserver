FROM mockserver/mockserver:5.15.0
ARG VERSION
ENV PROJECT_VERSION ${VERSION}
ADD ./target/dynamic-mock-expectations-${PROJECT_VERSION}.jar /libs/
ENV SERVER_PORT 1080
ENV DEBUG_PORT 5005
EXPOSE ${SERVER_PORT}
EXPOSE ${DEBUG_PORT}
ENTRYPOINT ["java", \
            "-Dfile.encoding=UTF-8", \
            "-Dmockserver.logLevel=WARN", \
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", \
            "-cp", \
            "/mockserver-netty-jar-with-dependencies.jar:/libs/*", \
            "-Dmockserver.propertyFile=/config/mockserver.properties", \
            "org.mockserver.cli.Main"]
CMD []
