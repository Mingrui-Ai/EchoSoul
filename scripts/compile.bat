@echo off
setlocal

where mvn >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Maven was not found on PATH.
  echo [ERROR] Install Maven 3.9+ and run this script again.
  exit /b 1
)

echo [INFO] Building EchoSoul with Maven...
mvn -B -DskipTests clean package
if errorlevel 1 (
  echo [ERROR] Build failed.
  exit /b 1
)

echo [INFO] Build succeeded.
endlocal
