# first stage

FROM hseeberger/scala-sbt:8u222_1.3.5_2.13.1 AS build

COPY ./ ./

RUN sbt clean assembly
RUN #sbt compile clean package
RUN #sbt update
  
  # second stage

FROM openjdk:8-jre-alpine3.9

COPY --from=build /root/target/scala-2.13/*.jar /Zeus-Protocol.jar
#COPY --from=build /root/.ivy2/cache/org.scala-lang/scala-library/jars/scala-library-2.13.1.jar /scala-library-2.13.1.jar
#COPY --from=build /root/.ivy2/cache/org.scala-lang/scala-library/jars/ /

CMD ["java", "-cp", "Zeus-Protocol.jar", "Zeus"]