#!/bin/sh
echo "===== START OF BACKEND ====="
cd /home/airline/airline/airline-data
SBT_OPTS="-Xmx4G -Xms512M -XX:MaxMetaspaceSize=512M --sun-misc-unsafe-memory-access=allow" sbt "runMain com.patson.MainSimulation"
echo "===== BACKEND SHUTDOWN WITH CODE $? ====="