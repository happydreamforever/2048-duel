#!/usr/bin/env bash
# Builds the server without internet (bundled Gradle + offline-repo) and runs it.
# Usage: ./run-server-offline.sh            (port 8080, database from database.json / server.env)
#        PORT=8765 ./run-server-offline.sh
set -e
cd "$(dirname "$0")"
./gradlew-offline.sh -q :server:installDist
exec server/build/install/server/bin/server
