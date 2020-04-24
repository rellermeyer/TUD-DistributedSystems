FROM hseeberger/scala-sbt:8u222_1.3.5_2.13.1 as builder
COPY . /hyperdex-akka
WORKDIR /hyperdex-akka
RUN ["rm", "/hyperdex-akka/src/main/resources/application.conf"]
RUN ["mv", "/hyperdex-akka/src/main/resources/dockercompose.conf", "/hyperdex-akka/src/main/resources/application.conf"]
RUN ["sbt" , "assembly"]

FROM openjdk:8u242-jre

COPY --from=builder /hyperdex-akka/target/scala-2.12/hyperdex.jar /hyperdex.jar
