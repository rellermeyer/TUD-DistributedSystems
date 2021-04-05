#!/bin/sh
ssh sven@instance-01 "cd distributed_systems; git pull"
ssh sven@instance-02 "cd distributed_systems; git pull"
ssh sven@instance-03 "cd distributed_systems; git pull"