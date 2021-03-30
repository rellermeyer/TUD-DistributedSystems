#!/bin/bash

cp src/main/resources/application.sbt.conf src/main/resources/application.conf
sbt "run $1"
