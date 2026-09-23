@echo off
rem Builds the 2048 Duel client (Android) and server (Kotlin/JVM) with JDK 11.
rem The machine-wide JAVA_HOME stays on Java 21; JDK 11 is used only inside this repo.
rem
rem Usage:  build-all-jdk11.bat
rem
rem Outputs:
rem   android\build\outputs\apk\debug\android-debug.apk
rem   server\build\install\server\bin\server.bat   (run-server.bat starts it)
setlocal
rem Stop leftover Kotlin/Gradle daemons (avoids Windows file-lock on server\build\kotlin\...).
call "%~dp0gradlew-jdk11.bat" --stop >nul 2>&1
call "%~dp0gradlew-jdk11.bat" :android:assembleDebug :server:installDist
if errorlevel 1 (
    echo.
    echo BUILD FAILED
    exit /b 1
)
echo.
echo Client APK  : android\build\outputs\apk\debug\android-debug.apk
echo Server dist : server\build\install\server\bin\server.bat
