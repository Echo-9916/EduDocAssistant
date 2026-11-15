@echo off
setlocal
cd /d "%~dp0..\backend"

where mvn >nul 2>nul
if errorlevel 1 (
  echo [ERROR] mvn not found in PATH. Install Maven or use the Maven wrapper if present.
  exit /b 1
)

echo Starting backend on http://localhost:8080 ...
echo Success: log shows "Started EduDocApplication in ... seconds"
echo          and "notification worker started" (stream=edudoc:stream:notification)
echo Press Ctrl+C to stop.
echo.

call mvn spring-boot:run