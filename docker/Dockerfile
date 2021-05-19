# Dockerfile for universalresolver/driver-did-hpass

FROM maven:3.6.3-openjdk-11-slim AS build
MAINTAINER Jens Jelitto <jje@zurich.ibm.com>

# build driver-did-hpass war file
ADD src /opt/driver-did-hpass/src
ADD pom.xml /opt/driver-did-hpass/pom.xml

RUN cd /opt/driver-did-hpass && mvn clean install package -P war

# get jetty
FROM jetty:9.4-jre11-slim
COPY --from=build /opt/driver-did-hpass/target/uni-resolver-driver-did-hpass-1.0.war /var/lib/jetty/webapps/uni-resolver-driver-did-hpass.war
COPY docker/uni-resolver-driver-did-hpass.xml /var/lib/jetty/webapps/uni-resolver-driver-did-hpass.xml

ARG JETTY_HTTP_PORT=8080
EXPOSE $JETTY_HTTP_PORT

ENV JETTY_HTTP_PORT=${JETTY_HTTP_PORT}

# CMD ["java","-jar","/usr/local/jetty/start.jar", "-Djetty.http.port=${JETTY_HTTP_PORT}"]
CMD java -jar /usr/local/jetty/start.jar -Djetty.http.port=$JETTY_HTTP_PORT
