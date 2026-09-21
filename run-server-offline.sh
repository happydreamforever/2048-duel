#!/usr/bin/env bash
# Builds the server without internet (bundled Gradle + offline-repo) and runs it.
# Usage: ./run-server-offline.sh            (port 8080, database from database.json / server.env)
#        PORT=8765 ./run-server-offline.sh
# serverOnly: the server never needs the :android module, so skip it - this also makes the
# script work on old JDK 8 machines without the Android SDK.
set -e
cd "$(dirname "$0")"
# Prefer a project-local portable JDK (jdk/ or jdk-11...) when JAVA_HOME is not set.
if [ -z "$JAVA_HOME" ]; then
    for jdk in ./jdk ./jdk-11*; do
        if [ -x "$jdk/bin/java" ]; then JAVA_HOME="$jdk"; export JAVA_HOME; break; fi
    done
fi
./gradlew-offline.sh -q -Pduel2048.serverOnly :server:installDist
exec server/build/install/server/bin/server
