@echo off
rem Builds without internet: bundled Gradle from offline\gradle-* and dependencies from m2\.
rem Usage: gradlew-offline.bat :android:assembleRelease
rem Uses your normal Gradle home (%USERPROFILE%\.gradle) unless GRADLE_USER_HOME is set.
setlocal
set "ROOT=%~dp0"
rem Prefer a project-local portable JDK (jdk\ or jdk-11...) when JAVA_HOME is not set.
if defined JAVA_HOME goto duelJdkDone
if exist "%ROOT%jdk\bin\java.exe" (
    set "JAVA_HOME=%ROOT%jdk"
    goto duelJdkDone
)
for /d %%D in ("%ROOT%jdk-11*") do if exist "%%D\bin\java.exe" set "JAVA_HOME=%%D"
:duelJdkDone
set "GRADLE_BIN="
for /d %%D in ("%ROOT%offline\gradle-*") do set "GRADLE_BIN=%%~fD\bin\gradle.bat"
if not defined GRADLE_BIN (
  echo Bundled Gradle not found in offline\. Run gradlew.bat downloadDependencies on a machine with internet first.
  exit /b 1
)
if not exist "%ROOT%m2\" (
  echo m2\ not found. Run gradlew.bat downloadDependencies on a machine with internet first.
  exit /b 1
)
call "%GRADLE_BIN%" --offline %*
