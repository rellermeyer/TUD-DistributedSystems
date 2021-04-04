#!/bin/sh
ssh sven@instance-01 "lsof -tc java | xargs --no-run-if-empty kill -9"
ssh sven@instance-02 "lsof -tc java | xargs --no-run-if-empty kill -9"
ssh sven@instance-03 "lsof -tc java | xargs --no-run-if-empty kill -9"