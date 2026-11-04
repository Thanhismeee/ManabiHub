$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition

Write-Host "Starting Full Stack Local Development Environment..." -ForegroundColor Cyan

# 1. Start DB
& "$scriptDir\dev-db-up.ps1"

# 2. Start Backend in new window
Write-Host "Launching Backend..." -ForegroundColor Cyan
try {
    Start-Process powershell -ArgumentList "-NoExit -ExecutionPolicy Bypass -File ""$scriptDir\dev-backend.ps1"""
} catch {
    Write-Host "Could not open new window for backend. Run it manually: powershell -ExecutionPolicy Bypass -File scripts\dev-backend.ps1" -ForegroundColor Yellow
}

# 3. Start Frontend in new window
Write-Host "Launching Frontend..." -ForegroundColor Cyan
try {
    Start-Process powershell -ArgumentList "-NoExit -ExecutionPolicy Bypass -File ""$scriptDir\dev-frontend.ps1"""
} catch {
    Write-Host "Could not open new window for frontend. Run it manually: powershell -ExecutionPolicy Bypass -File scripts\dev-frontend.ps1" -ForegroundColor Yellow
}

Write-Host "Full stack launched! Check the new windows for backend and frontend output." -ForegroundColor Green
