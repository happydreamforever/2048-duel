@echo off
rem Builds the server without internet (bundled Gradle + offline-repo) and runs it.
rem Usage: run-server-offline.bat            (port 8080, database from database.json / server.env)
rem        set PORT=8765 && run-server-offline.bat
setlocal
cd /d "%~dp0"
call gradlew-offline.bat -q :server:installDist
if errorlevel 1 exit /b 1
call "%~dp0server\build\install\server\bin\server.bat"
