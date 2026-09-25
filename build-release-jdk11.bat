@echo off
rem Builds signed release APK + server dist with JDK 11.
rem
rem Usage:  build-release-jdk11.bat
rem
rem Requires tools\create-keystore.bat once (keystore.properties + android\keystore\...).
rem Uses m2\ when that folder exists (see settings.gradle.kts). Pass -Dduel2048.m2=false
rem to force Maven Central instead. Use gradlew-offline-jdk11.bat for a fully offline build.
rem
rem Outputs:
rem   android\build\outputs\apk\release\android-release.apk
rem   server\build\install\server\bin\server.bat
setlocal
call "%~dp0gradlew-jdk11.bat" --stop >nul 2>&1
rem Clean android/cube2 first: AGP 7.3 can leave stale merged-not-compiled-resources on
rem Windows (mergeReleaseResources / notification_action.xml). Release builds are infrequent.
call "%~dp0gradlew-jdk11.bat" :android:clean :cube2:clean :android:assembleRelease :server:installDist
if errorlevel 1 (
    echo.
    echo BUILD FAILED
    echo If mergeReleaseResources mentions notification_action.xml, delete android\build and cube2\build, then re-run.
    exit /b 1
)
echo.
echo Client APK  : android\build\outputs\apk\release\android-release.apk
echo Server dist : server\build\install\server\bin\server.bat
