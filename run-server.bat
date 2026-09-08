@echo off
rem Builds the server once and runs it without Gradle's progress bar.
rem Usage: run-server.bat            (port 8080)
rem        set PORT=8765 && run-server.bat
setlocal
cd /d "%~dp0"
call gradlew.bat -q :server:installDist
if errorlevel 1 exit /b 1
call "%~dp0server\build\install\server\bin\server.bat"
