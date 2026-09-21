#!/usr/bin/env bash
# Builds without internet: bundled Gradle from offline/gradle-* and dependencies from offline-repo/.
# Usage: ./gradlew-offline.sh :android:assembleRelease
# Uses your normal Gradle home (~/.gradle) unless GRADLE_USER_HOME is set.
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
# Prefer a project-local portable JDK (jdk/ or jdk-11...) when JAVA_HOME is not set.
if [ -z "$JAVA_HOME" ]; then
    for jdk in "$ROOT/jdk" "$ROOT"/jdk-11*; do
        if [ -x "$jdk/bin/java" ]; then JAVA_HOME="$jdk"; export JAVA_HOME; break; fi
    done
fi
GRADLE_BIN="$(ls -d "$ROOT"/offline/gradle-*/bin/gradle 2>/dev/null | head -1 || true)"
if [ -z "$GRADLE_BIN" ]; then echo "Bundled Gradle not found in offline/. Run ./gradlew downloadDependencies on a machine with internet first."; exit 1; fi
if [ ! -d "$ROOT/offline-repo" ]; then echo "offline-repo/ not found. Run ./gradlew downloadDependencies on a machine with internet first."; exit 1; fi
chmod +x "$GRADLE_BIN" 2>/dev/null || true
exec "$GRADLE_BIN" --offline "$@"
