@echo off
rem Builds without internet: bundled Gradle from offline\gradle-* and dependencies from offline-repo\.
rem Usage: gradlew-offline.bat :android:assembleRelease
rem Uses your normal Gradle home (%USERPROFILE%\.gradle) unless GRADLE_USER_HOME is set.
setlocal
set "ROOT=%~dp0"
set "GRADLE_BIN="
for /d %%D in ("%ROOT%offline\gradle-*") do set "GRADLE_BIN=%%~fD\bin\gradle.bat"
if not defined GRADLE_BIN (
  echo Bundled Gradle not found in offline\. Run gradlew.bat downloadDependencies on a machine with internet first.
  exit /b 1
)
if not exist "%ROOT%offline-repo\" (
  echo offline-repo\ not found. Run gradlew.bat downloadDependencies on a machine with internet first.
  exit /b 1
)
call "%GRADLE_BIN%" --offline %*
