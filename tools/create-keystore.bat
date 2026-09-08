@echo off
rem Creates the release signing key once. Usage: tools\create-keystore.bat [password]
setlocal
cd /d "%~dp0.."
set KS=android\keystore\duel2048-release.jks
if exist "%KS%" (echo keystore already exists: %KS% & exit /b 0)
if "%~1"=="" (set PASS=duel%RANDOM%%RANDOM%%RANDOM%) else (set PASS=%~1)
if not exist android\keystore mkdir android\keystore
set KEYTOOL=keytool
if defined JAVA_HOME set KEYTOOL="%JAVA_HOME%\bin\keytool"
%KEYTOOL% -genkeypair -v -keystore "%KS%" -alias duel2048 -keyalg RSA -keysize 2048 -validity 10000 -storepass "%PASS%" -keypass "%PASS%" -dname "CN=2048 Duel, OU=Dev, O=Duel2048, C=US"
if errorlevel 1 exit /b 1
(
echo KEYSTORE_FILE=android/keystore/duel2048-release.jks
echo KEYSTORE_PASSWORD=%PASS%
echo KEY_ALIAS=duel2048
echo KEY_PASSWORD=%PASS%
) > keystore.properties
echo Created %KS% and keystore.properties. Keep both private and backed up: every future update must be signed with this key.
