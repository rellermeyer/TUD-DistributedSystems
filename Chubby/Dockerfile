FROM openjdk:8-jre-alpine

RUN mkdir -p /opt/app
WORKDIR /opt/app

COPY ./run_jar.sh ./
COPY ./target/scala-2.13/app-chubby.jar ./app-chubby.jar
COPY ./config.xml ./
COPY ./serverips.txt ./

RUN ["chmod", "+x", "./run_jar.sh"]

ENTRYPOINT ["./run_jar.sh"]
