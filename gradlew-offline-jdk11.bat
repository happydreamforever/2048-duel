@echo off
rem Offline build with JDK 11: bundled Gradle (offline\gradle-*) + dependencies from m2\.
rem The machine-wide JAVA_HOME stays on Java 21; JDK 11 is used only for this process.
rem
rem Usage:  gradlew-offline-jdk11.bat -Pduel2048.serverOnly :server:installDist
rem         gradlew-offline-jdk11.bat -Pduel2048.serverOnly :shared:test :server:test
setlocal
if not defined DUEL2048_JDK11 (
    if exist "%~dp0jdk\bin\java.exe" (
        set "DUEL2048_JDK11=%~dp0jdk"
    ) else if exist "%~dp0jdk-11.0.32.1+1\bin\java.exe" (
        set "DUEL2048_JDK11=%~dp0jdk-11.0.32.1+1"
    ) else (
        set "DUEL2048_JDK11=C:\jdk11\jdk-11.0.32.1+1"
    )
)
if not exist "%DUEL2048_JDK11%\bin\java.exe" (
    echo ERROR: JDK 11 not found at "%DUEL2048_JDK11%".
    echo Set DUEL2048_JDK11 to a JDK 11 home and retry.
    exit /b 1
)
set "JAVA_HOME=%DUEL2048_JDK11%"
call "%~dp0gradlew-offline.bat" %*
