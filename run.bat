@echo off
cd /d "%~dp0"
if not exist ".venv\Scripts\python.exe" (
    echo Create .venv first. See README.md.
    exit /b 1
)
".venv\Scripts\python.exe" "source\main.py" %*
