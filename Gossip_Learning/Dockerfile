FROM hseeberger/scala-sbt:8u222_1.3.5_2.13.1 AS build
WORKDIR /root
COPY ./ ./
RUN sbt clean assembly

FROM adoptopenjdk/openjdk11:x86_64-alpine-jre-11.0.10_9
WORKDIR /root
COPY --from=build /root/target /root/target

EXPOSE 4000
ENTRYPOINT ["java","-jar","/root/target/scala-2.13/GL-assembly-1.0.jar"]
