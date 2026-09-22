@echo off
rem Builds this repository with JDK 11 while the machine-wide JAVA_HOME stays on Java 21.
rem Gradle 7.4.2 / AGP 7.3.1 accept Java 11-18, not 21, so the normal gradlew.bat cannot be used.
rem
rem Usage:  gradlew-jdk11.bat :android:assembleDebug
rem         gradlew-jdk11.bat :server:installDist
rem
rem Point at another JDK 11 with:  set DUEL2048_JDK11=C:\path\to\jdk-11
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
call "%~dp0gradlew.bat" %*
