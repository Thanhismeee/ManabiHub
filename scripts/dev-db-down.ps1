$ErrorActionPreference = "Stop"

$composeFile = "deploy/docker-compose.local.yml"
if (-not (Test-Path $composeFile)) {
    Write-Host "ERROR: Could not find $composeFile" -ForegroundColor Red
    exit 1
}

Write-Host "Stopping PostgreSQL database container..." -ForegroundColor Cyan
docker compose -f $composeFile down
Write-Host "Database stopped. Data volume is preserved." -ForegroundColor Green
