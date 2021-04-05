#!/bin/bash

cp src/main/resources/application.sbt.conf src/main/resources/application.conf

if [ "$#" -eq 0 ]; then
    echo "no parameter given, defaulting to 8080"
    fuser -k 8080/tcp
    sbt "run 8080"

else
   fuser -k $1/tcp
    sbt "run $1"

fi
