@echo off
setlocal
cd /d "%~dp0..\ai-service"

if not exist ".venv\Scripts\python.exe" (
  echo [ERROR] .venv not found at ai-service\.venv
  echo Run this first: cd ai-service ^&^& uv sync
  exit /b 1
)

echo Starting ai-service on http://127.0.0.1:8000 ...
echo Success: http://127.0.0.1:8000/health returns "status":"UP"
echo Press Ctrl+C to stop.
echo.

".venv\Scripts\python.exe" -m uvicorn app.main:app --host 127.0.0.1 --port 8000