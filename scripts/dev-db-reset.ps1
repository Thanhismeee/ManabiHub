$ErrorActionPreference = "Stop"

$composeFile = "deploy/docker-compose.local.yml"
if (-not (Test-Path $composeFile)) {
    Write-Host "ERROR: Could not find $composeFile" -ForegroundColor Red
    exit 1
}

Write-Host "WARNING: This will delete local database data." -ForegroundColor Yellow
$confirmation = Read-Host "Type RESET to continue"

if ($confirmation -cne "RESET") {
    Write-Host "Cancelled." -ForegroundColor Cyan
    exit 0
}

Write-Host "Stopping and removing PostgreSQL database container and volume..." -ForegroundColor Cyan
docker compose -f $composeFile down -v
Write-Host "Database and local volume deleted." -ForegroundColor Green
