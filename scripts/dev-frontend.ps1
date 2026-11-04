$ErrorActionPreference = "Stop"

Set-Location -Path "frontend"

if (-not (Test-Path "node_modules")) {
    Write-Host "node_modules not found. Running npm install..." -ForegroundColor Cyan
    npm install
}

Write-Host "Starting Vite frontend dev server..." -ForegroundColor Cyan
# Vite usually runs on http://localhost:5173
Write-Host "Frontend will be available at http://localhost:5173 (default)" -ForegroundColor Green
npm run dev
