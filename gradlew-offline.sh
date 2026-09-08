#!/usr/bin/env bash
# Builds without internet: bundled Gradle from offline/gradle-* and dependencies from offline-repo/.
# Usage: ./gradlew-offline.sh :android:assembleRelease
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
GRADLE_BIN="$(ls -d "$ROOT"/offline/gradle-*/bin/gradle 2>/dev/null | head -1 || true)"
if [ -z "$GRADLE_BIN" ]; then echo "Bundled Gradle not found in offline/. Run ./gradlew downloadDependencies on a machine with internet first."; exit 1; fi
if [ ! -d "$ROOT/offline-repo" ]; then echo "offline-repo/ not found. Run ./gradlew downloadDependencies on a machine with internet first."; exit 1; fi
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT/.gradle-user-home}"
chmod +x "$GRADLE_BIN" 2>/dev/null || true
exec "$GRADLE_BIN" --offline "$@"
