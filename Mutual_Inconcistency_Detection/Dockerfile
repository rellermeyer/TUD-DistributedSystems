FROM openjdk:11

WORKDIR /MIDD
COPY . /MIDD

RUN chmod u+x ./sbt
RUN chmod u+x ./sbt-dist/bin/sbt

RUN ./sbt compile