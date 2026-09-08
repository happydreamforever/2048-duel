#!/usr/bin/env bash
# Builds the server once and runs it without Gradle's progress bar.
# Usage: ./run-server.sh            (port 8080)
#        PORT=8765 ./run-server.sh
set -e
cd "$(dirname "$0")"
./gradlew -q :server:installDist
exec server/build/install/server/bin/server
