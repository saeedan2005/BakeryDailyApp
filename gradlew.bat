@echo off
setlocal
set GRADLE_VERSION=8.9
set GRADLE_SHA256=d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab
if "%GRADLE_USER_HOME%"=="" set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set BASE_DIR=%GRADLE_USER_HOME%\codemagic-wrapper
set DIST_DIR=%BASE_DIR%\gradle-%GRADLE_VERSION%
set GRADLE_BIN=%DIST_DIR%\bin\gradle.bat
set ZIP=%BASE_DIR%\gradle-%GRADLE_VERSION%-bin.zip
if exist "%GRADLE_BIN%" goto run
if not exist "%BASE_DIR%" mkdir "%BASE_DIR%"
if not exist "%ZIP%" powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP%'"
for /f "tokens=*" %%H in ('powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 ''%ZIP%'').Hash.ToLower()"') do set ACTUAL_SHA256=%%H
if /I not "%ACTUAL_SHA256%"=="%GRADLE_SHA256%" (
  echo ERROR: Gradle distribution SHA-256 mismatch.
  del /Q "%ZIP%" >nul 2>&1
  exit /b 1
)
if exist "%DIST_DIR%" rmdir /S /Q "%DIST_DIR%"
if exist "%BASE_DIR%\extract" rmdir /S /Q "%BASE_DIR%\extract"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ZIP%' '%BASE_DIR%\extract'"
move "%BASE_DIR%\extract\gradle-%GRADLE_VERSION%" "%DIST_DIR%" >nul
rmdir /S /Q "%BASE_DIR%\extract"
:run
call "%GRADLE_BIN%" %*
endlocal
