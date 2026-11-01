@echo off
cd /d "%~dp0.."

set CONTROLLER_URL=http://localhost:8080
set DEVICE_COUNT=10
set PERIODIC_INFORM_SEC=300
set DOWNLOAD_MIN_SEC=2
set DOWNLOAD_MAX_SEC=5
set REBOOT_MIN_SEC=2
set REBOOT_MAX_SEC=5

py -m uvicorn device_simulator:app --app-dir simulator --port 9000