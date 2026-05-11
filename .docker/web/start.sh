#!/bin/sh
echo "===== START OF FRONTEND ====="
cd /home/airline/airline/airline-web
SBT_OPTS="-Xmx2G -Xms512M -XX:MaxMetaspaceSize=512M --sun-misc-unsafe-memory-access=allow" sbt run
echo "===== FRONTEND SHUTDOWN WITH CODE $? ====="
