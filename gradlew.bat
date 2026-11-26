@echo off
setlocal EnableExtensions EnableDelayedExpansion
set GRADLE_VERSION=8.13
set DIST_DIR=%USERPROFILE%\.gradle\manual-dists\gradle-%GRADLE_VERSION%
set GRADLE_BIN=%DIST_DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat
if not exist "!GRADLE_BIN!" (
  if not exist "!DIST_DIR!" mkdir "!DIST_DIR!"
  set ARCHIVE=!DIST_DIR!\gradle-%GRADLE_VERSION%-bin.zip
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '!ARCHIVE!'"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '!ARCHIVE!' '!DIST_DIR!'"
)
call "!GRADLE_BIN!" %*
endlocal
