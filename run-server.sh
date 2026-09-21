#!/usr/bin/env bash
# Builds the server once and runs it without Gradle's progress bar.
# Usage: ./run-server.sh            (port 8080)
#        PORT=8765 ./run-server.sh
# MySQL: DB_URL=jdbc:mysql://127.0.0.1:3306/duel2048 DB_USER=duel2048 DB_PASSWORD=duel2048 (defaults); DB=json for the file store
set -e
cd "$(dirname "$0")"
# Prefer a project-local portable JDK (jdk/ or jdk-11...) when JAVA_HOME is not set.
if [ -z "$JAVA_HOME" ]; then
    for jdk in ./jdk ./jdk-11*; do
        if [ -x "$jdk/bin/java" ]; then JAVA_HOME="$jdk"; export JAVA_HOME; break; fi
    done
fi
./gradlew -q -Pduel2048.serverOnly :server:installDist
exec server/build/install/server/bin/server
