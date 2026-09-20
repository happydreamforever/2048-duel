#!/usr/bin/env bash
# Builds the server once and runs it without Gradle's progress bar.
# Usage: ./run-server.sh            (port 8080)
#        PORT=8765 ./run-server.sh
# MySQL: DB_URL=jdbc:mysql://127.0.0.1:3306/duel2048 DB_USER=duel2048 DB_PASSWORD=duel2048 (defaults); DB=json for the file store
set -e
cd "$(dirname "$0")"
./gradlew -q -Pduel2048.serverOnly :server:installDist
exec server/build/install/server/bin/server
