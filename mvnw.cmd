@echo off
@REM Maven Wrapper for Windows
setlocal

set MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.9
set MAVEN_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip

if not exist "%MAVEN_HOME%" (
    echo Downloading Maven 3.9.9...
    mkdir "%MAVEN_HOME%"
    powershell -Command "Invoke-WebRequest -Uri '%MAVEN_URL%' -OutFile '%TEMP%\maven.zip'"
    powershell -Command "Expand-Archive -Path '%TEMP%\maven.zip' -DestinationPath '%MAVEN_HOME%' -Force"
    del "%TEMP%\maven.zip"
)

for /f "delims=" %%i in ('dir /s /b "%MAVEN_HOME%\mvn.cmd" 2^>nul') do set MAVEN_CMD=%%i
if not defined MAVEN_CMD (
    for /f "delims=" %%i in ('dir /s /b "%MAVEN_HOME%\bin\mvn.cmd" 2^>nul') do set MAVEN_CMD=%%i
)

"%MAVEN_CMD%" %*
