@echo off
rem Builds Duet for Windows. Double-click this file, or run it in a terminal from anywhere.
rem Output: dist\Duet-<version>-windows.zip (unzip it and run Duet.exe), and an installer too when WiX Toolset 3 is installed.
setlocal
cd /d "%~dp0\.."

if not exist "gradlew.bat" (
  echo.
  echo This file must stay inside the Duet project folder, next to gradlew.bat.
  echo It is looking in: %CD%
  echo.
  echo If you opened it from inside the zip, close this window, right-click the zip,
  echo choose Extract All, open the folder that was made, and run scripts\build-windows.bat from there.
  echo.
  pause
  exit /b 1
)

where java >nul 2>&1
if errorlevel 1 (
  echo Java 17 is missing. Installing it now...
  winget install -e --id EclipseAdoptium.Temurin.17.JDK --accept-source-agreements --accept-package-agreements
  echo.
  echo Java is installed. Close this window, open a new one and run this file again.
  pause
  exit /b 1
)

for /f "tokens=2 delims==" %%v in ('findstr /b /c:"val desktopVersion" desktopApp\build.gradle.kts') do set VERSION=%%v
set VERSION=%VERSION: =%
set VERSION=%VERSION:"=%

call gradlew.bat -PdesktopOnly :desktopApp:createDistributable
if errorlevel 1 goto fail

if not exist dist mkdir dist
powershell -NoProfile -Command "Compress-Archive -Force -Path 'desktopApp\build\compose\binaries\main\app\Duet' -DestinationPath 'dist\Duet-%VERSION%-windows.zip'"
echo Made dist\Duet-%VERSION%-windows.zip

where candle >nul 2>&1
if not errorlevel 1 (
  call gradlew.bat -PdesktopOnly :desktopApp:packageExe
  if not errorlevel 1 copy /y desktopApp\build\compose\binaries\main\exe\Duet-*.exe dist\ >nul
)
echo.
echo Done. The files are in the dist folder.
pause
exit /b 0

:fail
echo.
echo The build failed. Scroll up for the reason, and send it to Claude.
pause
exit /b 1
