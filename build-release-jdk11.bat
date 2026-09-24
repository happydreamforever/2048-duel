@echo off
rem Builds signed release APK + server dist with JDK 11.
rem
rem Usage:  build-release-jdk11.bat
rem
rem Requires tools\create-keystore.bat once (keystore.properties + android\keystore\...).
rem If a local offline-repo\ exists but is incomplete, this script skips it so Maven Central
rem can satisfy lint and other tooling (use gradlew-offline.bat when fully offline).
rem
rem Outputs:
rem   android\build\outputs\apk\release\android-release.apk
rem   server\build\install\server\bin\server.bat
setlocal
call "%~dp0gradlew-jdk11.bat" --stop >nul 2>&1
rem Clean android/cube2 first: AGP 7.3 can leave stale merged-not-compiled-resources on
rem Windows (mergeReleaseResources / notification_action.xml). Release builds are infrequent.
call "%~dp0gradlew-jdk11.bat" -Dduel2048.offlineRepo=false :android:clean :cube2:clean :android:assembleRelease :server:installDist
if errorlevel 1 (
    echo.
    echo BUILD FAILED
    echo If mergeReleaseResources mentions notification_action.xml, delete android\build and cube2\build, then re-run.
    exit /b 1
)
echo.
echo Client APK  : android\build\outputs\apk\release\android-release.apk
echo Server dist : server\build\install\server\bin\server.bat
