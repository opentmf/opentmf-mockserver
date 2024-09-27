FROM mockserver/mockserver:5.15.0

ARG VERSION
ENV PROJECT_VERSION ${VERSION}

ADD ./target/dynamic-mock-expectations-${PROJECT_VERSION}.jar /libs/

EXPOSE 1080

ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-Dmockserver.logLevel=WARN", "-cp", "/mockserver-netty-jar-with-dependencies.jar:/libs/*", "-Dmockserver.propertyFile=/config/mockserver.properties", "org.mockserver.cli.Main"]

ENV SERVER_PORT 1080

CMD []
