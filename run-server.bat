@echo off
rem Builds the server once and runs it without Gradle's progress bar.
rem Usage: run-server.bat            (port 8080)
rem        set PORT=8765 && run-server.bat
setlocal
cd /d "%~dp0"
rem Use JSON file storage when MySQL is not configured (no database.json / server.env needed).
if not defined DB set "DB=json"
rem Prefer a project-local portable JDK (jdk\ or jdk-11...) when JAVA_HOME is not set.
if defined JAVA_HOME goto duelJdkDone
if exist "jdk\bin\java.exe" (
    set "JAVA_HOME=%CD%\jdk"
    goto duelJdkDone
)
for /d %%D in ("jdk-11*") do if exist "%%D\bin\java.exe" set "JAVA_HOME=%%~fD"
:duelJdkDone
call gradlew.bat -q -Pduel2048.serverOnly :server:installDist
if errorlevel 1 exit /b 1
call "%~dp0server\build\install\server\bin\server.bat"
