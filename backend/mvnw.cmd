@ECHO OFF
SETLOCAL ENABLEDELAYEDEXPANSION

SET BASE_DIR=%~dp0
SET WRAPPER_PROPS=%BASE_DIR%.mvn\wrapper\maven-wrapper.properties

IF NOT EXIST "%WRAPPER_PROPS%" (
  ECHO Missing Maven wrapper properties at %WRAPPER_PROPS%
  EXIT /B 1
)

FOR /F "tokens=1,* delims==" %%A IN (%WRAPPER_PROPS%) DO (
  IF "%%A"=="distributionUrl" SET DISTRIBUTION_URL=%%B
)

FOR %%A IN ("%DISTRIBUTION_URL%") DO SET ARCHIVE_NAME=%%~nxA
SET ARCHIVE_STEM=%ARCHIVE_NAME:-bin.tar.gz=%
SET MAVEN_VERSION=%ARCHIVE_STEM:apache-maven-=%

SET INSTALL_DIR=%BASE_DIR%.mvn\apache-maven-%MAVEN_VERSION%
SET ARCHIVE_PATH=%TEMP%\%ARCHIVE_NAME%
SET MVN_BIN=%INSTALL_DIR%\bin\mvn.cmd

IF NOT EXIST "%MVN_BIN%" (
  IF NOT EXIST "%BASE_DIR%.mvn" MKDIR "%BASE_DIR%.mvn"
  IF NOT EXIST "%ARCHIVE_PATH%" (
    powershell -NoLogo -NoProfile -Command "Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile '%ARCHIVE_PATH%'"
  )
  IF EXIST "%INSTALL_DIR%" RMDIR /S /Q "%INSTALL_DIR%"
  MKDIR "%INSTALL_DIR%"
  tar -xzf "%ARCHIVE_PATH%" -C "%INSTALL_DIR%" --strip-components=1
)

CALL "%MVN_BIN%" %*
